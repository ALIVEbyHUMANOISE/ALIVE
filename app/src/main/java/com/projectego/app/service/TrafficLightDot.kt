package com.projectego.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * One LED of the pill's traffic light.
 *
 * The view is a fixed 14dp slot: a 10dp saturated core centred in it, a 2dp
 * colour-matched halo that fades to nothing at the slot edge, and one small
 * specular highlight. The halo never leaves the slot, so it can neither be
 * clipped nor widen the pill, and the core is always drawn at full colour -
 * only [glowFraction] moves while the red light is pulsing.
 */
class TrafficLightDot(context: Context) : View(context) {

    var coreColor: Int = Color.RED
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The reduced-motion cue for the approaching state: a thin light ring
     * inside the core, never a dimmed light.
     */
    var ringed: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** 1 = the full halo; only the red light's halo animates. */
    var glowFraction: Float = 1f
        set(value) {
            field = value.coerceIn(MIN_GLOW, 1f)
            invalidate()
        }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        val slot = minOf(width, height).toFloat()
        if (slot <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val coreRadius = slot * CORE_RATIO / 2f
        val glowRadius = slot / 2f

        glowPaint.shader = RadialGradient(
            cx,
            cy,
            glowRadius,
            intArrayOf(
                ColorUtils.setAlphaComponent(
                    coreColor,
                    (GLOW_ALPHA * glowFraction * 255f).toInt()
                ),
                Color.TRANSPARENT
            ),
            floatArrayOf(coreRadius / glowRadius, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        corePaint.color = coreColor
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        // One specular point, above the centre: an LED, not a bubble. At 8dp
        // this is a couple of pixels; any larger would read as a gradient.
        highlightPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, HIGHLIGHT_ALPHA)
        canvas.drawCircle(
            cx - coreRadius * 0.30f,
            cy - coreRadius * 0.34f,
            coreRadius * 0.22f,
            highlightPaint
        )

        if (ringed) {
            ringPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, RING_ALPHA)
            ringPaint.strokeWidth = resources.displayMetrics.density * RING_STROKE_DP
            canvas.drawCircle(cx, cy, coreRadius - ringPaint.strokeWidth / 2f, ringPaint)
        }
    }

    private companion object {
        /** The core is 10dp of the 14dp slot. */
        const val CORE_RATIO = 10f / 14f
        const val GLOW_ALPHA = 0.42f
        const val MIN_GLOW = 0.30f
        const val HIGHLIGHT_ALPHA = 70
        const val RING_ALPHA = 220
        const val RING_STROKE_DP = 1f
    }
}
