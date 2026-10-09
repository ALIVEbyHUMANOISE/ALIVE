package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import com.projectego.app.R
import kotlin.math.abs
import kotlin.math.ceil
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
 *
 * Columns are laid out by [ChartGeometry]: when a month cannot have an honest
 * slot width in one row, it becomes several balanced rows, each with its own
 * baseline. A chart that shrinks to fit 31 days is a chart of 31 hairlines.
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
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = palette.workPale
    }
    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = palette.ink
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.inter_regular)
        // Ten is the floor: below it a label stops being a word and becomes a
        // texture, which is what eight sp made the month's labels.
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        color = palette.inkSecondary
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.silkscreen_bold)
        textSize = sp(10f)
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
    private var emphasisAlpha = FULL_ALPHA

    /**
     * The kind of day the screen is asking about, or null for all of them.
     *
     * Set from the legend. It never removes bars - the month is the same month
     * either way - it dims everything that is not the kind asked for, so the
     * answer stays a shape in the chart instead of a shorter chart.
     */
    var highlightedKind: DayBarKind? = null
        set(value) {
            field = value
            invalidate()
        }

    /** How far a finger may wander and still be called a tap. */
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            selectAt(event.x, event.y)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val grid = geometryFor(measuredWidth)
        setMeasuredDimension(measuredWidth, resolveSize(ceil(desiredHeight(grid)).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val grid = geometry()
        if (columns.isEmpty() || grid.isEmpty) return

        val labelBand = labelBandHeight()
        val left = paddingLeft + dimens.s.toFloat()
        val right = width - paddingRight - dimens.s.toFloat()
        if (right <= left) return

        val singleRow = grid.rowCount == 1
        val headroom = if (singleRow) bubbleBand() else dimens.s.toFloat()
        val top = paddingTop + headroom
        val foot = height - paddingBottom - labelBand
        val pitch = (foot - top - edge.strokeWidth) / grid.rowCount
        val plotHeight = pitch - rowGap()
        if (plotHeight <= 0f) return

        val slot = (right - left) / grid.columnsPerRow
        val barWidth = ChartGeometry.barWidth(slot, dp(30f), dp(3f))
        val labelMargin = ChartGeometry.LABEL_MARGIN_DP * resources.displayMetrics.density

        columns.forEachIndexed { index, column ->
            val row = grid.rowOf(index)
            val centre = left + slot * (index - row * grid.columnsPerRow) + slot / 2f
            val baseline = top + row * pitch + plotHeight
            setEmphasis(highlightedKind != null && column.kind != highlightedKind)

            if (index == selected) {
                // The selection is a band across the whole slot rather than a
                // hairline around the bar: at month width a bar is 4dp and an
                // inset outline on it is invisible, while a band is legible at
                // any width and cannot overflow its own column.
                shape.set(centre - slot / 2f, baseline - plotHeight, centre + slot / 2f, baseline)
                canvas.drawRect(shape, band)
                canvas.drawLine(shape.left, top + row * pitch, shape.left, baseline, edge)
                canvas.drawLine(shape.right, top + row * pitch, shape.right, baseline, edge)
            }
            drawColumn(canvas, column, centre, barWidth, baseline - plotHeight, baseline, plotHeight)

            // Only the last row is named: a label per row would be three bands of
            // type between the bars, and the day each column is stays readable
            // from its order.
            if (row == grid.rowCount - 1 &&
                ChartGeometry.labelFits(labelPaint.measureText(column.label), slot, labelMargin)
            ) {
                labelPaint.color = if (index == selected) palette.ink else palette.inkSecondary
                // Setting a colour resets the alpha, so the emphasis goes back on
                // after it - otherwise a dimmed column keeps a bright label.
                labelPaint.alpha = emphasisAlpha
                canvas.drawText(column.label, centre, baseline + dimens.s - labelPaint.fontMetrics.ascent, labelPaint)
            }
        }

        // The axes and the bubble are not part of any kind, so they never dim.
        setEmphasis(dim = false)

        // One ink rule per row, because each row is its own axis: a bar's height
        // is that day's share of its allowance, measured from the line under it.
        face.color = palette.ink
        for (row in 0 until grid.rowCount) {
            val baseline = top + row * pitch + plotHeight
            canvas.drawRect(left, baseline, right, baseline + edge.strokeWidth, face)
        }

        // The number belongs in the sky above a bar, and a wrapped month has no
        // sky - the day line under the chart says it instead.
        if (singleRow && selected in columns.indices) {
            val row = grid.rowOf(selected)
            drawValueBubble(
                canvas,
                columns[selected],
                left + slot * (selected - row * grid.columnsPerRow) + slot / 2f,
                top
            )
        }
    }

    /**
     * Push every stroke of the column being drawn to the background, or pull it
     * back. The paints are shared, so this is always paired with a call that
     * restores them - the axes and the bubble are not part of any kind.
     */
    private fun setEmphasis(dim: Boolean) {
        emphasisAlpha = if (dim) DIM_ALPHA else FULL_ALPHA
        edge.alpha = emphasisAlpha
        hatchPaint.alpha = emphasisAlpha
        band.alpha = emphasisAlpha
    }

    /** The bar paint, at whatever emphasis the column it is being filled for wants. */
    private fun fillWith(@ColorInt color: Int) {
        face.color = color
        face.alpha = emphasisAlpha
    }

    private fun drawColumn(
        canvas: Canvas,
        column: Column,
        centre: Float,
        barWidth: Float,
        top: Float,
        baseline: Float,
        plotHeight: Float
    ) {
        val half = barWidth / 2f
        val stubHeight = dp(9f)
        // The same corner as a meter step: a bar is a block of this app, not a
        // spreadsheet column.
        val corner = dimens.radiusStep.toFloat()

        when (column.kind) {
            // Never seen: a hollow stub, so the gap in the record is visible
            // instead of being flattened into a zero.
            DayBarKind.MISSING -> {
                shape.set(centre - half, baseline - stubHeight, centre + half, baseline)
                fillWith(palette.paper)
                canvas.drawRoundRect(shape, corner, corner, face)
                canvas.drawRoundRect(shape, corner, corner, edge)
            }

            // No allowance to spend: same stub height, filled and hatched, so it
            // cannot be mistaken for a day that was merely unseen.
            DayBarKind.RECOVERY -> {
                shape.set(centre - half, baseline - stubHeight, centre + half, baseline)
                fillWith(palette.warmGray)
                canvas.drawRoundRect(shape, corner, corner, face)
                hatch(canvas, shape)
                canvas.drawRoundRect(shape, corner, corner, edge)
            }

            // An allowance and nothing spent: a square on the baseline. Drawing a
            // hairline bar here would claim a height the day does not have.
            DayBarKind.ZERO -> {
                val tick = min(barWidth, dp(8f))
                shape.set(centre - tick / 2f, baseline - tick, centre + tick / 2f, baseline)
                fillWith(palette.ink)
                canvas.drawRoundRect(shape, corner, corner, face)
            }

            else -> {
                // A spent day is never invisible: the shortest bar the eye can
                // still find is one pixel of ink edge plus a sliver of fill.
                val height = max(column.fraction * plotHeight, dp(3f))
                shape.set(centre - half, baseline - height, centre + half, baseline)
                val full = column.kind == DayBarKind.FULL
                // Soft forest, not forest: forest means "ALIVE is offering a
                // choice", and a bar of minutes used is not a choice the app made.
                // A day that ran its allowance out goes to the red that already
                // means "this is running out" everywhere else in the app - the
                // number under the chart says the same thing, as it always must.
                fillWith(if (full) palette.shortMid else palette.softForest)
                canvas.drawRoundRect(shape, corner, corner, face)
                if (barWidth >= dp(11f)) {
                    texture(canvas, shape, if (full) dotPaper else dotInk)
                }
                canvas.drawRoundRect(shape, corner, corner, edge)
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
        val corner = dimens.radiusStep.toFloat()
        canvas.drawRoundRect(bubble, corner, corner, face)
        val metrics = valuePaint.fontMetrics
        canvas.drawText(text, bubble.centerX(), bubble.centerY() - (metrics.ascent + metrics.descent) / 2f, valuePaint)
    }

    private fun bubbleBand(): Float = dp(24f)

    private fun rowGap(): Float = dp(10f)

    private fun labelBandHeight(): Float =
        (labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent) + dimens.s

    /** The columns this width asks for - and therefore how tall the view wants to be. */
    private fun geometry(): ChartLayout = geometryFor(width)

    private fun geometryFor(viewWidth: Int): ChartLayout = ChartGeometry.layout(
        viewWidth - paddingLeft - paddingRight - 2f * dimens.s,
        columns.size,
        resources.displayMetrics.density
    )

    /**
     * The height a grid needs, asked for rather than imposed.
     *
     * A month used to be handed 150dp for 31 columns and expected to fit; the
     * result was bars thinner than their own outline. Here the chart says how
     * many rows its columns need and how tall those rows are, and the card gives
     * it that.
     */
    private fun desiredHeight(grid: ChartLayout): Float {
        if (grid.isEmpty) return dimens.xl.toFloat()
        val headroom = if (grid.rowCount == 1) bubbleBand() else dimens.s.toFloat()
        val plot = dp(if (grid.rowCount == 1) SINGLE_ROW_PLOT_DP else WRAPPED_ROW_PLOT_DP)
        return paddingTop + headroom +
            grid.rowCount * (plot + rowGap()) - rowGap() +
            edge.strokeWidth + labelBandHeight() + paddingBottom
    }

    private fun selectAt(x: Float, y: Float) {
        if (columns.isEmpty()) return
        val grid = geometry()
        if (grid.isEmpty) return
        val left = paddingLeft + dimens.s.toFloat()
        val right = width - paddingRight - dimens.s.toFloat()
        if (x < left || x > right) return

        val singleRow = grid.rowCount == 1
        val top = paddingTop + (if (singleRow) bubbleBand() else dimens.s.toFloat())
        val foot = height - paddingBottom - labelBandHeight()
        val pitch = (foot - top - edge.strokeWidth) / grid.rowCount
        if (pitch <= 0f) return

        val slot = (right - left) / grid.columnsPerRow
        val row = ((y - top) / pitch).toInt().coerceIn(0, grid.rowCount - 1)
        val column = ((x - left) / slot).toInt().coerceIn(0, grid.columnsPerRow - 1)
        val index = grid.indexAt(row, column)
        if (index < 0) return

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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                // A thumb tap is never perfectly still, and the panel's ScrollView
                // claims the gesture the moment it drifts past the touch slop -
                // which is why tapping a bar used to answer with nothing at all.
                // Ask for the gesture up front, and hand it straight back as soon
                // as this really is a drag, so scrolling keeps working.
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE ->
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }

            else -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        taps.onTouchEvent(event)
        return true
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        /** One row gets a whole chart's plot height: there is nothing above it. */
        const val SINGLE_ROW_PLOT_DP = 140f

        /** A wrapped month is a grid, and a grid row is a step, not a stage. */
        const val WRAPPED_ROW_PLOT_DP = 48f

        /** A column that is not the kind being asked about. Still a bar, just quiet. */
        const val DIM_ALPHA = 76
        const val FULL_ALPHA = 255
    }
}
