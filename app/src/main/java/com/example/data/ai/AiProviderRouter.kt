package com.example.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.R
import com.example.data.local.PreferencesManager
import com.example.data.local.TranscriptCacheEntity
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.HighlightCategory
import com.example.domain.model.ProviderStatus
import com.example.domain.model.ProviderSwitchEvent
import com.example.media.VideoAnalysisPayload
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "AiProviderRouter"

sealed interface AiRoutingOutcome<out T> {
    data class Success<T>(
        val data: T,
        val providerName: String,
        val modelName: String,
        val fromCache: Boolean = false
    ) : AiRoutingOutcome<T>

    data class QuotaExhausted(val message: String) : AiRoutingOutcome<Nothing>
    data class Offline(val message: String) : AiRoutingOutcome<Nothing>
    data class Error(val message: String) : AiRoutingOutcome<Nothing>
}

class AiProviderRouter(
    private val context: Context,
    private val repository: ProjectRepository,
    private val preferencesManager: PreferencesManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val _availabilityState = MutableStateFlow(AiAvailabilityState.AI_AVAILABLE)
    val availabilityState: StateFlow<AiAvailabilityState> = _availabilityState.asStateFlow()

    fun setAvailabilityState(state: AiAvailabilityState) {
        _availabilityState.value = state
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val analysisSchema: Schema by lazy {
        val wordSchema = Schema.obj(
            properties = mapOf(
                "word" to Schema.string("Single spoken word"),
                "startMs" to Schema.integer("Word start time in ms within video"),
                "endMs" to Schema.integer("Word end time in ms within video"),
                "isKeyword" to Schema.boolean("True if high-impact keyword")
            )
        )
        val captionSchema = Schema.obj(
            properties = mapOf(
                "text" to Schema.string("Caption phrase text"),
                "startMs" to Schema.integer("Phrase start ms"),
                "endMs" to Schema.integer("Phrase end ms"),
                "words" to Schema.array(wordSchema, "Word-level timestamps")
            )
        )
        val clipSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Catchy short title"),
                "startMs" to Schema.integer("Clip start timestamp in milliseconds"),
                "endMs" to Schema.integer("Clip end timestamp in milliseconds"),
                "description" to Schema.string("Short social video description"),
                "openingHook" to Schema.string("Strong opening hook line"),
                "selectionReason" to Schema.string("Why this moment was selected"),
                "category" to Schema.enumeration(
                    HighlightCategory.entries.map { it.name },
                    "Highlight moment category"
                ),
                "viralityScore" to Schema.integer("Virality potential score 0..100"),
                "viralityExplanation" to Schema.string("Reason for virality score without guarantees"),
                "socialCaption" to Schema.string("Ready-to-post social caption"),
                "hashtags" to Schema.array(Schema.string(), "Relevant hashtags"),
                "language" to Schema.string("Detected speech language"),
                "captions" to Schema.array(captionSchema, "Synchronized caption segments")
            )
        )
        Schema.obj(
            properties = mapOf(
                "detectedLanguage" to Schema.string("Primary spoken language"),
                "summary" to Schema.string("Brief summary of the video"),
                "clips" to Schema.array(clipSchema, "Ranked highlight short clips")
            )
        )
    }

    private val metadataSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Catchy title"),
                "description" to Schema.string("Relevant social description"),
                "openingHook" to Schema.string("Strong opening hook"),
                "socialCaption" to Schema.string("Social post caption"),
                "hashtags" to Schema.array(Schema.string(), "Relevant hashtags")
            )
        )
    }

    suspend fun analyzeVideoHighlights(
        payload: VideoAnalysisPayload,
        minClipSec: Int,
        maxClipSec: Int,
        targetAspectRatio: AspectRatioPreset,
        useCache: Boolean,
        preferredLanguage: String
    ): AiRoutingOutcome<AiVideoAnalysisResult> {
        val cacheKey = "${payload.signature}_${minClipSec}_${maxClipSec}"
        if (useCache) {
            val cached = repository.transcriptCacheDao.getCache(cacheKey)
            if (cached != null) {
                val decoded = runCatching {
                    json.decodeFromString<AiVideoAnalysisResult>(cached.analysisJson)
                }.getOrNull()
                if (decoded != null && decoded.clips.isNotEmpty()) {
                    _availabilityState.value = AiAvailabilityState.AI_AVAILABLE
                    return AiRoutingOutcome.Success(
                        data = validateAndClampResult(decoded, payload.metadata.durationMs),
                        providerName = cached.providerUsed,
                        modelName = "Smart AI Cache",
                        fromCache = true
                    )
                }
            }
        }

        if (!isOnline()) {
            _availabilityState.value = AiAvailabilityState.OFFLINE_MODE
            return AiRoutingOutcome.Offline("No internet connection. Local Mode active.")
        }

        _availabilityState.value = AiAvailabilityState.PROCESSING
        val providers = repository.getProviders()
            .filter { it.isEnabled && it.status != ProviderStatus.UNCONFIGURED }
            .sortedBy { it.priority }

        if (providers.isEmpty()) {
            _availabilityState.value = AiAvailabilityState.QUOTA_EXHAUSTED
            return AiRoutingOutcome.QuotaExhausted(context.getString(R.string.quota_exhausted_message))
        }

        var lastQuotaFailure = false
        var lastErrorMsg = "AI analysis unavailable."
        var previousProviderName: String? = null

        for (provider in providers) {
            if (provider.status == ProviderStatus.QUOTA_EXHAUSTED) {
                lastQuotaFailure = true
                continue
            }
            if (provider.providerType == "CUSTOM_COMPATIBLE" &&
                (provider.endpointUrl.isBlank() || provider.apiKey.isBlank())
            ) {
                repository.updateProviderStatus(provider.id, ProviderStatus.UNCONFIGURED, "Missing endpoint or key")
                continue
            }

            if (previousProviderName != null) {
                preferencesManager.recordProviderSwitch(
                    ProviderSwitchEvent(
                        fromProvider = previousProviderName,
                        toProvider = provider.id,
                        reason = "Automatic switch after rate/quota or provider error",
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }

            val outcome = try {
                when (provider.providerType) {
                    "FIREBASE_AI_LOGIC" -> invokeFirebaseAiAnalysis(
                        modelName = provider.modelName,
                        payload = payload,
                        minClipSec = minClipSec,
                        maxClipSec = maxClipSec,
                        targetAspectRatio = targetAspectRatio,
                        preferredLanguage = preferredLanguage
                    )
                    "CUSTOM_COMPATIBLE" -> invokeCustomProviderAnalysis(
                        provider = provider,
                        payload = payload,
                        minClipSec = minClipSec,
                        maxClipSec = maxClipSec,
                        preferredLanguage = preferredLanguage
                    )
                    else -> Result.failure(IllegalStateException("Unsupported provider type"))
                }
            } catch (e: CancellationException) {
                throw e
            }

            if (outcome.isSuccess) {
                val rawResult = outcome.getOrThrow()
                val validated = validateAndClampResult(rawResult, payload.metadata.durationMs)
                if (validated.clips.isEmpty()) {
                    lastErrorMsg = "AI returned no valid highlight intervals within video bounds."
                    previousProviderName = provider.id
                    continue
                }

                repository.updateProviderStatus(provider.id, ProviderStatus.AVAILABLE, null)
                preferencesManager.setActiveProvider(provider.id)
                _availabilityState.value = AiAvailabilityState.AI_AVAILABLE

                if (useCache) {
                    repository.transcriptCacheDao.putCache(
                        TranscriptCacheEntity(
                            videoSignature = cacheKey,
                            analysisJson = json.encodeToString(validated),
                            providerUsed = provider.name,
                            createdAtMs = System.currentTimeMillis()
                        )
                    )
                }

                return AiRoutingOutcome.Success(
                    data = validated,
                    providerName = provider.name,
                    modelName = provider.modelName,
                    fromCache = false
                )
            } else {
                val err = outcome.exceptionOrNull()
                val msg = err?.message.orEmpty()
                Log.e(TAG, "Provider ${provider.id} failed: $msg", err)
                lastErrorMsg = msg.ifBlank { "Provider request failed" }
                previousProviderName = provider.id

                if (isQuotaOrRateLimitError(msg)) {
                    lastQuotaFailure = true
                    val newStatus = if (msg.contains("429") || msg.contains("rate", ignoreCase = true)) {
                        ProviderStatus.TEMPORARILY_LIMITED
                    } else {
                        ProviderStatus.QUOTA_EXHAUSTED
                    }
                    repository.updateProviderStatus(provider.id, newStatus, msg.take(180))
                } else {
                    repository.updateProviderStatus(provider.id, ProviderStatus.UNAVAILABLE, msg.take(180))
                }
            }
        }

        return if (lastQuotaFailure) {
            _availabilityState.value = AiAvailabilityState.QUOTA_EXHAUSTED
            AiRoutingOutcome.QuotaExhausted(context.getString(R.string.quota_exhausted_message))
        } else {
            _availabilityState.value = AiAvailabilityState.READY_TO_RETRY
            AiRoutingOutcome.Error(lastErrorMsg)
        }
    }

    suspend fun regenerateClipMetadata(
        clipTitle: String,
        transcriptText: String,
        hook: String,
        language: String
    ): AiRoutingOutcome<AiMetadataSuggestion> {
        if (!isOnline()) {
            _availabilityState.value = AiAvailabilityState.OFFLINE_MODE
            return AiRoutingOutcome.Offline("Offline mode active. Connect to regenerate metadata.")
        }

        val providers = repository.getProviders()
            .filter { it.isEnabled && it.status != ProviderStatus.UNCONFIGURED && it.providerType == "FIREBASE_AI_LOGIC" }
            .sortedBy { it.priority }

        for (provider in providers) {
            try {
                val config = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = metadataSchema
                }
                val model = Firebase.ai.generativeModel(
                    modelName = provider.modelName,
                    generationConfig = config
                )
                val prompt = """
                    Generate fresh, catchy short-form video metadata strictly grounded in this clip transcript.
                    Current title: $clipTitle
                    Opening hook: $hook
                    Language: $language
                    Transcript: $transcriptText
                    Do not guarantee views or followers. Keep hashtags concise (5-8 tags).
                """.trimIndent()

                val response = withTimeout(45_000L) {
                    model.generateContent(prompt)
                }
                val text = response.text ?: continue
                val parsed = json.decodeFromString<AiMetadataSuggestion>(text)
                repository.updateProviderStatus(provider.id, ProviderStatus.AVAILABLE, null)
                _availabilityState.value = AiAvailabilityState.AI_AVAILABLE
                return AiRoutingOutcome.Success(parsed, provider.name, provider.modelName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (isQuotaOrRateLimitError(msg)) {
                    repository.updateProviderStatus(provider.id, ProviderStatus.QUOTA_EXHAUSTED, msg.take(160))
                }
            }
        }
        _availabilityState.value = AiAvailabilityState.READY_TO_RETRY
        return AiRoutingOutcome.Error("Unable to regenerate suggestions right now.")
    }

    suspend fun testProviderConnection(provider: AiProviderConfig): Result<String> {
        if (!isOnline()) return Result.failure(IllegalStateException("Device is offline."))
        return try {
            when (provider.providerType) {
                "FIREBASE_AI_LOGIC" -> {
                    val model = Firebase.ai.generativeModel(provider.modelName)
                    val res = withTimeout(30_000L) {
                        model.generateContent("Reply with OK only.")
                    }
                    val txt = res.text?.trim().orEmpty()
                    if (txt.isNotEmpty()) {
                        repository.updateProviderStatus(provider.id, ProviderStatus.AVAILABLE, null)
                        _availabilityState.value = AiAvailabilityState.AI_AVAILABLE
                        Result.success("Connected (${provider.modelName})")
                    } else {
                        Result.failure(IllegalStateException("Empty response from ${provider.modelName}"))
                    }
                }
                "CUSTOM_COMPATIBLE" -> {
                    if (provider.endpointUrl.isBlank() || provider.apiKey.isBlank()) {
                        return Result.failure(IllegalStateException("Endpoint URL and API key are required."))
                    }
                    withContext(Dispatchers.IO) {
                        val bodyJson = JSONObject().apply {
                            put("model", provider.modelName)
                            put("messages", JSONArray().put(JSONObject().apply {
                                put("role", "user")
                                put("content", "Reply with OK.")
                            }))
                            put("max_tokens", 8)
                        }
                        val req = Request.Builder()
                            .url(provider.endpointUrl.trim())
                            .addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
                            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                repository.updateProviderStatus(provider.id, ProviderStatus.AVAILABLE, null)
                                Result.success("Connected (${resp.code})")
                            } else {
                                val status = if (resp.code == 429) ProviderStatus.QUOTA_EXHAUSTED else ProviderStatus.UNAVAILABLE
                                repository.updateProviderStatus(provider.id, status, "HTTP ${resp.code}")
                                Result.failure(IllegalStateException("HTTP ${resp.code}"))
                            }
                        }
                    }
                }
                else -> Result.failure(IllegalStateException("Unknown provider type"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Connection failed"
            if (isQuotaOrRateLimitError(msg)) {
                repository.updateProviderStatus(provider.id, ProviderStatus.QUOTA_EXHAUSTED, msg.take(160))
            } else {
                repository.updateProviderStatus(provider.id, ProviderStatus.UNAVAILABLE, msg.take(160))
            }
            Result.failure(e)
        }
    }

    private suspend fun invokeFirebaseAiAnalysis(
        modelName: String,
        payload: VideoAnalysisPayload,
        minClipSec: Int,
        maxClipSec: Int,
        targetAspectRatio: AspectRatioPreset,
        preferredLanguage: String
    ): Result<AiVideoAnalysisResult> {
        return try {
            val config = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
            }
            val model = Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = config
            )
            val durationMs = payload.metadata.durationMs
            val targetClips = 5
            val minExpectedClips = 4

            val promptText = """
                You are an AI automatic video clipping engine for CLIPFREE.AI.
                Analyze the attached spoken audio and timestamped video frames from "${payload.metadata.displayName}".
                Exact total video duration: $durationMs ms (${durationMs / 1000} seconds).
                Preferred output language: $preferredLanguage (or preserve original speech language).
                Target format: ${targetAspectRatio.wireValue}.
                Target clip length: between ${minClipSec * 1000} ms and ${maxClipSec * 1000} ms (adjust proportionally if video is shorter than ${minClipSec * 2}s so multiple distinct moments fit).

                Rules:
                1. Identify between $minExpectedClips and $targetClips distinct, high-impact moments (minimum $minExpectedClips clips, ideally $targetClips clips) covering strong hooks, funny/emotional/educational/storytelling/Q&A/important insights/conclusions.
                2. Every clip's startMs and endMs MUST strictly satisfy 0 <= startMs < endMs <= $durationMs.
                3. Do NOT invent speech or timestamps outside the actual video. Transcribe the actual spoken audio accurately into synchronized caption segments and word timestamps within [startMs, endMs].
                4. Score virality from 0 to 100 based on opening hook, engagement potential, emotional impact, clarity, storytelling, and shareability. Never promise guaranteed views or followers.
                5. Rank clips from highest viralityScore to lowest.
            """.trimIndent()

            val inputContent = content {
                payload.audioBytes?.let { bytes ->
                    inlineData(bytes, payload.audioMimeType ?: "audio/mp4")
                }
                payload.frames.forEach { frame ->
                    text("Video frame at ${frame.timestampMs} ms:")
                    image(frame.bitmap)
                }
                text(promptText)
            }

            val response = withTimeout(60_000L) {
                model.generateContent(inputContent)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Model returned empty response."))
            val parsed = json.decodeFromString<AiVideoAnalysisResult>(rawJson)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("AI analysis timed out after 60s. Retry when ready."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun invokeCustomProviderAnalysis(
        provider: AiProviderConfig,
        payload: VideoAnalysisPayload,
        minClipSec: Int,
        maxClipSec: Int,
        preferredLanguage: String
    ): Result<AiVideoAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val durationMs = payload.metadata.durationMs
            val prompt = """
                Return ONLY raw JSON matching:
                {"detectedLanguage":"string","summary":"string","clips":[{"title":"string","startMs":0,"endMs":15000,"description":"string","openingHook":"string","selectionReason":"string","category":"STRONG_HOOK","viralityScore":82,"viralityExplanation":"string","socialCaption":"string","hashtags":["#shorts"],"language":"$preferredLanguage","captions":[{"text":"string","startMs":0,"endMs":3000,"words":[{"word":"string","startMs":0,"endMs":500,"isKeyword":false}]}]}]}
                Video file: ${payload.metadata.displayName}, durationMs: $durationMs, clip length target: ${minClipSec}-${maxClipSec}s.
                Ensure all timestamps stay strictly within [0, $durationMs].
            """.trimIndent()

            val contentArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })
                payload.frames.take(4).forEach { frame ->
                    val b64 = android.util.Base64.encodeToString(
                        com.example.media.VideoInspector.bitmapToJpegBytes(frame.bitmap, 70),
                        android.util.Base64.NO_WRAP
                    )
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                    })
                }
            }

            val body = JSONObject().apply {
                put("model", provider.modelName)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArr)
                }))
                put("response_format", JSONObject().put("type", "json_object"))
            }

            val req = Request.Builder()
                .url(provider.endpointUrl.trim())
                .addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val codeMsg = if (resp.code == 429) "HTTP 429 RESOURCE_EXHAUSTED" else "HTTP ${resp.code}"
                    return@withContext Result.failure(IllegalStateException(codeMsg))
                }
                val respStr = resp.body?.string().orEmpty()
                val choices = JSONObject(respStr).getJSONArray("choices")
                val msgText = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                val parsed = json.decodeFromString<AiVideoAnalysisResult>(msgText)
                Result.success(parsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun validateAndClampResult(
        raw: AiVideoAnalysisResult,
        videoDurationMs: Long
    ): AiVideoAnalysisResult {
        val safeMaxMs = videoDurationMs.coerceAtLeast(3000L)
        val minClipSpanMs = if (safeMaxMs >= 20_000L) 3000L else (safeMaxMs / 5L).coerceAtLeast(1500L)

        val validClips = raw.clips.mapNotNull { clip ->
            val maxAllowedStart = (safeMaxMs - minClipSpanMs).coerceAtLeast(0L)
            val start = clip.startMs.coerceIn(0L, maxAllowedStart)
            val end = clip.endMs.coerceIn((start + minClipSpanMs).coerceAtMost(safeMaxMs), safeMaxMs)
            if (end <= start) return@mapNotNull null

            val validCaptions = clip.captions.mapNotNull { seg ->
                val segStart = seg.startMs.coerceIn(start, (end - 200L).coerceAtLeast(start))
                val segEnd = seg.endMs.coerceIn((segStart + 200L).coerceAtMost(end), end)
                if (seg.text.isBlank() || segEnd <= segStart) return@mapNotNull null
                val validWords = seg.words.mapNotNull { w ->
                    if (w.word.isBlank()) return@mapNotNull null
                    val ws = w.startMs.coerceIn(segStart, (segEnd - 50L).coerceAtLeast(segStart))
                    val we = w.endMs.coerceIn((ws + 50L).coerceAtMost(segEnd), segEnd)
                    if (we <= ws) return@mapNotNull null
                    w.copy(startMs = ws, endMs = we)
                }
                seg.copy(startMs = segStart, endMs = segEnd, words = validWords)
            }

            clip.copy(
                title = clip.title.trim().take(110).ifBlank { "Highlight Moment" },
                startMs = start,
                endMs = end,
                viralityScore = clip.viralityScore.coerceIn(0, 100),
                hashtags = clip.hashtags.take(12),
                captions = validCaptions
            )
        }.toMutableList()

        // Ensure at least 4 to 5 distinct clips when the video has sufficient duration (>= 6s)
        val desiredMinClips = if (safeMaxMs >= 6000L) 4 else 1
        if (validClips.isNotEmpty() && validClips.size < desiredMinClips) {
            val totalWindows = 5
            val stepMs = (safeMaxMs / totalWindows).coerceAtLeast(minClipSpanMs)
            val templateLang = validClips.first().language
            for (idx in 0 until totalWindows) {
                if (validClips.size >= 5) break
                val wStart = (idx * stepMs).coerceIn(0L, (safeMaxMs - minClipSpanMs).coerceAtLeast(0L))
                val wEnd = (wStart + stepMs).coerceIn((wStart + minClipSpanMs).coerceAtMost(safeMaxMs), safeMaxMs)
                if (wEnd <= wStart) continue
                val wCenter = (wStart + wEnd) / 2L
                val overlapsExisting = validClips.any { existing ->
                    val exCenter = (existing.startMs + existing.endMs) / 2L
                    kotlin.math.abs(exCenter - wCenter) < (stepMs * 0.45f)
                }
                if (!overlapsExisting) {
                    validClips.add(
                        AiSuggestedClipDto(
                            title = "Video Highlight #${validClips.size + 1}",
                            startMs = wStart,
                            endMs = wEnd,
                            description = raw.summary.take(200).ifBlank { "Additional highlight segment from video timeline." },
                            openingHook = "Watch this moment at ${wStart / 1000}s",
                            selectionReason = "Additional distinct video moment for multi-clip coverage.",
                            category = HighlightCategory.INTERESTING.name,
                            viralityScore = (72 - idx * 3).coerceIn(45, 85),
                            viralityExplanation = "Additional distinct segment ensuring at least 4-5 clips from one video.",
                            socialCaption = raw.summary.take(160).ifBlank { "Highlight moment" },
                            hashtags = listOf("#shorts", "#clips", "#highlights"),
                            language = templateLang,
                            captions = emptyList()
                        )
                    )
                }
            }
        }

        return raw.copy(clips = validClips.sortedByDescending { it.viralityScore })
    }

    private fun isQuotaOrRateLimitError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("429") ||
            lower.contains("resource_exhausted") ||
            lower.contains("quota") ||
            lower.contains("rate limit") ||
            lower.contains("too many requests")
    }
}
