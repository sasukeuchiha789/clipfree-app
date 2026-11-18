package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class FaceFocusResult(
    val focusX: Float,
    val focusY: Float,
    val faceDetectionsCount: Int,
    val statusDescription: String
)

object FaceReframingAnalyzer {

    suspend fun analyzeSubjectFocus(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long
    ): FaceFocusResult = withContext(Dispatchers.IO) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)
        val retriever = MediaMetadataRetriever()

        var sumX = 0f
        var sumY = 0f
        var detectionCount = 0

        try {
            retriever.setDataSource(context, videoUri)
            val span = (endMs - startMs).coerceAtLeast(1000L)
            val sampleOffsets = listOf(0.15f, 0.38f, 0.62f, 0.85f)

            for (fraction in sampleOffsets) {
                val tsMs = startMs + (span * fraction).toLong()
                val raw = retriever.getFrameAtTime(tsMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                val frame = VideoInspector.scaleBitmapMaxEdge(raw, 640)
                if (frame !== raw) raw.recycle()

                try {
                    val image = InputImage.fromBitmap(frame, 0)
                    val faces = detector.process(image).await()
                    val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    if (primaryFace != null && frame.width > 0 && frame.height > 0) {
                        val centerX = (primaryFace.boundingBox.exactCenterX() / frame.width).coerceIn(0.1f, 0.9f)
                        val centerY = (primaryFace.boundingBox.exactCenterY() / frame.height).coerceIn(0.1f, 0.9f)
                        sumX += centerX
                        sumY += centerY
                        detectionCount++
                    }
                } catch (_: Exception) {
                } finally {
                    frame.recycle()
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
            runCatching { detector.close() }
        }

        if (detectionCount > 0) {
            FaceFocusResult(
                focusX = (sumX / detectionCount).coerceIn(0.15f, 0.85f),
                focusY = (sumY / detectionCount).coerceIn(0.15f, 0.85f),
                faceDetectionsCount = detectionCount,
                statusDescription = "Face-tracked framing ($detectionCount face detections)"
            )
        } else {
            FaceFocusResult(
                focusX = 0.5f,
                focusY = 0.45f,
                faceDetectionsCount = 0,
                statusDescription = "Center framing (no faces detected)"
            )
        }
    }
}
