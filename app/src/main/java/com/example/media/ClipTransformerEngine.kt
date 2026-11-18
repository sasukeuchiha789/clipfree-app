package com.example.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipAdjustment
import com.example.domain.model.ExportResolution
import com.example.domain.model.GeneratedClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class TransformOutput(
    val file: File,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fileSizeBytes: Long
)

private const val TAG = "ClipTransformerEngine"

object ClipTransformerEngine {

    @OptIn(UnstableApi::class)
    fun computeOutputDimensions(
        aspectRatio: AspectRatioPreset,
        resolution: ExportResolution
    ): Pair<Int, Int> {
        val shortSide = resolution.shortSidePx.makeMultipleOf16()
        return when (aspectRatio) {
            AspectRatioPreset.VERTICAL_9_16 -> {
                val h = (shortSide * 16f / 9f).toInt().makeMultipleOf16()
                shortSide to h
            }
            AspectRatioPreset.PORTRAIT_4_5 -> {
                val h = (shortSide * 5f / 4f).toInt().makeMultipleOf16()
                shortSide to h
            }
            AspectRatioPreset.SQUARE_1_1 -> {
                shortSide to shortSide
            }
            AspectRatioPreset.LANDSCAPE_16_9 -> {
                val w = (shortSide * 16f / 9f).toInt().makeMultipleOf16()
                w to shortSide
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun computeCropEffect(
        sourceWidth: Int,
        sourceHeight: Int,
        targetAspect: AspectRatioPreset,
        adjustment: ClipAdjustment
    ): Crop {
        val srcW = sourceWidth.coerceAtLeast(1).toFloat()
        val srcH = sourceHeight.coerceAtLeast(1).toFloat()
        val srcAspect = srcW / srcH
        val targetRatio = targetAspect.widthRatio.toFloat() / targetAspect.heightRatio.toFloat()

        // Normalized crop window size in [0..1] of source dimensions
        var cropWFraction = 1f
        var cropHFraction = 1f
        if (srcAspect > targetRatio) {
            cropWFraction = (targetRatio / srcAspect).coerceIn(0.15f, 1f)
        } else {
            cropHFraction = (srcAspect / targetRatio).coerceIn(0.15f, 1f)
        }

        val zoom = adjustment.zoomScale.coerceIn(1.0f, 2.2f)
        cropWFraction = (cropWFraction / zoom).coerceIn(0.12f, 1f)
        cropHFraction = (cropHFraction / zoom).coerceIn(0.12f, 1f)

        val focusX = (adjustment.subjectFocusX + adjustment.cropOffsetX * 0.35f)
            .coerceIn(cropWFraction / 2f, 1f - cropWFraction / 2f)
        val focusY = (adjustment.subjectFocusY + adjustment.cropOffsetY * 0.35f)
            .coerceIn(cropHFraction / 2f, 1f - cropHFraction / 2f)

        // Convert [0..1] (top-left origin) to Media3 Crop NDC [-1..1] (bottom-left origin)
        val leftNdc = ((focusX - cropWFraction / 2f) * 2f - 1f).coerceIn(-1f, 0.9f)
        val rightNdc = ((focusX + cropWFraction / 2f) * 2f - 1f).coerceIn(leftNdc + 0.1f, 1f)
        val topNdc = (1f - (focusY - cropHFraction / 2f) * 2f).coerceIn(-0.9f, 1f)
        val bottomNdc = (1f - (focusY + cropHFraction / 2f) * 2f).coerceIn(-1f, topNdc - 0.1f)

        return Crop(leftNdc, rightNdc, bottomNdc, topNdc)
    }

    @OptIn(UnstableApi::class)
    suspend fun renderClipMp4(
        context: Context,
        sourceUri: Uri,
        sourceWidth: Int,
        sourceHeight: Int,
        clip: GeneratedClip,
        resolution: ExportResolution,
        outputFile: File,
        burnCaptions: Boolean = true,
        onProgress: (Int) -> Unit = {}
    ): Result<TransformOutput> {
        if (!DeviceCapabilityChecker.hasSufficientStorage(context, 40L * 1024L * 1024L)) {
            return Result.failure(IllegalStateException("Low device storage. Free up at least 40 MB and retry."))
        }

        val actualSourceDurationMs = queryDurationSafely(context, sourceUri)
        val safeMaxDuration = if (actualSourceDurationMs > 1000L) actualSourceDurationMs else clip.effectiveEndMs
        val startMs = clip.effectiveStartMs.coerceIn(0L, (safeMaxDuration - 1000L).coerceAtLeast(0L))
        val endMs = clip.effectiveEndMs.coerceIn((startMs + 1000L).coerceAtMost(safeMaxDuration), safeMaxDuration)
        if (endMs <= startMs) {
            return Result.failure(IllegalStateException("Invalid clip range for rendering."))
        }

        val (outW, outH) = computeOutputDimensions(clip.aspectRatio, resolution)

        // Attempt 1: Full pipeline (Crop + Presentation + Caption OverlayEffect)
        if (burnCaptions && clip.captions.isNotEmpty()) {
            val fullEffects = mutableListOf<Effect>(
                computeCropEffect(sourceWidth, sourceHeight, clip.aspectRatio, clip.adjustment),
                Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT),
                OverlayEffect(
                    listOf(
                        CaptionBitmapOverlay(
                            context = context,
                            clipStartMs = startMs,
                            segments = clip.captions,
                            adjustment = clip.adjustment,
                            outWidth = outW,
                            outHeight = outH
                        )
                    )
                )
            )
            val attempt1 = runTransformerOnce(
                context = context,
                sourceUri = sourceUri,
                startMs = startMs,
                endMs = endMs,
                videoEffects = fullEffects,
                outputFile = outputFile,
                expectedW = outW,
                expectedH = outH,
                onProgress = onProgress
            )
            if (attempt1.isSuccess) return attempt1
            Log.w(TAG, "Full overlay render failed, falling back to reframed export: ${attempt1.exceptionOrNull()?.message}")
        }

        // Attempt 2: Reframed Crop + Presentation without GL BitmapOverlay (avoids emulator EGL_BAD_ATTRIBUTE)
        val reframeEffects = listOf<Effect>(
            computeCropEffect(sourceWidth, sourceHeight, clip.aspectRatio, clip.adjustment),
            Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT)
        )
        val attempt2 = runTransformerOnce(
            context = context,
            sourceUri = sourceUri,
            startMs = startMs,
            endMs = endMs,
            videoEffects = reframeEffects,
            outputFile = outputFile,
            expectedW = outW,
            expectedH = outH,
            onProgress = onProgress
        )
        if (attempt2.isSuccess) return attempt2
        Log.w(TAG, "Reframed render failed, falling back to direct trim export: ${attempt2.exceptionOrNull()?.message}")

        // Attempt 3: Pure trim export (no GL video effects, maximum compatibility on emulators/older codecs)
        return runTransformerOnce(
            context = context,
            sourceUri = sourceUri,
            startMs = startMs,
            endMs = endMs,
            videoEffects = emptyList(),
            outputFile = outputFile,
            expectedW = sourceWidth.coerceAtLeast(1),
            expectedH = sourceHeight.coerceAtLeast(1),
            onProgress = onProgress
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnce(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        videoEffects: List<Effect>,
        outputFile: File,
        expectedW: Int,
        expectedH: Int,
        onProgress: (Int) -> Unit
    ): Result<TransformOutput> {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .apply {
                if (videoEffects.isNotEmpty()) {
                    setEffects(androidx.media3.transformer.Effects(emptyList(), videoEffects))
                }
            }
            .build()

        val exportResult = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Result<File>> { cont ->
                val mainHandler = Handler(Looper.getMainLooper())
                var polling = true
                val progressHolder = ProgressHolder()
                var transformerRef: Transformer? = null

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        polling = false
                        mainHandler.removeCallbacksAndMessages(null)
                        onProgress(100)
                        if (cont.isActive) cont.resume(Result.success(outputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        polling = false
                        mainHandler.removeCallbacksAndMessages(null)
                        if (cont.isActive) {
                            cont.resume(
                                Result.failure(
                                    IllegalStateException(
                                        "Export failed: ${exception.localizedMessage ?: exception.errorCodeName}"
                                    )
                                )
                            )
                        }
                    }
                }

                val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext)
                    .setEnableFallback(true)
                    .build()

                val transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()
                transformerRef = transformer

                val progressRunnable = object : Runnable {
                    override fun run() {
                        if (!polling || !cont.isActive) return
                        val state = runCatching { transformer.getProgress(progressHolder) }.getOrDefault(Transformer.PROGRESS_STATE_UNAVAILABLE)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(0, 99))
                        }
                        mainHandler.postDelayed(this, 250L)
                    }
                }

                cont.invokeOnCancellation {
                    polling = false
                    mainHandler.removeCallbacks(progressRunnable)
                    mainHandler.post { runCatching { transformerRef?.cancel() } }
                }

                try {
                    transformer.start(editedMediaItem, outputFile.absolutePath)
                    mainHandler.post(progressRunnable)
                } catch (e: Exception) {
                    polling = false
                    if (cont.isActive) cont.resume(Result.failure(e))
                }
            }
        }

