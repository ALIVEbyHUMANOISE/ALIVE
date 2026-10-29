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
 * The view is a fixed 18dp slot holding a 10dp saturated core: the remaining
 * 4dp is the halo's room, so the light reads as lit rather than as a matte dot.
 * The core is always drawn at full colour - only [glowFraction] moves while the
 * red light is pulsing - and the halo is one cached gradient, alpha-modulated
 * per frame, so a pulse allocates nothing.
 */
class TrafficLightDot(context: Context) : View(context) {

    var coreColor: Int = Color.RED
        set(value) {
            if (field == value) return
            field = value
            shader = null
            invalidate()
        }

    /**
     * The reduced-motion cue for the approaching state: a thin light ring
     * inside the core, never a dimmed light.
     */
    var ringed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** 1 = the full halo; only the red light's halo animates. */
    var glowFraction: Float = 1f
        set(value) {
            val next = value.coerceIn(MIN_GLOW, 1f)
            if (next == field) return
            field = next
            invalidate()
        }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var shader: RadialGradient? = null
    private var shaderWidth = 0
    private var shaderHeight = 0
    private var shaderColour = 0

    override fun onDraw(canvas: Canvas) {
        val slot = minOf(width, height).toFloat()
        if (slot <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val coreRadius = slot * CORE_RATIO / 2f
        val glowRadius = slot / 2f
        val density = resources.displayMetrics.density

        glowPaint.shader = glowFor(cx, cy, glowRadius, coreRadius)
        glowPaint.alpha = (GLOW_ALPHA * glowFraction * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // A thin bright rim at the core's edge: the halo alone reads as a
        // stain, the rim is what makes the source of the light visible.
        rimPaint.color = ColorUtils.setAlphaComponent(coreColor, RIM_ALPHA)
        rimPaint.strokeWidth = density * RIM_STROKE_DP
        canvas.drawCircle(cx, cy, coreRadius, rimPaint)

        corePaint.color = coreColor
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        // One specular point, above the centre: an LED, not a bubble.
        highlightPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, HIGHLIGHT_ALPHA)
        canvas.drawCircle(
            cx - coreRadius * 0.30f,
            cy - coreRadius * 0.34f,
            coreRadius * 0.22f,
            highlightPaint
        )

        if (ringed) {
            ringPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, RING_ALPHA)
            ringPaint.strokeWidth = density * RING_STROKE_DP
            canvas.drawCircle(cx, cy, coreRadius - ringPaint.strokeWidth / 2f, ringPaint)
        }
    }

    /**
     * The halo is a function of the colour and the slot, not of the frame: the
     * pulse moves [glowPaint] alpha instead, which keeps `onDraw` free of
     * allocations while it runs at display rate.
     */
    private fun glowFor(cx: Float, cy: Float, glowRadius: Float, coreRadius: Float): RadialGradient {
        val cached = shader
        if (cached != null &&
            shaderWidth == width && shaderHeight == height && shaderColour == coreColor
        ) {
            return cached
        }
        val gradient = RadialGradient(
            cx,
            cy,
            glowRadius,
            intArrayOf(coreColor, Color.TRANSPARENT),
            // The colour holds past the core's edge before it starts to fade,
            // so the lit ring is 3-4dp of the slot rather than a hairline.
            floatArrayOf((coreRadius / glowRadius).coerceIn(0f, 1f), 1f),
            Shader.TileMode.CLAMP
        )
        shader = gradient
        shaderWidth = width
        shaderHeight = height
        shaderColour = coreColor
        return gradient
    }

    private companion object {
        /** The core is 10dp of the 18dp slot; the rest is halo. */
        const val CORE_RATIO = 10f / 18f
        const val GLOW_ALPHA = 0.66f
        const val MIN_GLOW = 0.30f
        const val RIM_ALPHA = 150
        const val RIM_STROKE_DP = 1.2f
        const val HIGHLIGHT_ALPHA = 70
        const val RING_ALPHA = 220
        const val RING_STROKE_DP = 1f
    }
}
