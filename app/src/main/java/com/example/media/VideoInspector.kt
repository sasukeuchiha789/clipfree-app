package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.domain.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

data class TimestampedFrame(
    val timestampMs: Long,
    val bitmap: Bitmap
)

data class VideoAnalysisPayload(
    val metadata: VideoMetadata,
    val signature: String,
    val frames: List<TimestampedFrame>,
    val audioBytes: ByteArray?,
    val audioMimeType: String?
)

data class FolderVideoItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

object VideoInspector {

    private val MP4_COMPATIBLE_AUDIO_MIMES = setOf(
        "audio/mp4a-latm",
        "audio/mpeg",
        "audio/3gpp",
        "audio/amr-wb"
    )

    suspend fun listVideosInTreeFolder(
        context: Context,
        treeUri: Uri
    ): List<FolderVideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FolderVideoItem>()
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanDocumentFolder(context, treeUri, rootDocId, results, depth = 0, maxDepth = 1)
        } catch (_: Exception) {
        }
        results.sortedBy { it.displayName.lowercase() }
    }

    private fun scanDocumentFolder(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        out: MutableList<FolderVideoItem>,
        depth: Int,
        maxDepth: Int
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext() && out.size < 120) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "video.mp4" else "video.mp4"
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < maxDepth) {
                        scanDocumentFolder(context, treeUri, docId, out, depth + 1, maxDepth)
                    }
                } else if (mime.startsWith("video/") || name.hasVideoExtension()) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    out.add(
                        FolderVideoItem(
                            uri = docUri,
                            displayName = name,
                            mimeType = if (mime.startsWith("video/")) mime else "video/mp4",
                            sizeBytes = size
                        )
                    )
                }
            }
        }
    }

    private fun String.hasVideoExtension(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") ||
            lower.endsWith(".webm") || lower.endsWith(".m4v") || lower.endsWith(".3gp") ||
            lower.endsWith(".avi")
    }

    suspend fun inspectVideo(context: Context, uri: Uri): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri)
        val fileSize = queryFileSize(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

        // Ensure remote/Drive or non-seekable URIs are cached locally so MediaMetadataRetriever,
        // ExoPlayer, and Media3 Transformer always have reliable seekable file access.
        val resolvedUri = resolveReadableSeekableUri(context, uri, displayName)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, resolvedUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0

            if (durationMs <= 0L || width <= 0 || height <= 0) {
                return@withContext Result.failure(
                    IllegalArgumentException("Unsupported or corrupt video file. Please pick a valid MP4/MOV/WebM video.")
                )
            }

            val thumbBitmap = retriever.getFrameAtTime(
                minOf(durationMs / 4, 3_000L) * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            var thumbPath: String? = null
            if (thumbBitmap != null) {
                val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
                val outFile = File(thumbDir, "src_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    val scaled = scaleBitmapMaxEdge(thumbBitmap, 720)
                    scaled.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                    if (scaled !== thumbBitmap) scaled.recycle()
                }
                thumbBitmap.recycle()
                thumbPath = outFile.absolutePath
            }

            val finalFileSize = if (fileSize > 0L) fileSize else {
                if (resolvedUri.scheme == "file") File(resolvedUri.path.orEmpty()).length() else 0L
            }

            Result.success(
                VideoMetadata(
                    uriString = resolvedUri.toString(),
                    displayName = displayName,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    rotationDegrees = rotation,
                    mimeType = mimeType,
                    fileSizeBytes = finalFileSize,
                    thumbnailPath = thumbPath
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Unable to read selected video (${e.localizedMessage ?: "corrupt or inaccessible"}).")
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun resolveReadableSeekableUri(
        context: Context,
        uri: Uri,
        displayName: String
    ): Uri {
        if (uri.scheme == "file") return uri
        val authority = uri.authority.orEmpty().lowercase()
        val isCloudOrVirtual = authority.contains("drive") ||
            authority.contains("docs") ||
            authority.contains("dropbox") ||
            authority.contains("onedrive") ||
            authority.contains("box")

        if (!isCloudOrVirtual) {
            val probe = MediaMetadataRetriever()
            val directlyReadable = try {
                probe.setDataSource(context, uri)
                val dur = probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                dur > 0L
            } catch (_: Exception) {
                false
            } finally {
                runCatching { probe.release() }
            }
            if (directlyReadable) return uri
        }

        // Copy stream to local app storage so cloud/Drive/SAF streams work across WorkManager & restarts
        return try {
            val importsDir = File(context.filesDir, "imports").apply { mkdirs() }
            val ext = displayName.substringAfterLast('.', "mp4").take(6).ifBlank { "mp4" }
            val safeName = displayName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(36)
            val localFile = File(importsDir, "${safeName}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            if (localFile.exists() && localFile.length() > 1024L) {
                Uri.fromFile(localFile)
            } else {
                runCatching { localFile.delete() }
                uri
            }
        } catch (_: Exception) {
            uri
        }
    }

    suspend fun extractAnalysisPayload(
        context: Context,
        metadata: VideoMetadata,
        frameCount: Int = 8
    ): VideoAnalysisPayload = withContext(Dispatchers.IO) {
        val uri = Uri.parse(metadata.uriString)
        val frames = mutableListOf<TimestampedFrame>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val safeDuration = metadata.durationMs.coerceAtLeast(1000L)
            val step = safeDuration / (frameCount + 1)
            for (i in 1..frameCount) {
                val tsMs = (step * i).coerceIn(0L, safeDuration - 100L)
                val raw = retriever.getFrameAtTime(tsMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val scaled = scaleBitmapMaxEdge(raw, 512)
                    if (scaled !== raw) raw.recycle()
                    frames.add(TimestampedFrame(tsMs, scaled))
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val audioBytes = extractCompressedAudioTrack(context, uri, maxBytes = 8 * 1024 * 1024)
        val signature = "${metadata.displayName}_${metadata.fileSizeBytes}_${metadata.durationMs}"

        VideoAnalysisPayload(
            metadata = metadata,
            signature = signature,
            frames = frames,
            audioBytes = audioBytes,
            audioMimeType = if (audioBytes != null) "audio/mp4" else null
        )
    }

    private fun extractCompressedAudioTrack(
        context: Context,
        uri: Uri,
        maxBytes: Int
    ): ByteArray? {
        val extractor = MediaExtractor()
        val tempAudioFile = File(context.cacheDir, "ai_audio_${System.currentTimeMillis()}.m4a")
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
                if (mime in MP4_COMPATIBLE_AUDIO_MIMES) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val buffer = ByteBuffer.allocate(256 * 1024)
            val firstSampleSize = extractor.readSampleData(buffer, 0)
            if (firstSampleSize <= 0) return null

            muxer = MediaMuxer(tempAudioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val bufferInfo = android.media.MediaCodec.BufferInfo()
            var totalBytes = 0
            var sampleCount = 0

            // Write first sample already read
            bufferInfo.offset = 0
            bufferInfo.size = firstSampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
            totalBytes += firstSampleSize
            sampleCount++
            extractor.advance()

            while (totalBytes < maxBytes) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val pts = extractor.sampleTime
                if (pts >= 0L && sampleSize > 0) {
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = pts
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    totalBytes += sampleSize
                    sampleCount++
                }
                extractor.advance()
            }

            if (sampleCount > 0 && muxerStarted) {
                runCatching { muxer.stop() }
                muxerStarted = false
            }
            runCatching { muxer.release() }
            muxer = null

            if (sampleCount > 0 && tempAudioFile.exists() && tempAudioFile.length() in 1024L..(maxBytes + 512_000L)) {
                tempAudioFile.readBytes()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { tempAudioFile.delete() }
        }
    }

    fun scaleBitmapMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxEdge && h <= maxEdge) return src
        val ratio = if (w >= h) maxEdge.toFloat() / w else maxEdge.toFloat() / h
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx) ?: "imported_video.mp4"
                    }
                }
            uri.lastPathSegment?.substringAfterLast('/') ?: "imported_video.mp4"
        } catch (_: Exception) {
            "imported_video.mp4"
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0) return cursor.getLong(idx)
                    }
                }
            0L
        } catch (_: Exception) {
            0L
        }
    }
}
