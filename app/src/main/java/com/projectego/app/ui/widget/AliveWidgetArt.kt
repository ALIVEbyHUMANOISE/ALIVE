package com.projectego.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * The two parts of the card that must be ALIVE's own, painted by ALIVE.
 *
 * A widget layout is built by the launcher, and the launcher cannot load a font
 * that ships inside this app - it falls back to its own without a word of
 * complaint, which is how the pixel figure turned into the system face on the
 * home screen. So the figure and the allowance meter are drawn here, where the
 * typeface is readable, and handed over as pictures.
 */
object AliveWidgetArt {

    /** The size the figure wants to be, and the smallest it will go to fit. */
    private const val FIGURE_SP = 28f
    private const val FIGURE_MIN_SP = 15f

    /** Widest the figure may be: the card's own width minus its padding. */
    private const val FIGURE_MAX_DP = 200f

    // The card, when it is painted instead of drawn by the launcher. Same shape
    // as bg_widget.xml in the app - ink edge, hard shadow, paper face - but the
    // face is allowed to be translucent, which the drawable cannot be: in a
    // layered drawable the ink shadow sits behind the whole card, so a
    // translucent face would show that ink rather than the wallpaper.
    private const val CARD_RADIUS_DP = 16f
    private const val CARD_STROKE_DP = 3f
    private const val CARD_SHADOW_DP = 6f

    /** 232/255: the wallpaper is readable through the card without the ink
     *  losing its contrast against the paper behind it. */
    private const val CARD_FACE_ALPHA = 232

    /**
     * The card itself, at [widthPx] by [heightPx].
     *
     * Painted rather than declared, for one reason: the shadow has to be drawn
     * only where it will be seen. Draw it as a full rectangle behind a
     * translucent face and the face stops being translucent - the ink shows
     * through it as a grey wash and the wallpaper never does. So the shadow is
     * painted first, the face's area is cut out of it, and the face is laid in
     * that hole at [CARD_FACE_ALPHA].
     *
     * The picture is stretched to the widget's real size, so the corner radius
     * and the edge are exact only at the size this is drawn for; a 4x2 card is
     * what that size is.
     */
    fun card(context: Context, widthPx: Int, heightPx: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val radius = CARD_RADIUS_DP * density
        val stroke = CARD_STROKE_DP * density
        val shadow = CARD_SHADOW_DP * density

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ink = ContextCompat.getColor(context, R.color.alive_ink)
        val paper = ContextCompat.getColor(context, R.color.alive_paper)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val faceW = widthPx - shadow
        val faceH = heightPx - shadow
        val face = RectF(0f, 0f, faceW, faceH)

        paint.color = ink
        canvas.drawRoundRect(RectF(shadow, shadow, widthPx.toFloat(), heightPx.toFloat()), radius, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawRoundRect(face, radius, radius, paint)
        paint.xfermode = null

        paint.color = paper
        paint.alpha = CARD_FACE_ALPHA
        canvas.drawRoundRect(face, radius, radius, paint)
        paint.alpha = 255

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = ink
        val inset = stroke / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, faceW - inset, faceH - inset),
            radius,
            radius,
            paint
        )
        return bitmap
    }

    /** The meter is stretched to whatever the card turns out to be, so it is
     * drawn wide and flat: only the seams and the slice edges scale. */
    private const val METER_CANVAS_PX = 1200
    private const val METER_FILL_DP = 16f
    private const val METER_SEAM_PX = 10

    /**
     * The day's duration in the pixel face, at the largest size that still fits
     * the card. Silkscreen is a fixed grid, so the text size is stepped down
     * rather than the picture scaled - that is what keeps its edges on whole
     * pixels instead of turning a pixel font into a blur.
     */
    fun figure(context: Context, text: String, @ColorInt colour: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val maxWidth = FIGURE_MAX_DP * density

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.silkscreen_bold)
                ?: Typeface.DEFAULT_BOLD
            color = colour
            letterSpacing = 0f
            textSize = FIGURE_SP * density
        }
        while (paint.textSize > FIGURE_MIN_SP * density && paint.measureText(text) > maxWidth) {
            paint.textSize -= density
        }

        val metrics = paint.fontMetrics
        val width = max(1, ceil(paint.measureText(text)).toInt())
        val height = max(1, ceil(metrics.descent - metrics.ascent).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -metrics.ascent, paint)
        return bitmap
    }

    /**
     * The allowance as slices in the ring's own colours, ink between them. The
     * empty part of a day is left transparent so the card's own meter frame
     * shows through - paper inside an ink edge, the same thing every other
     * unfilled bar in ALIVE is.
     */
    fun meter(context: Context, slices: List<WidgetMeter.Slice>): Bitmap {
        val height = max(1, ceil(METER_FILL_DP * context.resources.displayMetrics.density).toInt())
        val bitmap = Bitmap.createBitmap(METER_CANVAS_PX, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var edge = 0f
        slices.forEach { slice ->
            val start = edge
            paint.color = ContextCompat.getColor(context, slice.colour)
            edge += METER_CANVAS_PX * slice.share
            canvas.drawRect(start, 0f, edge, height.toFloat(), paint)
        }

        // The seams come last: two solid colours touching is one wider colour,
        // and the whole point of the seam is that these are separate apps.
        paint.color = ContextCompat.getColor(context, R.color.alive_ink)
        edge = 0f
        slices.dropLast(1).forEach { slice ->
            edge += METER_CANVAS_PX * slice.share
            canvas.drawRect(
                edge - METER_SEAM_PX / 2f,
                0f,
                edge + METER_SEAM_PX / 2f,
                height.toFloat(),
                paint
            )
        }

        return bitmap
    }
}
