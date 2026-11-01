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
 * The promise page's mark: a crescent with one small star.
 *
 * Same drawing rules as [PillDropView] - one colour, one soft halo, crisp at
 * any size - so the two reminder pages read as a set rather than as two
 * borrowed icons.
 */
class PillMoonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var color: Int = Color.WHITE
    var glow: Float = 0.45f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = Color.WHITE
    }
    private val crescent = Path()
    private val outer = Path()
    private val inner = Path()
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        runCatching { drawMoon(canvas) }
    }

    private fun drawMoon(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val side = min(w, h)
        val cx = w * 0.46f
        val cy = h * 0.52f
        val r = side * 0.36f

        if (glow > 0f) {
            halo.shader = RadialGradient(
                cx, cy, r * 2.1f,
                intArrayOf(alpha(color, (100 * glow).toInt()), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r * 2.1f, halo)
            halo.shader = null
        }

        outer.reset()
        oval.set(cx - r, cy - r, cx + r, cy + r)
        outer.addOval(oval, Path.Direction.CW)
        inner.reset()
        oval.set(cx - r * 0.98f + r * 0.62f, cy - r * 1.02f, cx + r * 1.02f + r * 0.62f, cy + r * 0.98f)
        inner.addOval(oval, Path.Direction.CW)
        crescent.reset()
        crescent.op(outer, inner, Path.Op.DIFFERENCE)
        fill.color = color
        canvas.drawPath(crescent, fill)

        // The one star: a small dot high on the open side.
        canvas.drawCircle(cx + r * 0.92f, cy - r * 0.62f, r * 0.14f, star)
    }

    private fun alpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
