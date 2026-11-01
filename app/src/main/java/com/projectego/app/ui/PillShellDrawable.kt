package com.projectego.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.Log
import com.projectego.app.domain.PillTheme

/**
 * The island's own surface: black, with the character's blue bloom behind it.
 *
 * The reference island is not a black pill with glowing eyes - it is a pill that
 * lights up blue *behind* the eyes, strongest along the bottom edge and fading
 * to black at the top. That is a property of the shell, not of the eye layer,
 * so it lives here: one rounded black fill, and a gradient clipped to the same
 * rounded outline.
 *
 * [cornerRadius] is written by the morph on every frame, exactly like the
 * GradientDrawable it replaced, so the shape keeps moving as one object.
 */
class PillShellDrawable : Drawable() {

    /** The shape's corner, in pixels. Driven by the morph. */
    var cornerRadius: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    /** 0 = plain black shell, 1 = the bloom at full strength. */
    var aura: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (next == field) return
            field = next
            invalidateSelf()
        }

    /** The bloom's colour. Blue by default; the person may choose the pink one. */
    var auraColor: Int = PillTheme.EYE_HALO_COLOR
        set(value) {
            if (field == value) return
            field = value
            shader = null
            invalidateSelf()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()
    private val edgeRect = RectF()

    private var shader: LinearGradient? = null
    private var shaderHeight = 0
    private var shaderColor = 0
    private var drawFailureReported = false

    override fun draw(canvas: Canvas) {
        // The shell shares a process with the accessibility service: a bad frame
        // may leave the island blank, but it must never take the guard with it.
        runCatching { drawShell(canvas) }.onFailure { reportDrawFailure(it) }
    }

    private fun drawShell(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val radius = cornerRadius.coerceAtMost(minOf(b.width(), b.height()) / 2f)
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())

        canvas.drawRoundRect(rect, radius, radius, fill)

        // A hairline edge: black on a bright screen has no definition, and this
        // is what separates a surface from a hole. Drawn half a pixel inside so
        // the stroke is not clipped by the drawable's own bounds.
        edgePaint.color = Color.argb(EDGE_ALPHA, 255, 255, 255)
        edgeRect.set(rect.left + 0.5f, rect.top + 0.5f, rect.right - 0.5f, rect.bottom - 0.5f)
        canvas.drawRoundRect(edgeRect, radius, radius, edgePaint)
        if (aura <= 0f) return

        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        auraPaint.shader = gradient(b.height())
        auraPaint.alpha = (aura * AURA_ALPHA * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(rect, auraPaint)
        canvas.restoreToCount(save)
    }

    /**
     * Black at the top, saturated along the bottom edge: the light reads as
     * coming from under the pill, which is what makes the white eyes sit on it
     * instead of floating over it.
     */
    private fun gradient(height: Int): LinearGradient {
        val cached = shader
        if (cached != null && shaderHeight == height && shaderColor == auraColor) return cached
        val built = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                withAlpha(auraColor, 40),
                withAlpha(auraColor, 150),
                auraColor
            ),
            floatArrayOf(0f, 0.45f, 0.80f, 1f),
            Shader.TileMode.CLAMP
        )
        shader = built
        shaderHeight = height
        shaderColor = auraColor
        return built
    }

    /** One line per drawable instance, so a drawing bug is visible without spam. */
    private fun reportDrawFailure(error: Throwable) {
        if (drawFailureReported) return
        drawFailureReported = true
        Log.w("PillShell", "draw failed", error)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** The bloom never reaches full opacity; the shell must stay readable. */
        const val AURA_ALPHA = 0.85f

        /** The edge's heartbeat: visible, never a drawn border. */
        const val EDGE_ALPHA = 22
    }
}
