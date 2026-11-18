package com.example.domain.model

import kotlinx.serialization.Serializable

data class VideoMetadata(
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val mimeType: String,
    val fileSizeBytes: Long,
    val thumbnailPath: String? = null
) {
    val effectiveWidth: Int get() = if (rotationDegrees % 180 != 0) height else width
    val effectiveHeight: Int get() = if (rotationDegrees % 180 != 0) width else height
}

@Serializable
data class TranscriptWord(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val isKeyword: Boolean = false
)

@Serializable
data class CaptionSegment(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<TranscriptWord> = emptyList()
)

@Serializable
data class ClipAdjustment(
    val captionStyle: CaptionStylePreset = CaptionStylePreset.BOLD_CREATOR,
    val captionPosition: CaptionPosition = CaptionPosition.BOTTOM,
    val captionAnimation: CaptionAnimation = CaptionAnimation.POP,
    val captionFontStyle: CaptionFontStyle = CaptionFontStyle.DISPLAY_BOLD,
    val captionSizeSp: Float = 22f,
    val captionColorHex: Long = 0xFFFFFFFF,
    val captionHighlightColorHex: Long = 0xFF9878FF,
    val cropOffsetX: Float = 0f, // -1f..1f offset relative to detected focus
    val cropOffsetY: Float = 0f, // -1f..1f
    val zoomScale: Float = 1.0f, // 1.0f..2.2f
    val subjectFocusX: Float = 0.5f, // 0f..1f normalized detected subject center
    val subjectFocusY: Float = 0.45f,
    val faceTrackingStatus: String = "Center framing (pending scan)",
    val startTrimOffsetMs: Long = 0L,
    val endTrimOffsetMs: Long = 0L
)

data class GeneratedClip(
    val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val openingHook: String,
    val selectionReason: String,
    val category: HighlightCategory,
    val viralityScore: Int,
    val viralityExplanation: String,
    val socialCaption: String,
    val hashtags: List<String>,
    val language: String,
    val startMs: Long,
    val endMs: Long,
    val aspectRatio: AspectRatioPreset,
    val captions: List<CaptionSegment>,
    val adjustment: ClipAdjustment,
    val clipFilePath: String? = null,
    val thumbnailPath: String? = null,
    val isGenerated: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val effectiveStartMs: Long get() = (startMs + adjustment.startTrimOffsetMs).coerceAtLeast(0L)
    val effectiveEndMs: Long get() = (endMs + adjustment.endTrimOffsetMs).coerceAtLeast(effectiveStartMs + 3000L)
    val durationMs: Long get() = (effectiveEndMs - effectiveStartMs).coerceAtLeast(1000L)
}

data class ClipProject(
    val id: String,
    val name: String,
    val sourceUri: String,
    val sourceFileName: String,
    val sourceDurationMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val thumbnailPath: String?,
    val aspectRatio: AspectRatioPreset,
    val durationPreset: ClipDurationPreset,
    val customMinSeconds: Int,
    val customMaxSeconds: Int,
    val status: ProcessingStatus,
    val statusMessage: String,
    val progressPercent: Int,
    val clipCount: Int,
    val detectedLanguage: String,
    val cloudSynced: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class ExportedVideo(
    val id: String,
    val clipId: String,
    val projectId: String,
    val title: String,
    val filePath: String,
    val publicUri: String?,
    val resolution: ExportResolution,
    val aspectRatio: AspectRatioPreset,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val thumbnailPath: String?,
    val createdAtMs: Long
)

data class GeneratedThumbnail(
    val id: String,
    val clipId: String,
    val projectId: String,
    val filePath: String,
    val overlayTitle: String,
    val frameTimestampMs: Long,
    val textColorHex: Long,
    val aspectRatio: AspectRatioPreset,
    val createdAtMs: Long
)

data class AiProviderConfig(
    val id: String,
    val name: String,
    val modelName: String,
    val providerType: String, // "FIREBASE_AI_LOGIC" or "CUSTOM_COMPATIBLE"
    val priority: Int,
    val status: ProviderStatus,
    val isEnabled: Boolean,
    val endpointUrl: String = "",
    val apiKey: String = "",
    val lastError: String? = null,
    val lastCheckedMs: Long = 0L
)

data class ProviderSwitchEvent(
    val fromProvider: String,
    val toProvider: String,
    val reason: String,
    val timestampMs: Long
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "English",
    val smartAiCachingEnabled: Boolean = true,
    val cloudSyncConsent: Boolean = false,
    val externalAiProcessingConsent: Boolean = true,
    val isGuestModeActive: Boolean = false,
    val activeProviderId: String = "firebase_gemini_flash",
    val lastSwitchEvent: ProviderSwitchEvent? = null
)

sealed interface AccountSession {
    data object Unauthenticated : AccountSession
    data object Guest : AccountSession
    data class GoogleUser(
        val uid: String,
        val displayName: String,
        val email: String?,
        val photoUrl: String?
    ) : AccountSession
}
