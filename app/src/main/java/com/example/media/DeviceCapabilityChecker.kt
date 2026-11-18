package com.example.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.StatFs
import com.example.domain.model.ExportResolution

object DeviceCapabilityChecker {

    fun isResolutionSupported(
        resolution: ExportResolution,
        sourceWidth: Int,
        sourceHeight: Int
    ): Boolean {
        if (resolution == ExportResolution.HD_720P) return true

        val maxVideoEncodeDim = maxHardwareVideoEncodeDimension()
        return when (resolution) {
            ExportResolution.HD_720P -> true
            ExportResolution.FHD_1080P -> maxVideoEncodeDim >= 1920
            ExportResolution.UHD_4K -> {
                val sourceShortSide = minOf(sourceWidth, sourceHeight)
                val sourceLongSide = maxOf(sourceWidth, sourceHeight)
                val sourceIs4k = sourceShortSide >= 2160 || sourceLongSide >= 3840
                sourceIs4k && maxVideoEncodeDim >= 3840
            }
        }
    }

    fun maxHardwareVideoEncodeDimension(): Int {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var maxDim = 1280
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                if (!info.supportedTypes.any {
                        it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ||
                            it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
                    }
                ) continue
                val caps = runCatching {
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                }.getOrNull() ?: continue
                val videoCaps: MediaCodecInfo.VideoCapabilities = caps.videoCapabilities ?: continue
                maxDim = maxOf(maxDim, videoCaps.supportedWidths.upper, videoCaps.supportedHeights.upper)
            }
            maxDim
        } catch (_: Exception) {
            1920
        }
    }

    fun availableStorageBytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    fun hasSufficientStorage(context: Context, minimumBytes: Long = 100L * 1024L * 1024L): Boolean {
        return availableStorageBytes(context) >= minimumBytes
    }
}
