package com.projectego.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import android.view.View
import androidx.core.graphics.ColorUtils
import com.projectego.app.domain.PillTheme
import com.projectego.app.domain.RingSpec

/**
 * The pill's one status ring.
 *
 * It replaces the three traffic lights and carries exactly the same meaning:
 * one thin circle, a controlled halo, and a hue that comes from
 * [com.projectego.app.domain.PillStatusRingPolicy]. Nothing here decides
 * status - the caller hands over a [RingSpec] and this view only draws it.
 *
 * A colour change is animated rather than snapped, because the day crossing
 * from amber to red is the one moment the pill is worth looking at. The glow is
 * a cached gradient whose *alpha* moves with the pulse, so a breathing ring
 * allocates nothing per frame.
 */
class PillStatusRingView(context: Context) : View(context) {

    var spec: RingSpec = RingSpec(
        color = PillTheme.RING_UNKNOWN_COLOR,
        glow = 0f,
        pulsing = false,
        ringed = false
    )
        set(value) {
            val previous = field
            field = value
            if (previous.color != value.color) startColorFade(previous.color, value.color)
            invalidate()
        }

    /** 1 = the ring's own halo strength; the pulse drives this between 0.3 and 1. */
    var glowFraction: Float = 1f
        set(value) {
            val next = value.coerceIn(MIN_GLOW, 1f)
            if (next == field) return
            field = next
            invalidate()
        }

    /**
     * The share of the day's allowance already spent, 0..1, drawn from twelve
     * o'clock. 1 is a full ring, which is also the honest answer when no report
     * is readable - the ring must not claim an empty day.
     */
    var progress: Float = 1f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (next == field) return
            field = next
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** The colour actually on screen, which lags [spec] while a fade runs. */
    private var shownColor: Int = PillTheme.RING_UNKNOWN_COLOR
    private var fade: ValueAnimator? = null

    private var glowShader: RadialGradient? = null
    private var shaderSize = 0
    private var shaderColor = 0
    private val arc = RectF()
    private var drawFailureReported = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun startColorFade(from: Int, to: Int) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PillTheme.RING_COLOR_FADE_MILLIS
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                shownColor = blend(from, to, t)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // A pill that cannot draw is a blank pill, not a dead guard: this view
        // shares a process with the accessibility service, so a throw here must
        // never reach the default handler.
        runCatching { drawFrame(canvas) }.onFailure { reportDrawFailure(it) }
    }

    private fun drawFrame(canvas: Canvas) {
        val slot = minOf(width, height).toFloat()
        if (slot <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density
        val stroke = (RING_STROKE_DP * density).coerceAtMost(slot / 4f)
        val radius = slot / 2f - stroke / 2f

        // Halo: past the ring, fading out. It is what makes a 2dp circle read as
        // lit rather than as a drawn outline.
        if (spec.glow > 0f) {
            glowPaint.shader = glowFor(cx, cy, slot / 2f, radius, shownColor)
            glowPaint.alpha = (HALO_ALPHA * spec.glow * glowFraction * 255f)
                .toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, slot / 2f, glowPaint)
        }

        ringPaint.strokeWidth = stroke
        ringPaint.strokeCap = Paint.Cap.ROUND

        // The track: the whole circle, faint. Without it a partly-spent day
        // would float as a lone arc with nothing to measure it against.
        ringPaint.color = ColorUtils.setAlphaComponent(shownColor, TRACK_ALPHA)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // The fill: how much of the allowance is gone, from twelve o'clock.
        if (progress > 0f) {
            ringPaint.color = shownColor
            arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arc, -90f, 360f * progress, false, ringPaint)
        }

        // Reduced motion loses the pulse but must not lose the state: approaching
        // keeps a static ring inside the circle.
        if (spec.ringed) {
            innerPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, INNER_RING_ALPHA)
            innerPaint.strokeWidth = (1f * density).coerceAtLeast(1f)
            canvas.drawCircle(cx, cy, radius - stroke, innerPaint)
        }
    }

    /**
     * The halo is a function of the colour and the slot. While the pulse runs
     * only the paint's alpha moves, so no frame allocates a shader.
     */
    private fun glowFor(
        cx: Float,
        cy: Float,
        outerRadius: Float,
        ringRadius: Float,
        color: Int
    ): RadialGradient {
        val cached = glowShader
        if (cached != null && shaderSize == width && shaderColor == color) return cached
        val gradient = RadialGradient(
            cx,
            cy,
            outerRadius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                color,
                Color.TRANSPARENT
            ),
            floatArrayOf(
                0f,
                // Everything inside the ring stays black: a filled disc would
                // turn the status ring into a button.
                (ringRadius / outerRadius * 0.86f).coerceIn(0f, 1f),
                (ringRadius / outerRadius).coerceIn(0f, 1f),
                1f
            ),
            Shader.TileMode.CLAMP
        )
        glowShader = gradient
        shaderSize = width
        shaderColor = color
        return gradient
    }

    override fun onDetachedFromWindow() {
        fade?.cancel()
        fade = null
        super.onDetachedFromWindow()
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped).toInt()
        return Color.argb(a, r, g, b)
    }

    /** One line per view instance, so a drawing bug is visible without spam. */
    private fun reportDrawFailure(error: Throwable) {
        if (drawFailureReported) return
        drawFailureReported = true
        Log.w("PillRing", "draw failed", error)
    }

    private companion object {
        const val MIN_GLOW = 0.30f
        const val RING_STROKE_DP = 3f
        const val HALO_ALPHA = 0.55f
        const val INNER_RING_ALPHA = 210
        /** The unfilled part of the track. Faint, never absent. */
        const val TRACK_ALPHA = 64
    }
}
