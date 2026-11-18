package com.example.data.ai

import com.example.domain.model.HighlightCategory
import kotlinx.serialization.Serializable

@Serializable
data class AiWordDto(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val isKeyword: Boolean = false
)

@Serializable
data class AiCaptionSegmentDto(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<AiWordDto> = emptyList()
)

@Serializable
data class AiSuggestedClipDto(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String,
    val openingHook: String,
    val selectionReason: String,
    val category: String = HighlightCategory.INTERESTING.name,
    val viralityScore: Int,
    val viralityExplanation: String,
    val socialCaption: String,
    val hashtags: List<String> = emptyList(),
    val language: String = "English",
    val captions: List<AiCaptionSegmentDto> = emptyList()
)

@Serializable
data class AiVideoAnalysisResult(
    val detectedLanguage: String = "English",
    val summary: String = "",
    val clips: List<AiSuggestedClipDto> = emptyList()
)

@Serializable
data class AiMetadataSuggestion(
    val title: String,
    val description: String,
    val openingHook: String,
    val socialCaption: String,
    val hashtags: List<String>
)
