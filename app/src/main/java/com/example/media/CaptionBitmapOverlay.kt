package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.example.R
import com.example.domain.model.CaptionFontStyle
import com.example.domain.model.CaptionPosition
import com.example.domain.model.CaptionSegment
import com.example.domain.model.CaptionStylePreset
import com.example.domain.model.ClipAdjustment

@UnstableApi
class CaptionBitmapOverlay(
    context: Context,
    private val clipStartMs: Long,
    private val segments: List<CaptionSegment>,
    private val adjustment: ClipAdjustment,
    private val outWidth: Int,
    private val outHeight: Int
) : BitmapOverlay() {

    private val bitmap: Bitmap = Bitmap.createBitmap(
        outWidth.coerceAtLeast(16),
        outHeight.coerceAtLeast(16),
        Bitmap.Config.ARGB_8888
    )
    private val canvas = Canvas(bitmap)

    private val typeface: Typeface = when (adjustment.captionFontStyle) {
        CaptionFontStyle.DISPLAY_BOLD -> ResourcesCompat.getFont(context, R.font.space_grotesk)
            ?: Typeface.DEFAULT_BOLD
        CaptionFontStyle.MODERN_SANS -> ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
            ?: Typeface.SANS_SERIF
        CaptionFontStyle.CONDENSED -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = Typeface.create(this@CaptionBitmapOverlay.typeface, Typeface.BOLD)
        textSize = (outWidth * (adjustment.captionSizeSp / 360f)).coerceIn(28f, 96f)
        color = adjustment.captionColorHex.toInt()
        setShadowLayer(8f, 0f, 3f, Color.argb(200, 0, 0, 0))
    }

    private val highlightPaint = TextPaint(textPaint).apply {
        color = adjustment.captionHighlightColorHex.toInt()
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 20, 18, 30)
        style = Paint.Style.FILL
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        bitmap.eraseColor(Color.TRANSPARENT)
        val absoluteTimeMs = clipStartMs + (presentationTimeUs / 1000L)
        val activeSegment = segments.firstOrNull {
            absoluteTimeMs in it.startMs..it.endMs
        } ?: return bitmap

        val displayText = when (adjustment.captionStyle) {
            CaptionStylePreset.WORD_BY_WORD -> {
                val activeWord = activeSegment.words.firstOrNull {
                    absoluteTimeMs in it.startMs..it.endMs
                }?.word
                activeWord ?: activeSegment.text
            }
            CaptionStylePreset.KARAOKE -> activeSegment.text
            CaptionStylePreset.BOLD_CREATOR -> activeSegment.text.uppercase()
            CaptionStylePreset.MINIMAL -> activeSegment.text
            CaptionStylePreset.HIGHLIGHTED_KEYWORDS -> activeSegment.text
        }

        if (displayText.isBlank()) return bitmap

        val maxTextWidth = (outWidth * 0.84f).toInt().coerceAtLeast(100)
        val activePaint = if (adjustment.captionStyle == CaptionStylePreset.KARAOKE) {
            val activeWord = activeSegment.words.firstOrNull {
                absoluteTimeMs in it.startMs..it.endMs
            }
            if (activeWord?.isKeyword == true) highlightPaint else textPaint
        } else {
            textPaint
        }

        val layout = StaticLayout.Builder.obtain(
            displayText,
            0,
            displayText.length,
            activePaint,
            maxTextWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()

        val boxPaddingX = outWidth * 0.045f
        val boxPaddingY = outHeight * 0.015f
        val centerY = when (adjustment.captionPosition) {
            CaptionPosition.TOP -> outHeight * 0.16f
            CaptionPosition.CENTER -> outHeight * 0.50f
            CaptionPosition.BOTTOM -> outHeight * 0.78f
        }
        val topY = (centerY - layout.height / 2f).coerceIn(24f, outHeight - layout.height - 24f)
        val leftX = (outWidth - maxTextWidth) / 2f

        if (adjustment.captionStyle != CaptionStylePreset.MINIMAL) {
            var maxLineWidth = 0f
            for (line in 0 until layout.lineCount) {
                maxLineWidth = maxOf(maxLineWidth, layout.getLineWidth(line))
            }
            val bgRect = RectF(
                (outWidth / 2f) - (maxLineWidth / 2f) - boxPaddingX,
                topY - boxPaddingY,
                (outWidth / 2f) + (maxLineWidth / 2f) + boxPaddingX,
                topY + layout.height + boxPaddingY
            )
            canvas.drawRoundRect(bgRect, 20f, 20f, boxPaint)
        }

        canvas.save()
        canvas.translate(leftX, topY)
        layout.draw(canvas)
        canvas.restore()

        return bitmap
    }
}
