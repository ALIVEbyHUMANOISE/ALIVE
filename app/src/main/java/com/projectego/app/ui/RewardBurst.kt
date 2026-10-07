package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import kotlin.math.cos
import kotlin.math.sin

/**
 * A short burst of pixel squares, thrown once when a quest actually pays out.
 *
 * It is the only movement ALIVE adds on its own initiative, and it is bounded in
 * four ways: one per attempt rather than one per render (the caller keeps the
 * attempt id it already celebrated), under a second long, silent, and gone
 * entirely when the device is set to remove animations.
 *
 * It is drawn through a [android.view.ViewGroupOverlay], which is what lets it
 * say "I never took a tap from you": overlay children are painted over everything
 * their host covers but join neither its layout nor its touch dispatch, so a
 * celebration cannot swallow the back button press happening under it.
 */
class RewardBurst(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())

    private class Grain(val angle: Double, val speed: Float, val size: Float, val colorIndex: Int)

    private var grains: List<Grain> = emptyList()
    private var originX = 0f
    private var originY = 0f
    private var elapsed = 0L
    private var lastFrame = 0L
    private var playing = false
    private var removeFrom: (() -> Unit)? = null

    /** Colours the burst throws: reward, identity, paper, ink. No new hue. */
    var paletteColors: IntArray = intArrayOf(0, 0, 0, 0)

    private val ticker = object : Runnable {
        override fun run() {
            if (!playing) return
            val now = System.currentTimeMillis()
            elapsed += now - lastFrame
            lastFrame = now
            if (elapsed >= DURATION_MILLIS) {
                stop()
                return
            }
            invalidate()
            handler.postDelayed(this, FRAME_MILLIS)
        }
    }

    /** Throws from ([x], [y]) in [host]'s coordinate space and lands in a second. */
    fun show(host: ViewGroup, x: Float, y: Float) {
        if (!animationsOn()) return
        if (host.width == 0 || host.height == 0) return
        measure(
            MeasureSpec.makeMeasureSpec(host.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(host.height, MeasureSpec.EXACTLY)
        )
        // An overlay child is never laid out by anyone, so its position has to be
        // set here or every square lands one card-width off where it was thrown.
        layout(0, 0, host.width, host.height)
        host.overlay.add(this)
        removeFrom = { host.overlay.remove(this) }
        // The host performs the haptic: an overlay child has no window attachment
        // of its own to route one through.
        host.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        start(x, y)
    }

    private fun start(x: Float, y: Float) {
        originX = x
        originY = y
        elapsed = 0L
        lastFrame = System.currentTimeMillis()
        grains = (0 until GRAINS).map { index ->
            Grain(
                angle = Math.toRadians((360.0 / GRAINS) * index + SPREAD_DEGREES),
                speed = (MIN_SPEED + (index % 4) * SPEED_STEP),
                size = if (index % 3 == 0) LARGE_SIZE else SMALL_SIZE,
                colorIndex = index % 4
            )
        }
        playing = true
        invalidate()
        handler.postDelayed(ticker, FRAME_MILLIS)
    }

    private fun animationsOn(): Boolean = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0f

    private fun stop() {
        playing = false
        handler.removeCallbacks(ticker)
        removeFrom?.invoke()
        removeFrom = null
    }

    override fun onDetachedFromWindow() {
        playing = false
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!playing) return
        val progress = elapsed.toFloat() / DURATION_MILLIS

        grains.forEach { grain ->
            // Out fast, then a short fall: the shape of a thrown handful, not an
            // orbit. Everything squares, because this app's pixels are squares.
            val travelled = grain.speed * progress
            val fall = GRAVITY * progress * progress
            val x = originX + (cos(grain.angle) * travelled).toFloat()
            val y = originY + (sin(grain.angle) * travelled + fall).toFloat()
            paint.color = paletteColors.getOrElse(grain.colorIndex) { paletteColors[0] }
            paint.alpha = (255 * (1f - progress * 0.85f)).toInt().coerceIn(0, 255)
            canvas.drawRect(x, y, x + grain.size, y + grain.size, paint)
        }
    }

    private companion object {
        const val GRAINS = 14
        const val DURATION_MILLIS = 780L
        const val FRAME_MILLIS = 16L
        const val SPREAD_DEGREES = 14.0
        const val MIN_SPEED = 90f
        const val SPEED_STEP = 34f
        const val GRAVITY = 120f
        const val SMALL_SIZE = 9f
        const val LARGE_SIZE = 14f
    }
}
