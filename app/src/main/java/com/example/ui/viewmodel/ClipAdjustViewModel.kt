package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiProviderRouter
import com.example.data.ai.AiRoutingOutcome
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionAnimation
import com.example.domain.model.CaptionFontStyle
import com.example.domain.model.CaptionPosition
import com.example.domain.model.CaptionStylePreset
import com.example.domain.model.ClipProject
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportedVideo
import com.example.domain.model.GeneratedClip
import com.example.domain.model.GeneratedThumbnail
import com.example.media.ClipTransformerEngine
import com.example.media.DeviceCapabilityChecker
import com.example.media.ThumbnailGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ClipAdjustViewModel(
    private val appContext: Context,
    private val clipId: String,
    private val repository: ProjectRepository,
    private val aiRouter: AiProviderRouter
) : ViewModel() {

    val clip: StateFlow<GeneratedClip?> = repository.observeClip(clipId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _project = MutableStateFlow<ClipProject?>(null)
    val project: StateFlow<ClipProject?> = _project.asStateFlow()

    val thumbnails: StateFlow<List<GeneratedThumbnail>> = repository.observeThumbnailsForClip(clipId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _candidateFrames = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val candidateFrames: StateFlow<List<Pair<Long, String>>> = _candidateFrames.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _exportProgress = MutableStateFlow<Int?>(null)
    val exportProgress: StateFlow<Int?> = _exportProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _availableResolutions = MutableStateFlow(listOf(ExportResolution.HD_720P))
    val availableResolutions: StateFlow<List<ExportResolution>> = _availableResolutions.asStateFlow()

    init {
        viewModelScope.launch {
            val loadedClip = repository.getClip(clipId) ?: return@launch
            val loadedProject = repository.getProject(loadedClip.projectId) ?: return@launch
            _project.value = loadedProject

            _availableResolutions.value = ExportResolution.entries.filter { res ->
                DeviceCapabilityChecker.isResolutionSupported(
                    res,
                    loadedProject.sourceWidth,
                    loadedProject.sourceHeight
                )
            }

            _candidateFrames.value = ThumbnailGenerator.extractCandidateFrames(
                appContext,
                Uri.parse(loadedProject.sourceUri),
                loadedClip.effectiveStartMs,
                loadedClip.effectiveEndMs,
                count = 4
            )
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun updateCaptionText(segmentId: String, newText: String) {
        val current = clip.value ?: return
        val updatedSegments = current.captions.map { seg ->
            if (seg.id == segmentId) seg.copy(text = newText) else seg
        }
        saveClip(current.copy(captions = updatedSegments))
    }

    fun updateCaptionStyle(
        style: CaptionStylePreset = clip.value?.adjustment?.captionStyle ?: CaptionStylePreset.BOLD_CREATOR,
        position: CaptionPosition = clip.value?.adjustment?.captionPosition ?: CaptionPosition.BOTTOM,
        animation: CaptionAnimation = clip.value?.adjustment?.captionAnimation ?: CaptionAnimation.POP,
        fontStyle: CaptionFontStyle = clip.value?.adjustment?.captionFontStyle ?: CaptionFontStyle.DISPLAY_BOLD,
        sizeSp: Float = clip.value?.adjustment?.captionSizeSp ?: 22f,
        colorHex: Long = clip.value?.adjustment?.captionColorHex ?: 0xFFFFFFFF
    ) {
        val current = clip.value ?: return
        saveClip(
            current.copy(
                adjustment = current.adjustment.copy(
                    captionStyle = style,
                    captionPosition = position,
                    captionAnimation = animation,
                    captionFontStyle = fontStyle,
                    captionSizeSp = sizeSp.coerceIn(14f, 34f),
                    captionColorHex = colorHex
                )
            )
        )
    }

    fun updateFraming(
        aspectRatio: AspectRatioPreset = clip.value?.aspectRatio ?: AspectRatioPreset.VERTICAL_9_16,
        cropOffsetX: Float = clip.value?.adjustment?.cropOffsetX ?: 0f,
        cropOffsetY: Float = clip.value?.adjustment?.cropOffsetY ?: 0f,
        zoomScale: Float = clip.value?.adjustment?.zoomScale ?: 1f
    ) {
        val current = clip.value ?: return
        saveClip(
            current.copy(
                aspectRatio = aspectRatio,
                adjustment = current.adjustment.copy(
                    cropOffsetX = cropOffsetX.coerceIn(-1f, 1f),
                    cropOffsetY = cropOffsetY.coerceIn(-1f, 1f),
                    zoomScale = zoomScale.coerceIn(1.0f, 2.2f)
                )
            )
        )
    }

    fun nudgeStartEnd(startDeltaMs: Long, endDeltaMs: Long) {
        val current = clip.value ?: return
        val sourceMaxMs = _project.value?.sourceDurationMs ?: current.endMs
        val newStartOffset = current.adjustment.startTrimOffsetMs + startDeltaMs
        val newEndOffset = current.adjustment.endTrimOffsetMs + endDeltaMs
        val proposedStart = (current.startMs + newStartOffset).coerceIn(0L, sourceMaxMs - 3000L)
        val proposedEnd = (current.endMs + newEndOffset).coerceIn(proposedStart + 3000L, sourceMaxMs)
        saveClip(
            current.copy(
                adjustment = current.adjustment.copy(
                    startTrimOffsetMs = proposedStart - current.startMs,
                    endTrimOffsetMs = proposedEnd - current.endMs
                )
            )
        )
    }

    fun updateMetadata(
        title: String,
        description: String,
        hook: String,
        socialCaption: String,
        hashtagsCsv: String
    ) {
        val current = clip.value ?: return
        val tags = hashtagsCsv.split(',', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("#")) it else "#$it" }
            .take(15)
        saveClip(
            current.copy(
                title = title.trim().ifBlank { current.title },
                description = description.trim(),
                openingHook = hook.trim(),
                socialCaption = socialCaption.trim(),
                hashtags = tags
            )
        )
        _statusMessage.value = "Clip details saved."
    }

    fun regenerateMetadataWithAi() {
        val current = clip.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val transcript = current.captions.joinToString(" ") { it.text }.ifBlank { current.description }
            when (val res = aiRouter.regenerateClipMetadata(current.title, transcript, current.openingHook, current.language)) {
                is AiRoutingOutcome.Success -> {
                    val s = res.data
                    saveClip(
                        current.copy(
                            title = s.title,
                            description = s.description,
                            openingHook = s.openingHook,
                            socialCaption = s.socialCaption,
                            hashtags = s.hashtags
                        )
                    )
                    _statusMessage.value = "Refreshed titles & hashtags via ${res.providerName}."
                }
                is AiRoutingOutcome.QuotaExhausted -> _statusMessage.value = res.message
                is AiRoutingOutcome.Offline -> _statusMessage.value = res.message
                is AiRoutingOutcome.Error -> _statusMessage.value = res.message
            }
            _isBusy.value = false
        }
    }

    fun generateCustomThumbnail(frameTimestampMs: Long, overlayTitle: String, textColorHex: Long) {
        val current = clip.value ?: return
        val proj = _project.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val thumbDir = File(appContext.filesDir, "thumbnails/${proj.id}").apply { mkdirs() }
            val outFile = File(thumbDir, "${current.id}_${System.currentTimeMillis()}.jpg")
            val res = ThumbnailGenerator.generateStyledThumbnail(
                context = appContext,
                videoUri = Uri.parse(proj.sourceUri),
                frameTimestampMs = frameTimestampMs,
                overlayTitle = overlayTitle,
                textColorHex = textColorHex,
                fontStyle = current.adjustment.captionFontStyle,
                aspectRatio = current.aspectRatio,
                outputFile = outFile
            )
            res.onSuccess { file ->
                repository.addThumbnail(
                    GeneratedThumbnail(
                        id = "thumb_${UUID.randomUUID().toString().take(8)}",
                        clipId = current.id,
                        projectId = proj.id,
                        filePath = file.absolutePath,
                        overlayTitle = overlayTitle,
                        frameTimestampMs = frameTimestampMs,
                        textColorHex = textColorHex,
                        aspectRatio = current.aspectRatio,
                        createdAtMs = System.currentTimeMillis()
                    )
                )
                saveClip(current.copy(thumbnailPath = file.absolutePath))
                _statusMessage.value = "Thumbnail updated."
            }.onFailure { err ->
                _statusMessage.value = "Thumbnail error: ${err.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun downloadThumbnailToGallery(filePath: String, title: String) {
        viewModelScope.launch {
            val res = ThumbnailGenerator.saveThumbnailToGallery(appContext, File(filePath), title)
            _statusMessage.value = if (res.isSuccess) {
                "Thumbnail saved to Pictures/ClipFreeAI."
            } else {
                "Save failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun regenerateClipPreview() {
        val current = clip.value ?: return
        val proj = _project.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            _exportProgress.value = 0
            val outDir = File(appContext.filesDir, "clips/${proj.id}").apply { mkdirs() }
            val outFile = File(outDir, "${current.id}_v${System.currentTimeMillis()}.mp4")
            val res = ClipTransformerEngine.renderClipMp4(
                context = appContext,
                sourceUri = Uri.parse(proj.sourceUri),
                sourceWidth = proj.sourceWidth,
                sourceHeight = proj.sourceHeight,
                clip = current,
                resolution = ExportResolution.HD_720P,
                outputFile = outFile,
                burnCaptions = true,
                onProgress = { pct -> _exportProgress.value = pct }
            )
            res.onSuccess { output ->
                current.clipFilePath?.let { old -> runCatching { File(old).delete() } }
                saveClip(current.copy(clipFilePath = output.file.absolutePath, isGenerated = true))
                _statusMessage.value = "Clip preview regenerated (${output.fileSizeBytes / 1024} KB)."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Clip regeneration failed."
            }
            _exportProgress.value = null
            _isBusy.value = false
        }
    }

    fun exportMp4(resolution: ExportResolution, saveToMoviesGallery: Boolean) {
        val current = clip.value ?: return
        val proj = _project.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            _exportProgress.value = 0
            val exportsDir = File(appContext.filesDir, "exports").apply { mkdirs() }
            val outFile = File(exportsDir, "clipfree_${current.id}_${resolution.shortSidePx}p_${System.currentTimeMillis()}.mp4")
            val res = ClipTransformerEngine.renderClipMp4(
                context = appContext,
                sourceUri = Uri.parse(proj.sourceUri),
                sourceWidth = proj.sourceWidth,
                sourceHeight = proj.sourceHeight,
                clip = current,
                resolution = resolution,
                outputFile = outFile,
                burnCaptions = true,
                onProgress = { pct -> _exportProgress.value = pct }
            )
            res.onSuccess { output ->
                var publicUri: String? = null
                if (saveToMoviesGallery) {
                    val saved = ClipTransformerEngine.saveMp4ToDeviceMovies(appContext, output.file, current.title)
                    publicUri = saved.getOrNull()?.toString()
                }
                repository.addExport(
                    ExportedVideo(
                        id = "exp_${UUID.randomUUID().toString().take(8)}",
                        clipId = current.id,
                        projectId = proj.id,
                        title = current.title,
                        filePath = output.file.absolutePath,
                        publicUri = publicUri,
                        resolution = resolution,
                        aspectRatio = current.aspectRatio,
                        durationMs = output.durationMs,
                        fileSizeBytes = output.fileSizeBytes,
                        thumbnailPath = current.thumbnailPath,
                        createdAtMs = System.currentTimeMillis()
                    )
                )
                saveClip(current.copy(clipFilePath = output.file.absolutePath, isGenerated = true))
                _statusMessage.value = if (publicUri != null) {
                    "Exported ${resolution.label} MP4 & saved to Movies/ClipFreeAI."
                } else {
                    "Exported ${resolution.label} MP4 to Library."
                }
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "MP4 export failed."
            }
            _exportProgress.value = null
            _isBusy.value = false
        }
    }

    private fun saveClip(updated: GeneratedClip) {
        viewModelScope.launch {
            repository.upsertClip(updated.copy(updatedAtMs = System.currentTimeMillis()))
        }
    }
}
