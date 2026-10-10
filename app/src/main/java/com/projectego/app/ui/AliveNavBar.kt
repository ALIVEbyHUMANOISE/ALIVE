package com.projectego.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.widget.ImageViewCompat
import com.projectego.app.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ALIVE's bottom bar: four keys, and the selected one is a raised circle.
 *
 * The strip is one dock - paper fill, a single bold ink contour - and the
 * selected slot lifts its glyph out of the strip into a circle that floats
 * above it, with the contour opening into a notch around the circle. Each
 * slot owns the colour of its own circle (forest for the day, amber for the
 * board, rose for the numbers, work blue for the ledger of the self), so the
 * bar says where you are in colour and the glyph says what it is.
 *
 * One circle, moving: choosing another key sends the circle across the bar
 * and lets the notch travel with it - a notch that stayed behind would be a
 * hole with nothing in it. The move is the reference's motion, sized down:
 * a short slide with a pop, and nothing at all when the device asks for
 * reduced motion.
 *
 * A plain FrameLayout rather than a Material component: BottomNavigationView
 * cannot grow a notch around its selection, and the notch is the whole point.
 */
class AliveNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private class Slot(
        @StringRes val label: Int,
        @DrawableRes val glyph: Int,
        @ColorInt val face: Int,
        @ColorInt val onFace: Int
    )

    /** Where the bar's four positions are, in tap order. */
    object Index {
        const val HOME = 0
        const val QUEST = 1
        const val STATS = 2
        const val PROFILE = 3

        /** The position that stands for a tab. */
        fun indexForTab(tab: Int): Int = when (tab) {
            0 -> HOME
            1 -> QUEST
            else -> PROFILE
        }
    }

    /** Called on every tap, including a re-tap of the key already chosen. */
    var onSelect: ((Int) -> Unit)? = null

    private val ui = AliveComponents(context)
    private val palette = ui.palette
    private val dimens = ui.dimens

    private val slots = listOf(
        Slot(R.string.nav_home, R.drawable.ic_nav_home, palette.forest, palette.white),
        Slot(R.string.nav_quest, R.drawable.ic_nav_quest, palette.amber, palette.ink),
        Slot(R.string.nav_stats, R.drawable.ic_pixel_chart, palette.pinkSoft, palette.ink),
        Slot(R.string.nav_profile, R.drawable.ic_nav_profile, palette.workDark, palette.white)
    )

    private val barHeightPx = dimens.dp(64)
    private val circleSizePx = dimens.dp(54)

    /**
     * The circle's resting Y, in the bar's own coordinates: half of it rises
     * past the bar's top edge and floats over the page. The bar itself stays
     * in the layout flow, so nothing above it gets cut for a dead zone - the
     * circle overlaps the content instead of the content making room for it.
     */
    private val circleRestY = -(circleSizePx / 2f + dimens.dp(2))

    /** Same centre, in the bar body's coordinates, whose top line is y = 0. */
    private val circleCenterInBodyPx = circleRestY + circleSizePx / 2f

    private val notchRadiusPx = circleSizePx / 2f + dimens.dp(7)

    private val body = NotchBody()
    private val circleGlyph = ImageView(context)
    private val circle = FrameLayout(context)
    private val cells = mutableListOf<LinearLayout>()
    private val cellGlyphs = mutableListOf<ImageView>()
    private val cellLabels = mutableListOf<TextView>()

    private var selected = -1
    private var notchCenterX = 0f
    private var travel: ValueAnimator? = null

    init {
        clipChildren = false
        clipToPadding = false

        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, barHeightPx))

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        slots.forEachIndexed { index, slot ->
            val glyph = ImageView(context).apply {
                setImageResource(slot.glyph)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(dimens.navIcon, dimens.navIcon)
            }
            val label = TextView(context).apply {
                setTextAppearance(R.style.TextAppearance_ALIVE_NavLabel)
                text = context.getString(slot.label)
            }
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                contentDescription = context.getString(slot.label)
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                setPadding(0, dimens.xs, 0, dimens.dp(6))
                addView(glyph)
                addView(
                    label,
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dimens.dp(2) }
                )
                setOnClickListener {
                    if (index != selected) select(index)
                    onSelect?.invoke(index)
                }
            }
            cells += cell
            cellGlyphs += glyph
            cellLabels += label
            row.addView(cell)
        }
        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, barHeightPx)
        )

        circleGlyph.apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LayoutParams(dimens.navIcon, dimens.navIcon, Gravity.CENTER)
        }
        circle.addView(circleGlyph)
        addView(
            circle,
            LayoutParams(circleSizePx, circleSizePx).apply {
                gravity = Gravity.START
            }
        )
    }

    /**
     * Choose a position. The circle arcs across the bar and lands with a
     * bounce - the notch travels with it, because a notch that stayed behind
     * would be a hole with nothing in it. Programmatic calls from a restored
     * panel pass [animate] = false to arrive without theatre. Choosing the
     * position already chosen is a no-op: nothing moves, and an in-flight
     * trip to it is not cancelled.
     */
    fun select(index: Int, animate: Boolean = true) {
        if (index !in slots.indices) return
        if (index == selected) return
        val first = selected == -1
        selected = index
        paintCells()
        paintCircle()

        val target = centerXFor(index)
        travel?.cancel()
        // A bar that has not been measured yet has no where to travel to: every
        // centre on it is zero. Jumping instead of animating is not a shortcut -
        // an animator started here would carry that zero as both its start and
        // its end, and its next frame after the first layout would pull the
        // circle back off the key `onSizeChanged` had just put it on. That is how
        // opening the app from the widget parked the circle at the left edge with
        // the notch somewhere off-screen beside it.
        if (first || !animate || !animationsAllowed() || width == 0) {
            moveTo(target, circleRestY)
            return
        }
        val from = notchCenterX
        travel = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340L
            // Overshoot, not ease: the arrival is the moment, so it should
            // carry a little past the target and settle back.
            interpolator = OvershootInterpolator(1.18f)
            addUpdateListener {
                val t = it.animatedValue as Float
                val x = from + (target - from) * t
                // An arc: the circle lifts off the edge mid-flight, then the
                // overshoot dips it a breath below rest before it settles.
                val y = circleRestY - dimens.dp(12) * sin(PI.toFloat() * t)
                moveTo(x, y)
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // select() may have run before the first layout; the notch and circle
        // are placed by width, so they re-seat themselves here. A trip still in
        // flight is cancelled rather than left running, because it is carrying a
        // centre computed against a width that no longer exists - and its next
        // frame would undo this line.
        travel?.cancel()
        if (selected != -1) moveTo(centerXFor(selected), circleRestY)
    }

    private fun centerXFor(index: Int): Float = width * (index + 0.5f) / slots.size

    private fun moveTo(centerX: Float, centerY: Float) {
        notchCenterX = centerX
        circle.translationX = centerX - circleSizePx / 2f
        circle.translationY = centerY
        body.invalidate()
    }

    private fun paintCells() {
        cellGlyphs.forEachIndexed { index, glyph ->
            val isSelected = index == selected
            // INVISIBLE, never GONE: the chosen glyph lives in the circle, and
            // its seat must keep the four labels on one line.
            glyph.visibility = if (isSelected) View.INVISIBLE else View.VISIBLE
            ImageViewCompat.setImageTintList(glyph, ColorStateList.valueOf(palette.inkSecondary))
            cellLabels[index].setTextColor(
                if (isSelected) palette.ink else palette.inkSecondary
            )
            cells[index].isSelected = isSelected
        }
    }

    private fun paintCircle() {
        val slot = slots[selected]
        circle.background = circleFace(slot.face)
        circleGlyph.setImageResource(slot.glyph)
        ImageViewCompat.setImageTintList(circleGlyph, ColorStateList.valueOf(slot.onFace))
    }

    /** The circle is raised, so it casts like every other raised thing here. */
    private fun circleFace(@ColorInt face: Int): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.ink)
        }
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(face)
            setStroke(dimens.borderButton, palette.ink)
        }
        return LayerDrawable(arrayOf(base, fill)).apply {
            setLayerInset(1, 0, 0, dimens.shadowMinor, dimens.shadowMinor)
        }
    }

    /** The device decides, not us: "remove animated transitions" stills the bar. */
    private fun animationsAllowed(): Boolean = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0f

    /**
     * The strip itself: paper, a bold contour along the top, and the contour
     * dips around the circle's resting place. Drawn in the bar body's own
     * coordinates, whose top line is y = 0 - the notch circle's centre lives
     * just above it, so the arc reads as an opening cut from the edge.
     */
    private inner class NotchBody : View(context) {

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = palette.paper
        }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dimens.borderBold.toFloat()
            color = palette.ink
            strokeJoin = Paint.Join.ROUND
        }
        private val bodyPath = Path()
        private val contourPath = Path()
        private val notchRect = RectF()

        override fun onDraw(canvas: Canvas) {
            if (width == 0) return
            val cx = notchCenterX
            val cy = circleCenterInBodyPx
            val r = notchRadiusPx
            // Where the top line crosses the notch circle, a hair off its
            // equator because the centre sits above the line.
            val above = -cy
            val half = sqrt(r * r - above * above)
            val startDeg = Math.toDegrees(atan2(above.toDouble(), -half.toDouble())).toFloat()
            val sweep = Math.toDegrees(atan2(above.toDouble(), half.toDouble())).toFloat() -
                startDeg
            notchRect.set(cx - r, cy - r, cx + r, cy + r)

            bodyPath.reset()
            bodyPath.moveTo(0f, 0f)
            bodyPath.lineTo(cx - half, 0f)
            bodyPath.arcTo(notchRect, startDeg, sweep, false)
            bodyPath.lineTo(width.toFloat(), 0f)
            bodyPath.lineTo(width.toFloat(), height.toFloat())
            bodyPath.lineTo(0f, height.toFloat())
            bodyPath.close()
            canvas.drawPath(bodyPath, fill)

            contourPath.reset()
            contourPath.moveTo(0f, 0f)
            contourPath.lineTo(cx - half, 0f)
            contourPath.arcTo(notchRect, startDeg, sweep, false)
            contourPath.lineTo(width.toFloat(), 0f)
            canvas.drawPath(contourPath, edge)
        }
    }
}
