package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUri: String,
    val sourceFileName: String,
    val sourceDurationMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val thumbnailPath: String?,
    val aspectRatio: String,
    val durationPreset: String,
    val customMinSeconds: Int,
    val customMaxSeconds: Int,
    val status: String,
    val statusMessage: String,
    val progressPercent: Int,
    val clipCount: Int,
    val detectedLanguage: String,
    val cloudSynced: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(
    tableName = "clips",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val openingHook: String,
    val selectionReason: String,
    val category: String,
    val viralityScore: Int,
    val viralityExplanation: String,
    val socialCaption: String,
    val hashtagsJson: String,
    val language: String,
    val startMs: Long,
    val endMs: Long,
    val aspectRatio: String,
    val captionsJson: String,
    val adjustmentJson: String,
    val clipFilePath: String?,
    val thumbnailPath: String?,
    val isGenerated: Boolean,
    val updatedAtMs: Long
)

@Entity(tableName = "transcript_cache")
data class TranscriptCacheEntity(
    @PrimaryKey val videoSignature: String,
    val analysisJson: String,
    val providerUsed: String,
    val createdAtMs: Long
)

@Entity(tableName = "exported_media")
data class ExportedMediaEntity(
    @PrimaryKey val id: String,
    val clipId: String,
    val projectId: String,
    val title: String,
    val filePath: String,
    val publicUri: String?,
    val resolution: String,
    val aspectRatio: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val thumbnailPath: String?,
    val createdAtMs: Long
)

@Entity(tableName = "thumbnails")
data class ThumbnailEntity(
    @PrimaryKey val id: String,
    val clipId: String,
    val projectId: String,
    val filePath: String,
    val overlayTitle: String,
    val frameTimestampMs: Long,
    val textColorHex: Long,
    val aspectRatio: String,
    val createdAtMs: Long
)

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modelName: String,
    val providerType: String,
    val priority: Int,
    val status: String,
    val isEnabled: Boolean,
    val endpointUrl: String,
    val apiKey: String,
    val lastError: String?,
    val lastCheckedMs: Long
)
