package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.projectego.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Today's entertainment time, split by app.
 *
 * A ring rather than a bar list because the question it answers is "which share
 * of the day was this", and a share is easier to see as an arc. Slices are
 * separated by a thick ink edge instead of by hue, so two light fills next to
 * each other still read as two slices - and every slice is named in the legend
 * beside the ring, which is where the reading actually happens.
 *
 * Nothing animates in. Touch selects, and a drag still scrolls the panel.
 */
class BrutalistDonut(context: Context) : View(context) {

    /**
     * One slice. [hatched] marks the aggregate - the minutes that belong to apps
     * past the fourth - so it is distinguishable as a shape and not only as a
     * colour nobody can name.
     */
    data class Slice(val label: String, val minutes: Long, val color: Int, val hatched: Boolean = false)

    /** The tapped slice's index, or -1 when the tap cleared the selection. */
    var onSliceTapped: ((Int) -> Unit)? = null

    /** Printed under the number when no slice is selected. */
    var totalCaption: String = ""

    private val palette = AlivePalette(context)
    private val dimens = AliveDimens(context)

    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dimens.borderButton.toFloat()
        color = palette.ink
    }
    private val holeEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dimens.borderBold.toFloat()
        color = palette.ink
    }
    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = palette.ink
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.silkscreen_bold)
        textSize = sp(40f)
        textAlign = Paint.Align.CENTER
        color = palette.ink
    }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.ibm_plex_mono_regular)
        textSize = sp(9f)
        textAlign = Paint.Align.CENTER
        color = palette.inkSecondary
    }

    private val bounds = RectF()
    private val hole = RectF()
    private val path = Path()

    private var slices: List<Slice> = emptyList()
    private var totalMinutes = 0L
    private var selected = -1

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            selectAt(event.x, event.y)
            return true
        }
    })

    fun setData(slices: List<Slice>, totalMinutes: Long) {
        this.slices = slices
        this.totalMinutes = totalMinutes
        selected = -1
        invalidate()
    }

    val selectedIndex: Int get() = selected

    /** Driven by the legend beside the ring, so both point at the same slice. */
    fun select(index: Int) {
        selected = if (index in slices.indices) index else -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centreX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val centreY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val outer = min(
            (width - paddingLeft - paddingRight) / 2f,
            (height - paddingTop - paddingBottom) / 2f
        ) - holeEdge.strokeWidth
        val inner = outer * HOLE_RATIO
        if (outer <= 0f) return

        if (totalMinutes <= 0L || slices.isEmpty()) {
            drawEmptyRing(canvas, centreX, centreY, outer, inner)
            return
        }

        var startAngle = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.minutes / totalMinutes
            val isSelected = index == selected
            val dimmed = selected != -1 && !isSelected
            val lift = if (isSelected) dp(6f) else 0f
            val mid = Math.toRadians((startAngle + sweep / 2f).toDouble())

            bounds.set(
                centreX - outer + (cos(mid) * lift).toFloat(),
                centreY - outer + (sin(mid) * lift).toFloat(),
                centreX + outer + (cos(mid) * lift).toFloat(),
                centreY + outer + (sin(mid) * lift).toFloat()
            )

            face.color = slice.color
            face.alpha = if (dimmed) DIMMED_ALPHA else FULL_ALPHA
            edge.alpha = if (dimmed) DIMMED_ALPHA else FULL_ALPHA

            // Outer arc out, inner arc back: one closed ring segment, so the ink
            // edge runs along both radii and separates neighbouring slices even
            // when their fills are both light.
            path.reset()
            path.arcTo(bounds, startAngle, sweep)
            hole.set(bounds)
            hole.inset(bounds.width() * (1f - HOLE_RATIO) / 2f, bounds.height() * (1f - HOLE_RATIO) / 2f)
            path.arcTo(hole, startAngle + sweep, -sweep, false)
            path.close()
            canvas.drawPath(path, face)
            canvas.drawPath(path, edge)
            if (slice.hatched) {
                // Clipped to the slice, so the hatch belongs to that wedge and
                // not to the ring: "not one app, the rest of them".
                canvas.save()
                canvas.clipPath(path)
                drawHatch(canvas, bounds)
                canvas.restore()
            }

            startAngle += sweep
        }

        face.alpha = FULL_ALPHA
        edge.alpha = FULL_ALPHA
        drawCentre(canvas, centreX, centreY, inner)
    }

    private fun drawHatch(canvas: Canvas, area: RectF) {
        val step = dp(7f)
        var x = area.left - area.height()
        while (x < area.right) {
            canvas.drawLine(x, area.bottom, x + area.height(), area.top, hatchPaint)
            x += step
        }
    }

    private fun drawEmptyRing(canvas: Canvas, centreX: Float, centreY: Float, outer: Float, inner: Float) {
        bounds.set(centreX - outer, centreY - outer, centreX + outer, centreY + outer)
        face.color = palette.white
        canvas.drawArc(bounds, 0f, 360f, true, face)
        canvas.drawArc(bounds, 0f, 360f, true, edge)
        bounds.set(centreX - inner, centreY - inner, centreX + inner, centreY + inner)
        face.color = palette.paper
        canvas.drawArc(bounds, 0f, 360f, true, face)
        canvas.drawArc(bounds, 0f, 360f, true, holeEdge)
        drawCentre(canvas, centreX, centreY, inner)
    }

    /** The number in the hole: the selected slice, or the day's total. */
    private fun drawCentre(canvas: Canvas, centreX: Float, centreY: Float, inner: Float) {
        val slice = slices.getOrNull(selected)
        val number = (slice?.minutes ?: totalMinutes).toString()
        val caption = slice?.label ?: totalCaption
        val maxWidth = inner * 1.5f

        val numberMetrics = numberPaint.fontMetrics
        val captionMetrics = captionPaint.fontMetrics
        val numberHeight = numberMetrics.descent - numberMetrics.ascent
        val captionHeight = if (caption.isEmpty()) 0f else captionMetrics.descent - captionMetrics.ascent + dp(4f)
        val top = centreY - (numberHeight + captionHeight) / 2f

        canvas.drawText(
            number,
            centreX,
            top - numberMetrics.ascent,
            numberPaint
        )
        if (caption.isNotEmpty()) {
            val fitted = TextUtils.ellipsize(caption.uppercase(), captionPaint, maxWidth, TextUtils.TruncateAt.END)
            canvas.drawText(
                fitted,
                0,
                fitted.length,
                centreX,
                top + numberHeight + dp(4f) - captionMetrics.ascent,
                captionPaint
            )
        }
    }

    private fun selectAt(x: Float, y: Float) {
        if (slices.isEmpty() || totalMinutes <= 0L) return

        val centreX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val centreY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val outer = min(
            (width - paddingLeft - paddingRight) / 2f,
            (height - paddingTop - paddingBottom) / 2f
        ) - holeEdge.strokeWidth
        val distance = hypot(x - centreX, y - centreY)
        if (distance > outer + dp(8f) || distance < outer * HOLE_RATIO * 0.8f) return

        // atan2 gives -180..180 from the positive x axis; the ring starts at the
        // top and runs clockwise, so both have to be shifted into that frame.
        var angle = Math.toDegrees(atan2((y - centreY).toDouble(), (x - centreX).toDouble())).toFloat() + 90f
        if (angle < 0f) angle += 360f

        var swept = 0f
        slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.minutes / totalMinutes
            if (angle >= swept && angle < swept + sweep) {
                selected = if (selected == index) -1 else index
                invalidate()
                onSliceTapped?.invoke(selected)
                return
            }
            swept += sweep
        }
    }

    /** A TalkBack double tap has no coordinate, so it walks to the next slice. */
    override fun performClick(): Boolean {
        if (slices.isNotEmpty()) {
            selected = if (selected + 1 < slices.size) selected + 1 else 0
            invalidate()
            onSliceTapped?.invoke(selected)
        }
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        taps.onTouchEvent(event)
        // See BrutalistBarChart: claiming the gesture is what makes taps arrive,
        // and the scroll view still intercepts the moment the finger moves.
        return true
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        /** The hole as a share of the outer radius. */
        const val HOLE_RATIO = 0.56f
        const val FULL_ALPHA = 255
        const val DIMMED_ALPHA = 96
    }
}