        return exportResult.fold(
            onSuccess = { file ->
                verifyPlayableMp4(context, file, expectedW, expectedH)
            },
            onFailure = { err ->
                runCatching { outputFile.delete() }
                Result.failure(err)
            }
        )
    }

    private suspend fun queryDurationSafely(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun verifyPlayableMp4(
        context: Context,
        file: File,
        expectedW: Int,
        expectedH: Int
    ): Result<TransformOutput> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 1024L) {
            return@withContext Result.failure(IllegalStateException("Exported MP4 file is missing or empty."))
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.fromFile(file))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) {
                return@withContext Result.failure(IllegalStateException("Exported MP4 failed playability verification."))
            }
            Result.success(
                TransformOutput(
                    file = file,
                    width = expectedW,
                    height = expectedH,
                    durationMs = durationMs,
                    fileSizeBytes = file.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Export verification failed: ${e.localizedMessage}"))
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun saveMp4ToDeviceMovies(
        context: Context,
        sourceFile: File,
        displayTitle: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val sanitizedName = displayTitle.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
                .trim().take(48).ifBlank { "ClipFree_Short" }
            val fileName = "${sanitizedName}_${System.currentTimeMillis()}.mp4"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipFreeAI")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Could not create MediaStore entry."))

            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Failed to write MP4 to device storage."))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Int.makeMultipleOf16(): Int {
        val rounded = ((this + 8) / 16) * 16
        return rounded.coerceAtLeast(16)
    }
}
