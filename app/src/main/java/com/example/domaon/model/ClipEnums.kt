package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ClipDurationPreset(
    val label: String,
    val minSeconds: Int,
    val maxSeconds: Int
) {
    SHORT_15_30("15-30s", 15, 30),
    MEDIUM_30_45("30-45s", 30, 45),
    STANDARD_45_60("45-60s", 45, 60),
    LONG_60_90("60-90s", 60, 90),
    CUSTOM("Custom", 10, 120)
}

enum class AspectRatioPreset(
    val wireValue: String,
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    VERTICAL_9_16("9:16", "9:16 Vertical (Reels / Shorts / TikTok)", 9, 16),
    PORTRAIT_4_5("4:5", "4:5 Portrait (Feed)", 4, 5),
    SQUARE_1_1("1:1", "1:1 Square", 1, 1),
    LANDSCAPE_16_9("16:9", "16:9 Landscape", 16, 9);

    companion object {
        fun fromWire(value: String): AspectRatioPreset =
            entries.firstOrNull { it.wireValue == value || it.name == value } ?: VERTICAL_9_16
    }
}

enum class CaptionStylePreset(val label: String) {
    WORD_BY_WORD("Word-by-Word"),
    KARAOKE("Karaoke Highlight"),
    BOLD_CREATOR("Bold Creator"),
    MINIMAL("Minimal Clean"),
    HIGHLIGHTED_KEYWORDS("Keyword Pop")
}

enum class CaptionPosition(val label: String, val verticalBias: Float) {
    TOP("Top", 0.18f),
    CENTER("Center", 0.50f),
    BOTTOM("Bottom", 0.78f)
}

enum class CaptionAnimation(val label: String) {
    NONE("Static"),
    POP("Pop Scale"),
    FADE("Smooth Fade"),
    SLIDE_UP("Slide Up")
}

enum class CaptionFontStyle(val label: String) {
    MODERN_SANS("Modern Sans"),
    DISPLAY_BOLD("Space Display"),
    CONDENSED("Creator Heavy")
}

enum class ExportResolution(val label: String, val shortSidePx: Int) {
    HD_720P("720p HD", 720),
    FHD_1080P("1080p Full HD", 1080),
    UHD_4K("4K Ultra HD", 2160)
}

enum class ProcessingStatus(val label: String) {
    DRAFT("Draft"),
    PROCESSING("Processing"),
    PAUSED("Paused"),
    COMPLETED("Ready"),
    FAILED("Needs Retry");

    companion object {
        fun fromWire(raw: String): ProcessingStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

enum class AiAvailabilityState(val label: String) {
    AI_AVAILABLE("AI Available"),
    TEMPORARILY_LIMITED("Temporarily Limited"),
    QUOTA_EXHAUSTED("Quota Exhausted"),
    OFFLINE_MODE("Offline Mode"),
    PROCESSING("Processing"),
    READY_TO_RETRY("Ready to Retry")
}

enum class AiMode(val label: String) {
    SMART_AI("Smart AI Mode"),
    RETRY_AI("Retry AI Mode"),
    LOCAL_FALLBACK("Local Mode")
}

enum class ProviderStatus(val label: String) {
    AVAILABLE("Available"),
    TEMPORARILY_LIMITED("Rate Limited"),
    QUOTA_EXHAUSTED("Quota Exhausted"),
    UNAVAILABLE("Unavailable"),
    UNCONFIGURED("Not Configured")
}

@Serializable
enum class HighlightCategory(val label: String) {
    STRONG_HOOK("Strong Hook"),
    INTERESTING("Interesting"),
    FUNNY("Funny"),
    EMOTIONAL("Emotional"),
    EDUCATIONAL("Educational"),
    IMPORTANT_STATEMENT("Key Statement"),
    SURPRISING("Surprising"),
    QA("Q&A"),
    ADVICE("Useful Advice"),
    STORYTELLING("Storytelling"),
    CONCLUSION("Strong Conclusion")
}
