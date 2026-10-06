package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.projectego.app.R
import kotlin.math.max
import kotlin.math.min

/**
 * The usage chart's bars, drawn rather than assembled from views.
 *
 * Drawn because a month is 31 columns: as views that is 31 layouts with 31 tap
 * listeners inside a scroll view, and the bars still could not carry a texture.
 * Everything the shape of a day means is here - a hollow stub is a day ALIVE
 * never saw, a hatched stub is a day there was no allowance to spend, a square
 * on the baseline is a day with the allowance and nothing spent, an ink bar is
 * a day that used it all up.
 *
 * No entry animation and no springs: the bar is already where it belongs when
 * the card opens. Touch only ever selects, so a drag still scrolls the panel.
 */
class BrutalistBarChart(context: Context) : View(context) {

    /**
     * One column. [fraction] is a share of that day's own allowance, so a full
     * bar means the same thing whatever the allowance was.
     */
    data class Column(
        val label: String,
        val minutes: Long,
        val fraction: Float,
        val kind: DayBarKind
    )

    /** The tapped column's index, or -1 when the tap cleared the selection. */
    var onColumnTapped: ((Int) -> Unit)? = null

    private val palette = AlivePalette(context)
    private val dimens = AliveDimens(context)

    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dimens.borderButton.toFloat()
        color = palette.ink
    }
    private val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = palette.paper
    }
    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = palette.ink
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.press_start_2p)
        textSize = sp(7f)
        textAlign = Paint.Align.CENTER
        color = palette.inkSecondary
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.press_start_2p)
        textSize = sp(8f)
        textAlign = Paint.Align.CENTER
        color = palette.paper
    }
    private val dotInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = palette.ink
        alpha = 34
    }
    private val dotPaper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = palette.paper
        alpha = 46
    }

    private val bubble = RectF()
    private val shape = RectF()

    private var columns: List<Column> = emptyList()
    private var selected = -1

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            selectAt(event.x)
            return true
        }
    })

    fun setData(columns: List<Column>) {
        this.columns = columns
        selected = -1
        requestLayout()
        invalidate()
    }

    /** Which column is selected, so the card can print the day under the chart. */
    val selectedIndex: Int get() = selected

    fun select(index: Int) {
        selected = if (index in columns.indices) index else -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (columns.isEmpty()) return

        val labelMetrics = labelPaint.fontMetrics
        val labelBand = (labelMetrics.descent - labelMetrics.ascent) + dimens.s
        val left = paddingLeft + dimens.s.toFloat()
        val right = width - paddingRight - dimens.s.toFloat()
        val baseline = height - paddingBottom - labelBand
        val top = paddingTop + bubbleBand()
        val plotHeight = baseline - top
        if (right <= left || plotHeight <= 0f) return

        val slot = (right - left) / columns.size
        val barWidth = min(slot * 0.62f, dp(30f)).coerceAtLeast(dp(3f))

        // The axis: one ink rule every bar stands on.
        face.color = palette.ink
        canvas.drawRect(left, baseline, right, baseline + edge.strokeWidth, face)

        columns.forEachIndexed { index, column ->
            val centre = left + slot * index + slot / 2f
            drawColumn(canvas, column, centre, barWidth, top, baseline, plotHeight, index == selected)
            labelPaint.color = if (index == selected) palette.ink else palette.inkSecondary
            if (column.label.isNotEmpty()) {
                canvas.drawText(column.label, centre, baseline + dimens.s - labelMetrics.ascent, labelPaint)
            }
        }

        if (selected in columns.indices) {
            drawValueBubble(canvas, columns[selected], left + slot * selected + slot / 2f, top)
        }
    }

    private fun drawColumn(
        canvas: Canvas,
        column: Column,
        centre: Float,
        barWidth: Float,
        top: Float,
        baseline: Float,
        plotHeight: Float,
        isSelected: Boolean
    ) {
        val half = barWidth / 2f
        val stubHeight = dp(9f)

        when (column.kind) {
            // Never seen: a hollow stub, so the gap in the record is visible
            // instead of being flattened into a zero.
            DayBarKind.MISSING -> {
                shape.set(centre - half, baseline - stubHeight, centre + half, baseline)
                face.color = palette.paper
                canvas.drawRect(shape, face)
                canvas.drawRect(shape, edge)
            }

            // No allowance to spend: same stub height, filled and hatched, so it
            // cannot be mistaken for a day that was merely unseen.
            DayBarKind.RECOVERY -> {
                shape.set(centre - half, baseline - stubHeight, centre + half, baseline)
                face.color = palette.warmGray
                canvas.drawRect(shape, face)
                hatch(canvas, shape)
                canvas.drawRect(shape, edge)
            }

            // An allowance and nothing spent: a square on the baseline. Drawing a
            // hairline bar here would claim a height the day does not have.
            DayBarKind.ZERO -> {
                val tick = min(barWidth, dp(8f))
                shape.set(centre - tick / 2f, baseline - tick, centre + tick / 2f, baseline)
                face.color = palette.ink
                canvas.drawRect(shape, face)
            }

            else -> {
                // A spent day is never invisible: the shortest bar the eye can
                // still find is one pixel of ink edge plus a sliver of fill.
                val height = max(column.fraction * plotHeight, dp(3f))
                shape.set(centre - half, baseline - height, centre + half, baseline)
                val full = column.kind == DayBarKind.FULL
                face.color = if (full) palette.ink else palette.forest
                canvas.drawRect(shape, face)
                if (barWidth >= dp(11f)) {
                    texture(canvas, shape, if (full) dotPaper else dotInk)
                }
                canvas.drawRect(shape, edge)
                if (isSelected) {
                    shape.inset(seam.strokeWidth, seam.strokeWidth)
                    canvas.drawRect(shape, seam)
                }
            }
        }
    }

    /** Pixel dots inside a bar, the same grain the canvas background has. */
    private fun texture(canvas: Canvas, bar: RectF, paint: Paint) {
        val step = dp(5f)
        val dot = dp(1.5f)
        var y = bar.top + step
        while (y + dot < bar.bottom) {
            var x = bar.left + step
            while (x + dot < bar.right) {
                canvas.drawRect(x, y, x + dot, y + dot, paint)
                x += step
            }
            y += step
        }
    }

    /** Three diagonal strokes: "this shape means something other than minutes". */
    private fun hatch(canvas: Canvas, bar: RectF) {
        val step = bar.width() / 4f
        var x = bar.left + step
        while (x < bar.right) {
            canvas.drawLine(x, bar.bottom, x - step / 2f, bar.top, hatchPaint)
            x += step
        }
    }

    /** The number, in an ink box over the bar it belongs to. */
    private fun drawValueBubble(canvas: Canvas, column: Column, centre: Float, top: Float) {
        val text = column.minutes.toString()
        val width = valuePaint.measureText(text) + dp(12f)
        val height = bubbleBand() - dp(6f)
        val halfWidth = width / 2f
        var bubbleLeft = centre - halfWidth
        var bubbleRight = centre + halfWidth

        // Keep the box on the card: the first and last columns sit at the edges.
        val overflowLeft = (paddingLeft + dp(2f)) - bubbleLeft
        if (overflowLeft > 0f) {
            bubbleLeft += overflowLeft
            bubbleRight += overflowLeft
        }
        val overflowRight = bubbleRight - (width - paddingRight - dp(2f))
        if (overflowRight > 0f) {
            bubbleLeft -= overflowRight
            bubbleRight -= overflowRight
        }

        bubble.set(bubbleLeft, top - height, bubbleRight, top)
        face.color = palette.ink
        canvas.drawRect(bubble, face)
        val metrics = valuePaint.fontMetrics
        canvas.drawText(text, bubble.centerX(), bubble.centerY() - (metrics.ascent + metrics.descent) / 2f, valuePaint)
    }

    private fun bubbleBand(): Float = dp(24f)

    private fun selectAt(x: Float) {
        if (columns.isEmpty()) return
        val left = paddingLeft + dimens.s.toFloat()
        val right = width - paddingRight - dimens.s.toFloat()
        if (x < left || x > right) return
        val slot = (right - left) / columns.size
        val index = ((x - left) / slot).toInt().coerceIn(0, columns.lastIndex)
        selected = if (selected == index) -1 else index
        invalidate()
        onColumnTapped?.invoke(selected)
    }

    /** A TalkBack double tap has no coordinate, so it walks to the next day. */
    override fun performClick(): Boolean {
        if (columns.isNotEmpty()) {
            selected = if (selected + 1 < columns.size) selected + 1 else 0
            invalidate()
            onColumnTapped?.invoke(selected)
        }
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        taps.onTouchEvent(event)
        // Claiming the gesture is what lets a tap arrive at all. The panel's
        // scroll view still intercepts once the finger moves, so dragging keeps
        // scrolling the card rather than being swallowed by the chart.
        return true
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
