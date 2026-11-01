package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.view.View
import androidx.core.graphics.ColorUtils
import com.projectego.app.domain.PillCharacterCycle.EyeExpression
import com.projectego.app.domain.PillTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * The character's eyes: two white ovals on a blue bloom, drawn - not a sprite.
 *
 * The shipped ALIVE faces are 16-cell pixel art, and the redesign forbids
 * scaling that artwork. So the compact eyes are their own rendered layer: the
 * ovals are plain white, and the blue bloom is what keeps them from reading as
 * a sticker on the black shell. That is the NUSA treatment, applied without
 * copying its pill geometry.
 *
 * Everything a frame needs is allocated once: the bloom is one cached radial
 * shader, and the sleeping expression is a cached [Path]. [reveal] and
 * [expression] are the only per-frame inputs.
 */
class PillEyesView(context: Context) : View(context) {

    var expression: EyeExpression = EyeExpression.OPEN
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** 0 = invisible, 1 = fully revealed. The cycle drives this. */
    var reveal: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (next == field) return
            field = next
            invalidate()
        }

    var eyeColor: Int = PillTheme.EYE_COLOR
        set(value) {
            if (field == value) return
            field = value
            eyePaint.color = value
            sleepPaint.color = value
            invalidate()
        }

    var haloColor: Int = PillTheme.EYE_HALO_COLOR
        set(value) {
            if (field == value) return
            field = value
            haloShader = null
            invalidate()
        }

    var glow: Float = PillTheme.EYE_GLOW
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (next == field) return
            field = next
            invalidate()
        }

    /** 1 = the round oval; ~0.68 = narrowed, so the eyes can change shape. */
    var heightScale: Float = 1f
        set(value) {
            val next = value.coerceIn(0.4f, 1.2f)
            if (next == field) return
            field = next
            invalidate()
        }

    /** -1 looking left, 0 at the viewer, +1 looking right. */
    var gaze: Float = 0f
        set(value) {
            val next = value.coerceIn(-1f, 1f)
            if (next == field) return
            field = next
            invalidate()
        }

    /** 0..1 through the sleeping window; drives the Zzz. */
    var sleepPhase: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (next == field) return
            field = next
            invalidate()
        }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PillTheme.EYE_COLOR }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sleepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = PillTheme.EYE_COLOR
    }
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = PillTheme.EYE_COLOR
    }

    private val eye = RectF()
    private val sleepPath = Path()
    private val sleepArc = RectF()

    private var haloShader: RadialGradient? = null
    private var haloWidth = 0
    private var drawFailureReported = false

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        // A pill that cannot draw is a blank pill, not a dead guard: this view
        // shares a process with the accessibility service, so a throw here must
        // never reach the default handler.
        runCatching { drawFrame(canvas) }.onFailure { reportDrawFailure(it) }
    }

    private fun drawFrame(canvas: Canvas) {
        if (reveal <= 0f || width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        // The eyes are sized from the height, not the width: the view is wider
        // than the face so the Zzz has room to the side, and that extra width
        // must not inflate the eyes.
        val eyeWidth = height * EYE_WIDTH_TO_HEIGHT
        val gap = eyeWidth * EYE_GAP_TO_WIDTH
        val centerY = height / 2f
        val openHeight = height * OPEN_HEIGHT_RATIO * heightScale
        val blinkHeight = openHeight * BLINK_SQUASH
        val tall = if (expression == EyeExpression.BLINK) blinkHeight else openHeight
        val radius = eyeWidth / 2f

        // Both eyes look the same way, as one face does.
        val shift = gaze * eyeWidth * GAZE_TRAVEL
        val leftX = (width - (eyeWidth * 2f + gap)) / 2f + shift

        // The bloom: one soft blue circle behind each eye, small enough to stay
        // inside the slot. Two wider circles merge across the whole 18dp and
        // clip into a visible square against pure black.
        if (glow > 0f) {
            val haloRadius = eyeWidth * HALO_RADIUS_RATIO
            glowPaint.shader = haloFor(haloRadius)
            glowPaint.alpha = (GLOW_ALPHA * reveal * glow * 255f).toInt().coerceIn(0, 255)
            for (i in 0 until 2) {
                val cx = leftX + i * (eyeWidth + gap) + eyeWidth / 2f
                canvas.save()
                canvas.translate(cx, centerY)
                canvas.drawCircle(0f, 0f, haloRadius, glowPaint)
                canvas.restore()
            }
        }

        if (expression == EyeExpression.SLEEP) {
            drawSleeping(canvas, leftX, gap, eyeWidth, centerY, density)
            drawZs(canvas, density)
            return
        }

        eyePaint.alpha = (reveal * 255f).toInt().coerceIn(0, 255)
        drawPair(canvas, leftX, gap, eyeWidth, tall, centerY, radius)
    }

    private fun drawPair(
        canvas: Canvas,
        leftX: Float,
        gap: Float,
        eyeWidth: Float,
        tall: Float,
        centerY: Float,
        radius: Float
    ) {
        for (i in 0 until 2) {
            val x = leftX + i * (eyeWidth + gap)
            eye.set(x, centerY - tall / 2f, x + eyeWidth, centerY + tall / 2f)
            canvas.drawRoundRect(eye, radius, radius, eyePaint)
        }
    }

    /**
     * Three small z's rising from the sleeping eyes, each on its own third of
     * the phase so there is always one in the air. Six dp of travel: the whole
     * pill is 38dp tall, and the Zzz must read as a detail, not as a new panel.
     */
    private fun drawZs(canvas: Canvas, density: Float) {
        for (i in 0 until 3) {
            val progress = ((sleepPhase * Z_CYCLES + i / 3f) % 1f + 1f) % 1f
            val fading = sin(progress * PI).toFloat().coerceIn(0f, 1f)
            val alpha = fading * reveal * Z_ALPHA
            if (alpha <= 0.03f) continue
            zPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            zPaint.textSize = (Z_MIN_DP + progress * Z_GROW_DP) * density
            // To the right of the face, never straight above an eye: the Zzz
            // drifts up and a little further right so it clears the eyes
            // entirely on every frame of its cycle.
            val x = width * 0.86f + progress * 2.2f * density
            val y = height * 0.56f - progress * Z_RISE_DP * density
            canvas.drawText("z", x, y, zPaint)
        }
    }

    /** Two closed lids: a soft downward curve, which is the whole sleep pose. */
    private fun drawSleeping(
        canvas: Canvas,
        leftX: Float,
        gap: Float,
        eyeWidth: Float,
        centerY: Float,
        density: Float
    ) {
        sleepPaint.alpha = (reveal * 255f).toInt().coerceIn(0, 255)
        sleepPaint.strokeWidth = (SLEEP_STROKE_DP * density).coerceAtLeast(1f)
        for (i in 0 until 2) {
            val x = leftX + i * (eyeWidth + gap)
            sleepArc.set(x, centerY - eyeWidth * 0.42f, x + eyeWidth, centerY + eyeWidth * 0.42f)
            sleepPath.reset()
            // 0 degrees is 3 o'clock; 0..180 sweeps the lower half, which is a
            // closed lid rather than a smile.
            sleepPath.addArc(sleepArc, 0f, 180f)
            canvas.drawPath(sleepPath, sleepPaint)
        }
    }

    /**
     * The bloom is a function of the size and the colour. While the eyes fade
     * only the paint's alpha moves, so no frame allocates a shader.
     */
    private fun haloFor(radius: Float): RadialGradient {
        val cached = haloShader
        if (cached != null && haloWidth == width) return cached
        val built = RadialGradient(
            0f,
            0f,
            radius.coerceAtLeast(1f),
            intArrayOf(haloColor, ColorUtils.setAlphaComponent(haloColor, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        haloShader = built
        haloWidth = width
        return built
    }

    /** One line per view instance, so a drawing bug is visible without spam. */
    private fun reportDrawFailure(error: Throwable) {
        if (drawFailureReported) return
        drawFailureReported = true
        Log.w("PillEyes", "draw failed", error)
    }

    private companion object {
        const val EYE_WIDTH_TO_HEIGHT = 0.5f
        const val EYE_GAP_TO_WIDTH = 0.72f
        const val OPEN_HEIGHT_RATIO = 0.52f
        const val BLINK_SQUASH = 0.16f
        const val GLOW_ALPHA = 0.50f
        const val HALO_RADIUS_RATIO = 0.95f
        const val SLEEP_STROKE_DP = 1.6f

        /** How far a full look travels, as a fraction of one eye's width. */
        const val GAZE_TRAVEL = 0.5f

        /** Zzz: three cycles across the sleep window, rising six dp. */
        const val Z_CYCLES = 2.2f
        const val Z_ALPHA = 0.85f
        const val Z_MIN_DP = 5.0f
        const val Z_GROW_DP = 3.0f
        const val Z_RISE_DP = 6f
    }
}
