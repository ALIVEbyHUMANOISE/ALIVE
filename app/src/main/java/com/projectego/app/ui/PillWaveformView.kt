package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The compact waveform: five rounded bars, a slow ripple, no meaning.
 *
 * It is explicitly **not an audio meter**. NUSA's waveform could not be
 * inspected (the reference tooling refused the third-party binary), so ALIVE
 * does not claim to reproduce its semantics, and it certainly does not ask for
 * the microphone to imitate the look. What this draws is a decorative "the pill
 * is awake" ripple, and it is labeled as such in the report.
 *
 * The view never runs its own clock: the service hands it a [phase] per frame,
 * which keeps one scheduler in the pill instead of two. Allocating nothing per
 * frame is a side effect of keeping only one [RectF] and one [Paint].
 */
class PillWaveformView(context: Context) : View(context) {

    /** 0..1, wrapped by the caller. The bars are a pure function of this. */
    var phase: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()
    private var drawFailureReported = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        // A pill that cannot draw is a blank pill, not a dead guard: this view
        // shares a process with the accessibility service, so a throw here must
        // never reach the default handler.
        runCatching { drawFrame(canvas) }.onFailure { reportDrawFailure(it) }
    }

    private fun drawFrame(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val barWidth = (BAR_WIDTH_DP * density).coerceAtLeast(1f)
        val gap = (BAR_GAP_DP * density).coerceAtLeast(1f)
        val total = barWidth * BARS + gap * (BARS - 1)
        val startX = (width - total) / 2f
        val centerY = height / 2f
        val maxHeight = height * MAX_HEIGHT_RATIO
        val minHeight = barWidth

        barPaint.color = BAR_COLOR
        for (i in 0 until BARS) {
            val wave = abs(sin(2f * PI.toFloat() * phase + i * STAGGER))
            val barHeight = (minHeight + (maxHeight - minHeight) * wave)
                .coerceIn(minHeight, height.toFloat())
            val x = startX + i * (barWidth + gap)
            bar.set(x, centerY - barHeight / 2f, x + barWidth, centerY + barHeight / 2f)
            canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, barPaint)
        }
    }

    /** One line per view instance, so a drawing bug is visible without spam. */
    private fun reportDrawFailure(error: Throwable) {
        if (drawFailureReported) return
        drawFailureReported = true
        Log.w("PillWave", "draw failed", error)
    }

    private companion object {
        const val BARS = 5
        const val BAR_WIDTH_DP = 2f
        const val BAR_GAP_DP = 1.6f
        const val MAX_HEIGHT_RATIO = 0.78f
        const val STAGGER = 0.9f

        /** Paper at 86%, so the bars read as quiet next to the ring. */
        const val BAR_COLOR = 0xDBF5F1E8.toInt()
    }
}
