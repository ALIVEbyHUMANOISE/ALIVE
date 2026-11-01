package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * ALIVE's water reminder mark: one drop, optionally with the character's eyes.
 *
 * Drawn rather than shipped as a bitmap so it stays crisp at every size and in
 * both palettes. The compact pill uses the bare drop; the expanded card gives
 * the same drop a face, which is the character in its water mood.
 */
class PillDropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** The drop's ink; the caller passes the chosen palette's colour. */
    var color: Int = Color.WHITE

    /** True for the expanded card's character, false for the compact mark. */
    var withEyes: Boolean = false

    /** 0..1 halo strength behind the bulb. */
    var glow: Float = 0.5f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val path = Path()
    private val bulb = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        runCatching { drawDrop(canvas) }
    }

    private fun drawDrop(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = h * 0.05f
        val cx = w / 2f
        // A drop is a round bulb with a point growing out of its top; the
        // radius follows the narrower side so it never spills out of its slot.
        val r = min(w * 0.42f, h * 0.34f)
        val bulbCy = h - pad - r

        if (glow > 0f) {
            halo.shader = RadialGradient(
                cx, bulbCy, r * 1.9f,
                intArrayOf(alpha(color, (110 * glow).toInt()), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, bulbCy, r * 1.9f, halo)
            halo.shader = null
        }

        path.reset()
        path.moveTo(cx, pad)
        path.cubicTo(cx, pad + h * 0.16f, cx + r, bulbCy - r * 1.15f, cx + r, bulbCy)
        bulb.set(cx - r, bulbCy - r, cx + r, bulbCy + r)
        path.arcTo(bulb, 0f, 180f, false)
        path.cubicTo(cx - r, bulbCy - r * 1.15f, cx, pad + h * 0.16f, cx, pad)
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)

        // One soft highlight, low on the bulb, keeps the drop from reading as
        // a flat blob without adding a second colour.
        highlight.color = alpha(Color.WHITE, 46)
        canvas.drawOval(
            cx - r * 0.52f, bulbCy - r * 0.18f,
            cx - r * 0.16f, bulbCy + r * 0.30f,
            highlight
        )

        if (withEyes) {
            val eyeW = r * 0.30f
            val eyeH = r * 0.44f
            val gap = r * 0.26f
            val eyeCy = bulbCy - r * 0.10f
            canvas.drawOval(cx - gap - eyeW / 2f, eyeCy - eyeH / 2f,
                cx - gap + eyeW / 2f, eyeCy + eyeH / 2f, eyes)
            canvas.drawOval(cx + gap - eyeW / 2f, eyeCy - eyeH / 2f,
                cx + gap + eyeW / 2f, eyeCy + eyeH / 2f, eyes)
        }
    }

    private fun alpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
