package com.example.data.repository

import android.content.Context
import com.example.data.local.AiProviderDao
import com.example.data.local.AiProviderEntity
import com.example.data.local.AppDatabase
import com.example.data.local.ClipDao
import com.example.data.local.ClipEntity
import com.example.data.local.ExportedMediaDao
import com.example.data.local.ExportedMediaEntity
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.ThumbnailDao
import com.example.data.local.ThumbnailEntity
import com.example.data.local.TranscriptCacheDao
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionSegment
import com.example.domain.model.ClipAdjustment
import com.example.domain.model.ClipDurationPreset
import com.example.domain.model.ClipProject
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportedVideo
import com.example.domain.model.GeneratedClip
import com.example.domain.model.GeneratedThumbnail
import com.example.domain.model.HighlightCategory
import com.example.domain.model.ProcessingStatus
import com.example.domain.model.ProviderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectRepository(private val db: AppDatabase) {
    constructor(context: Context) : this(AppDatabase.getInstance(context))

    private val projectDao: ProjectDao = db.projectDao()
    private val clipDao: ClipDao = db.clipDao()
    private val exportDao: ExportedMediaDao = db.exportedMediaDao()
    private val thumbnailDao: ThumbnailDao = db.thumbnailDao()
    private val providerDao: AiProviderDao = db.aiProviderDao()
    val transcriptCacheDao: TranscriptCacheDao = db.transcriptCacheDao()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val projects: Flow<List<ClipProject>> = projectDao.observeAllProjects().map { list ->
        list.map { it.toDomain() }
    }

    val allClips: Flow<List<GeneratedClip>> = clipDao.observeAllClips().map { list ->
        list.map { it.toDomain() }
    }

    val allExports: Flow<List<ExportedVideo>> = exportDao.observeAllExports().map { list ->
        list.map { it.toDomain() }
    }

    val allThumbnails: Flow<List<GeneratedThumbnail>> = thumbnailDao.observeAllThumbnails().map { list ->
        list.map { it.toDomain() }
    }

    val providers: Flow<List<AiProviderConfig>> = providerDao.observeProviders().map { list ->
        list.map { it.toDomain() }
    }

    fun observeProject(projectId: String): Flow<ClipProject?> =
        projectDao.observeProject(projectId).map { it?.toDomain() }

    fun observeClipsForProject(projectId: String): Flow<List<GeneratedClip>> =
        clipDao.observeClipsForProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeClip(clipId: String): Flow<GeneratedClip?> =
        clipDao.observeClip(clipId).map { it?.toDomain() }

    fun observeThumbnailsForClip(clipId: String): Flow<List<GeneratedThumbnail>> =
        thumbnailDao.observeThumbnailsForClip(clipId).map { list -> list.map { it.toDomain() } }

    suspend fun getProject(projectId: String): ClipProject? =
        projectDao.getProject(projectId)?.toDomain()

    suspend fun getClipsForProject(projectId: String): List<GeneratedClip> =
        clipDao.getClipsForProject(projectId).map { it.toDomain() }

    suspend fun getClip(clipId: String): GeneratedClip? =
        clipDao.getClip(clipId)?.toDomain()

    suspend fun upsertProject(project: ClipProject) {
        projectDao.upsertProject(project.toEntity())
    }

    suspend fun renameProject(projectId: String, newName: String) {
        val clean = newName.trim().take(120).ifBlank { return }
        projectDao.renameProject(projectId, clean, System.currentTimeMillis())
    }

    suspend fun updateProjectProgress(
        projectId: String,
        status: ProcessingStatus,
        message: String,
        progressPercent: Int,
        clipCount: Int
    ) {
        projectDao.updateProjectProgress(
            projectId = projectId,
            status = status.name,
            message = message,
            progress = progressPercent.coerceIn(0, 100),
            clipCount = clipCount,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun deleteProject(projectId: String) {
        val clips = clipDao.getClipsForProject(projectId)
        clips.forEach { clip ->
            clip.clipFilePath?.let { path -> runCatching { File(path).delete() } }
            clip.thumbnailPath?.let { path -> runCatching { File(path).delete() } }
        }
        projectDao.deleteProjectById(projectId)
    }

    suspend fun upsertClip(clip: GeneratedClip) {
        clipDao.upsertClip(clip.toEntity())
    }

    suspend fun upsertClips(clips: List<GeneratedClip>) {
        clipDao.upsertClips(clips.map { it.toEntity() })
    }

    suspend fun deleteClip(clipId: String) {
        clipDao.getClip(clipId)?.let { clip ->
            clip.clipFilePath?.let { runCatching { File(it).delete() } }
        }
        clipDao.deleteClipById(clipId)
    }

    suspend fun addExport(export: ExportedVideo) {
        exportDao.insertExport(export.toEntity())
    }

    suspend fun deleteExport(exportId: String, filePath: String) {
        runCatching { File(filePath).delete() }
        exportDao.deleteExportById(exportId)
    }

    suspend fun addThumbnail(thumbnail: GeneratedThumbnail) {
        thumbnailDao.insertThumbnail(thumbnail.toEntity())
    }

    suspend fun deleteThumbnail(thumbnailId: String, filePath: String) {
        runCatching { File(filePath).delete() }
        thumbnailDao.deleteThumbnailById(thumbnailId)
    }

    suspend fun ensureDefaultProviders() {
        val existing = providerDao.getProviders()
        if (existing.isEmpty()) {
            providerDao.upsertProviders(
                listOf(
                    AiProviderEntity(
                        id = "firebase_gemini_flash",
                        name = "Firebase AI Logic (Primary)",
                        modelName = "gemini-3.8-flash",
                        providerType = "FIREBASE_AI_LOGIC",
                        priority = 1,
                        status = ProviderStatus.AVAILABLE.name,
                        isEnabled = true,
                        endpointUrl = "",
                        apiKey = "",
                        lastError = null,
                        lastCheckedMs = 0L
                    ),
                    AiProviderEntity(
                        id = "firebase_gemini_lite",
                        name = "Firebase AI Logic (Fallback)",
                        modelName = "gemini-3.5-flash-lite",
                        providerType = "FIREBASE_AI_LOGIC",
                        priority = 2,
                        status = ProviderStatus.AVAILABLE.name,
                        isEnabled = true,
                        endpointUrl = "",
                        apiKey = "",
                        lastError = null,
                        lastCheckedMs = 0L
                    ),
                    AiProviderEntity(
                        id = "custom_compatible_backend",
                        name = "Custom Authorized Provider",
                        modelName = "gpt-4o-mini",
                        providerType = "CUSTOM_COMPATIBLE",
                        priority = 3,
                        status = ProviderStatus.UNCONFIGURED.name,
                        isEnabled = false,
                        endpointUrl = "",
                        apiKey = "",
                        lastError = null,
                        lastCheckedMs = 0L
                    )
                )
            )
        }
    }

    suspend fun getProviders(): List<AiProviderConfig> {
        ensureDefaultProviders()
        return providerDao.getProviders().map { it.toDomain() }
    }

    suspend fun upsertProvider(provider: AiProviderConfig) {
        providerDao.upsertProvider(provider.toEntity())
    }

    suspend fun updateProviderStatus(id: String, status: ProviderStatus, error: String?) {
        providerDao.updateProviderStatus(id, status.name, error, System.currentTimeMillis())
    }

    suspend fun clearAllLocalProjectsAndMedia(context: Context) {
        projectDao.deleteAllProjects()
        exportDao.deleteAllExports()
        transcriptCacheDao.clearCache()
        listOf("clips", "exports", "thumbnails", "imports").forEach { dirName ->
            val dir = File(context.filesDir, dirName)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun ProjectEntity.toDomain() = ClipProject(
        id = id,
        name = name,
        sourceUri = sourceUri,
        sourceFileName = sourceFileName,
        sourceDurationMs = sourceDurationMs,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        thumbnailPath = thumbnailPath,
        aspectRatio = AspectRatioPreset.fromWire(aspectRatio),
        durationPreset = ClipDurationPreset.entries.firstOrNull { it.name == durationPreset }
            ?: ClipDurationPreset.SHORT_15_30,
        customMinSeconds = customMinSeconds,
        customMaxSeconds = customMaxSeconds,
        status = ProcessingStatus.fromWire(status),
        statusMessage = statusMessage,
        progressPercent = progressPercent,
        clipCount = clipCount,
        detectedLanguage = detectedLanguage,
        cloudSynced = cloudSynced,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun ClipProject.toEntity() = ProjectEntity(
        id = id,
        name = name,
        sourceUri = sourceUri,
        sourceFileName = sourceFileName,
        sourceDurationMs = sourceDurationMs,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        thumbnailPath = thumbnailPath,
        aspectRatio = aspectRatio.wireValue,
        durationPreset = durationPreset.name,
        customMinSeconds = customMinSeconds,
        customMaxSeconds = customMaxSeconds,
        status = status.name,
        statusMessage = statusMessage,
        progressPercent = progressPercent,
        clipCount = clipCount,
        detectedLanguage = detectedLanguage,
        cloudSynced = cloudSynced,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun ClipEntity.toDomain(): GeneratedClip {
        val hashtags = runCatching { json.decodeFromString<List<String>>(hashtagsJson) }.getOrDefault(emptyList())
        val captions = runCatching { json.decodeFromString<List<CaptionSegment>>(captionsJson) }.getOrDefault(emptyList())
        val adjustment = runCatching { json.decodeFromString<ClipAdjustment>(adjustmentJson) }.getOrDefault(ClipAdjustment())
        return GeneratedClip(
            id = id,
            projectId = projectId,
            title = title,
            description = description,
            openingHook = openingHook,
            selectionReason = selectionReason,
            category = HighlightCategory.entries.firstOrNull { it.name == category } ?: HighlightCategory.INTERESTING,
            viralityScore = viralityScore,
            viralityExplanation = viralityExplanation,
            socialCaption = socialCaption,
            hashtags = hashtags,
            language = language,
            startMs = startMs,
            endMs = endMs,
            aspectRatio = AspectRatioPreset.fromWire(aspectRatio),
            captions = captions,
            adjustment = adjustment,
            clipFilePath = clipFilePath,
            thumbnailPath = thumbnailPath,
            isGenerated = isGenerated,
            updatedAtMs = updatedAtMs
        )
    }

    private fun GeneratedClip.toEntity() = ClipEntity(
        id = id,
        projectId = projectId,
        title = title,
        description = description,
        openingHook = openingHook,
        selectionReason = selectionReason,
        category = category.name,
        viralityScore = viralityScore,
        viralityExplanation = viralityExplanation,
        socialCaption = socialCaption,
        hashtagsJson = json.encodeToString(hashtags),
        language = language,
        startMs = startMs,
        endMs = endMs,
        aspectRatio = aspectRatio.wireValue,
        captionsJson = json.encodeToString(captions),
        adjustmentJson = json.encodeToString(adjustment),
        clipFilePath = clipFilePath,
        thumbnailPath = thumbnailPath,
        isGenerated = isGenerated,
        updatedAtMs = updatedAtMs
    )

    private fun ExportedMediaEntity.toDomain() = ExportedVideo(
        id = id,
        clipId = clipId,
        projectId = projectId,
        title = title,
        filePath = filePath,
        publicUri = publicUri,
        resolution = ExportResolution.entries.firstOrNull { it.name == resolution } ?: ExportResolution.HD_720P,
        aspectRatio = AspectRatioPreset.fromWire(aspectRatio),
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        thumbnailPath = thumbnailPath,
        createdAtMs = createdAtMs
    )

    private fun ExportedVideo.toEntity() = ExportedMediaEntity(
        id = id,
        clipId = clipId,
        projectId = projectId,
        title = title,
        filePath = filePath,
        publicUri = publicUri,
        resolution = resolution.name,
        aspectRatio = aspectRatio.wireValue,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        thumbnailPath = thumbnailPath,
        createdAtMs = createdAtMs
    )

    private fun ThumbnailEntity.toDomain() = GeneratedThumbnail(
        id = id,
        clipId = clipId,
        projectId = projectId,
        filePath = filePath,
        overlayTitle = overlayTitle,
        frameTimestampMs = frameTimestampMs,
        textColorHex = textColorHex,
        aspectRatio = AspectRatioPreset.fromWire(aspectRatio),
        createdAtMs = createdAtMs
    )

    private fun GeneratedThumbnail.toEntity() = ThumbnailEntity(
        id = id,
        clipId = clipId,
        projectId = projectId,
        filePath = filePath,
        overlayTitle = overlayTitle,
        frameTimestampMs = frameTimestampMs,
        textColorHex = textColorHex,
        aspectRatio = aspectRatio.wireValue,
        createdAtMs = createdAtMs
    )

    private fun AiProviderEntity.toDomain() = AiProviderConfig(
        id = id,
        name = name,
        modelName = modelName,
        providerType = providerType,
        priority = priority,
        status = ProviderStatus.entries.firstOrNull { it.name == status } ?: ProviderStatus.UNAVAILABLE,
        isEnabled = isEnabled,
        endpointUrl = endpointUrl,
        apiKey = apiKey,
        lastError = lastError,
        lastCheckedMs = lastCheckedMs
    )

    private fun AiProviderConfig.toEntity() = AiProviderEntity(
        id = id,
        name = name,
        modelName = modelName,
        providerType = providerType,
        priority = priority,
        status = status.name,
        isEnabled = isEnabled,
        endpointUrl = endpointUrl,
        apiKey = apiKey,
        lastError = lastError,
        lastCheckedMs = lastCheckedMs
    )
}
