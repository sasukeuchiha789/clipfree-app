package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionFontStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailGenerator {

    suspend fun extractCandidateFrames(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        count: Int = 4
    ): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<Long, String>>()
        val retriever = MediaMetadataRetriever()
        val thumbDir = File(context.filesDir, "thumbnails/candidates").apply { mkdirs() }
        try {
            retriever.setDataSource(context, videoUri)
            val span = (endMs - startMs).coerceAtLeast(1000L)
            for (i in 0 until count) {
                val frac = (i + 0.5f) / count
                val ts = startMs + (span * frac).toLong()
                val bmp = retriever.getFrameAtTime(ts * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    val scaled = VideoInspector.scaleBitmapMaxEdge(bmp, 640)
                    if (scaled !== bmp) bmp.recycle()
                    val file = File(thumbDir, "cand_${ts}_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { fos ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                    }
                    scaled.recycle()
                    results.add(ts to file.absolutePath)
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
        results
    }

    suspend fun generateStyledThumbnail(
        context: Context,
        videoUri: Uri,
        frameTimestampMs: Long,
        overlayTitle: String,
        textColorHex: Long,
        fontStyle: CaptionFontStyle,
        aspectRatio: AspectRatioPreset,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val frame = retriever.getFrameAtTime(
                frameTimestampMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext Result.failure(IllegalStateException("Could not extract video frame."))

            val (outW, outH) = when (aspectRatio) {
                AspectRatioPreset.VERTICAL_9_16 -> 720 to 1280
                AspectRatioPreset.PORTRAIT_4_5 -> 864 to 1080
                AspectRatioPreset.SQUARE_1_1 -> 1080 to 1080
                AspectRatioPreset.LANDSCAPE_16_9 -> 1280 to 720
            }

            val resultBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)

            // Center-crop source frame onto target canvas
            val srcRatio = frame.width.toFloat() / frame.height.coerceAtLeast(1)
            val dstRatio = outW.toFloat() / outH
            val srcRect = if (srcRatio > dstRatio) {
                val cropW = (frame.height * dstRatio).toInt()
                val left = (frame.width - cropW) / 2
                Rect(left, 0, left + cropW, frame.height)
            } else {
                val cropH = (frame.width / dstRatio).toInt()
                val top = (frame.height - cropH) / 2
                Rect(0, top, frame.width, top + cropH)
            }
            canvas.drawBitmap(frame, srcRect, Rect(0, 0, outW, outH), null)
            frame.recycle()

            // Gradient bottom scrim for readable title contrast
            val scrimPaint = Paint().apply {
                shader = LinearGradient(
                    0f, outH * 0.45f, 0f, outH.toFloat(),
                    Color.TRANSPARENT, Color.argb(215, 16, 14, 24),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, outH * 0.45f, outW.toFloat(), outH.toFloat(), scrimPaint)

            if (overlayTitle.isNotBlank()) {
                val tf = when (fontStyle) {
                    CaptionFontStyle.DISPLAY_BOLD -> ResourcesCompat.getFont(context, R.font.space_grotesk)
                        ?: Typeface.DEFAULT_BOLD
                    CaptionFontStyle.MODERN_SANS -> ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
                        ?: Typeface.SANS_SERIF
                    CaptionFontStyle.CONDENSED -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(tf, Typeface.BOLD)
                    textSize = outW * 0.075f
                    color = textColorHex.toInt()
                    setShadowLayer(12f, 0f, 4f, Color.argb(220, 0, 0, 0))
                }
                val textWidth = (outW * 0.86f).toInt()
                val layout = StaticLayout.Builder.obtain(
                    overlayTitle.trim(),
                    0,
                    overlayTitle.trim().length,
                    textPaint,
                    textWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(3)
                    .build()

                canvas.save()
                canvas.translate((outW - textWidth) / 2f, outH - layout.height - outH * 0.10f)
                layout.draw(canvas)
                canvas.restore()
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            resultBitmap.recycle()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun saveThumbnailToGallery(
        context: Context,
        sourceFile: File,
        title: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
                .trim().take(40).ifBlank { "ClipFree_Cover" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${safeName}_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ClipFreeAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Could not create image MediaStore entry."))
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
