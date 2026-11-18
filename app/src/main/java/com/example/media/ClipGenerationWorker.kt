package com.example.media

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.ai.AiProviderRouter
import com.example.data.ai.AiRoutingOutcome
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.CaptionSegment
import com.example.domain.model.CaptionStylePreset
import com.example.domain.model.ClipAdjustment
import com.example.domain.model.ExportResolution
import com.example.domain.model.GeneratedClip
import com.example.domain.model.GeneratedThumbnail
import com.example.domain.model.HighlightCategory
import com.example.domain.model.ProcessingStatus
import com.example.domain.model.TranscriptWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

class ClipGenerationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_CAPTION_STYLE = "caption_style"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS_MESSAGE = "status_message"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_QUOTA_EXHAUSTED = "quota_exhausted"
    }

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID) ?: return Result.failure()
        val captionStyleName = inputData.getString(KEY_CAPTION_STYLE) ?: CaptionStylePreset.BOLD_CREATOR.name
        val captionStyle = CaptionStylePreset.entries.firstOrNull { it.name == captionStyleName }
            ?: CaptionStylePreset.BOLD_CREATOR

        val db = AppDatabase.getInstance(applicationContext)
        val repository = ProjectRepository(db)
        val prefsManager = PreferencesManager(applicationContext)
        val prefs = prefsManager.preferencesFlow.first()
        val router = AiProviderRouter(applicationContext, repository, prefsManager)

        val project = repository.getProject(projectId) ?: return Result.failure(
            workDataOf(KEY_ERROR_MESSAGE to "Project not found.")
        )

        return try {
            updateProgress(repository, projectId, 8, "Inspecting source video & audio...")
            val uri = Uri.parse(project.sourceUri)
            val inspected = VideoInspector.inspectVideo(applicationContext, uri).getOrElse { err ->
                repository.updateProjectProgress(
                    projectId, ProcessingStatus.FAILED, err.message ?: "Missing or corrupt video", 0, 0
                )
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to (err.message ?: "Invalid video")))
            }

            if (!DeviceCapabilityChecker.hasSufficientStorage(applicationContext, 80L * 1024L * 1024L)) {
                val msg = "Low storage space. Free at least 80 MB and retry."
                repository.updateProjectProgress(projectId, ProcessingStatus.FAILED, msg, 0, 0)
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
            }

            updateProgress(repository, projectId, 20, "Sampling frames & speech track for AI...")
            val payload = VideoInspector.extractAnalysisPayload(applicationContext, inspected, frameCount = 8)
            val minSec = if (project.durationPreset.name == "CUSTOM") project.customMinSeconds else project.durationPreset.minSeconds
            val maxSec = if (project.durationPreset.name == "CUSTOM") project.customMaxSeconds else project.durationPreset.maxSeconds

            updateProgress(repository, projectId, 35, "AI detecting viral hooks & highlights...")
            val outcome = router.analyzeVideoHighlights(
                payload = payload,
                minClipSec = minSec,
                maxClipSec = maxSec,
                targetAspectRatio = project.aspectRatio,
                useCache = prefs.smartAiCachingEnabled,
                preferredLanguage = prefs.language
            )

            // Recycle bitmaps right after AI inference to keep mid-range phone memory lean
            payload.frames.forEach { it.bitmap.recycle() }

            val analysisResult = when (outcome) {
                is AiRoutingOutcome.Success -> outcome.data
                is AiRoutingOutcome.QuotaExhausted -> {
                    repository.updateProjectProgress(projectId, ProcessingStatus.PAUSED, outcome.message, 35, 0)
                    return Result.failure(
                        workDataOf(
                            KEY_ERROR_MESSAGE to outcome.message,
                            KEY_QUOTA_EXHAUSTED to true
                        )
                    )
                }
                is AiRoutingOutcome.Offline -> {
                    repository.updateProjectProgress(projectId, ProcessingStatus.PAUSED, outcome.message, 35, 0)
                    return Result.failure(workDataOf(KEY_ERROR_MESSAGE to outcome.message))
                }
                is AiRoutingOutcome.Error -> {
                    repository.updateProjectProgress(projectId, ProcessingStatus.FAILED, outcome.message, 35, 0)
                    return Result.failure(workDataOf(KEY_ERROR_MESSAGE to outcome.message))
                }
            }

            val totalClips = analysisResult.clips.size
            val clipsDir = File(applicationContext.filesDir, "clips/$projectId").apply { mkdirs() }
            val thumbsDir = File(applicationContext.filesDir, "thumbnails/$projectId").apply { mkdirs() }
            val generatedList = mutableListOf<GeneratedClip>()

            analysisResult.clips.forEachIndexed { index, dto ->
                if (isStopped) throw CancellationException("Work cancelled")

                val basePct = 45 + ((index.toFloat() / totalClips.coerceAtLeast(1)) * 50).toInt()
                updateProgress(
                    repository,
                    projectId,
                    basePct,
                    "Reframing & rendering clip ${index + 1} of $totalClips..."
                )

                val faceFocus = FaceReframingAnalyzer.analyzeSubjectFocus(
                    applicationContext,
                    uri,
                    dto.startMs,
                    dto.endMs
                )

                val clipId = "clip_${UUID.randomUUID().toString().take(8)}"
                val adjustment = ClipAdjustment(
                    captionStyle = captionStyle,
                    subjectFocusX = faceFocus.focusX,
                    subjectFocusY = faceFocus.focusY,
                    faceTrackingStatus = faceFocus.statusDescription
                )

                val captions = dto.captions.mapIndexed { cIdx, c ->
                    CaptionSegment(
                        id = "${clipId}_seg_$cIdx",
                        text = c.text,
                        startMs = c.startMs,
                        endMs = c.endMs,
                        words = c.words.map { w ->
                            TranscriptWord(w.word, w.startMs, w.endMs, w.isKeyword)
                        }
                    )
                }

                val draftClip = GeneratedClip(
                    id = clipId,
                    projectId = projectId,
                    title = dto.title,
                    description = dto.description,
                    openingHook = dto.openingHook,
                    selectionReason = dto.selectionReason,
                    category = HighlightCategory.entries.firstOrNull { it.name == dto.category }
                        ?: HighlightCategory.INTERESTING,
                    viralityScore = dto.viralityScore,
                    viralityExplanation = dto.viralityExplanation,
                    socialCaption = dto.socialCaption,
                    hashtags = dto.hashtags,
                    language = dto.language.ifBlank { analysisResult.detectedLanguage },
                    startMs = dto.startMs,
                    endMs = dto.endMs,
                    aspectRatio = project.aspectRatio,
                    captions = captions,
                    adjustment = adjustment
                )

                val outFile = File(clipsDir, "$clipId.mp4")
                val renderRes = ClipTransformerEngine.renderClipMp4(
                    context = applicationContext,
                    sourceUri = uri,
                    sourceWidth = inspected.effectiveWidth,
                    sourceHeight = inspected.effectiveHeight,
                    clip = draftClip,
                    resolution = ExportResolution.HD_720P,
                    outputFile = outFile,
                    burnCaptions = true
                )

                val thumbFile = File(thumbsDir, "$clipId.jpg")
                val thumbRes = ThumbnailGenerator.generateStyledThumbnail(
                    context = applicationContext,
                    videoUri = uri,
                    frameTimestampMs = (dto.startMs + dto.endMs) / 2,
                    overlayTitle = dto.title,
                    textColorHex = 0xFFFFFFFF,
                    fontStyle = adjustment.captionFontStyle,
                    aspectRatio = project.aspectRatio,
                    outputFile = thumbFile
                )
                val thumbPath = thumbRes.getOrNull()?.absolutePath
                if (thumbPath != null) {
                    repository.addThumbnail(
                        GeneratedThumbnail(
                            id = "thumb_${UUID.randomUUID().toString().take(8)}",
                            clipId = clipId,
                            projectId = projectId,
                            filePath = thumbPath,
                            overlayTitle = dto.title,
                            frameTimestampMs = (dto.startMs + dto.endMs) / 2,
                            textColorHex = 0xFFFFFFFF,
                            aspectRatio = project.aspectRatio,
                            createdAtMs = System.currentTimeMillis()
                        )
                    )
                }

                val readyClip = draftClip.copy(
                    clipFilePath = if (renderRes.isSuccess) outFile.absolutePath else null,
                    thumbnailPath = thumbPath,
                    isGenerated = renderRes.isSuccess
                )
                repository.upsertClip(readyClip)
                generatedList += readyClip
            }

            if (generatedList.isEmpty()) {
                val msg = "AI did not return any clips for this video."
                repository.updateProjectProgress(projectId, ProcessingStatus.FAILED, msg, 0, 0)
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
            }

            repository.upsertProject(
                project.copy(
                    status = ProcessingStatus.COMPLETED,
                    statusMessage = "${generatedList.size} viral-ready shorts generated",
                    progressPercent = 100,
                    clipCount = generatedList.size,
                    detectedLanguage = analysisResult.detectedLanguage,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            router.setAvailabilityState(AiAvailabilityState.AI_AVAILABLE)
            Result.success(workDataOf(KEY_PROGRESS to 100))
        } catch (e: CancellationException) {
            repository.updateProjectProgress(
                projectId,
                ProcessingStatus.PAUSED,
                "Processing cancelled. Ready to retry.",
                0,
                repository.getClipsForProject(projectId).size
            )
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unexpected processing error."
            repository.updateProjectProgress(
                projectId,
                ProcessingStatus.FAILED,
                msg,
                0,
                repository.getClipsForProject(projectId).size
            )
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
        }
    }

    private suspend fun updateProgress(
        repository: ProjectRepository,
        projectId: String,
        percent: Int,
        message: String
    ) {
        setProgress(workDataOf(KEY_PROGRESS to percent, KEY_STATUS_MESSAGE to message))
        val existingCount = repository.getClipsForProject(projectId).size
        repository.updateProjectProgress(
            projectId = projectId,
            status = ProcessingStatus.PROCESSING,
            message = message,
            progressPercent = percent,
            clipCount = existingCount
        )
    }
}
