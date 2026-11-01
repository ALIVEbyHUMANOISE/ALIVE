package com.projectego.app.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sign

/**
 * The morph's clock.
 *
 * One number describes how far the pill is between its two states: `m = 0` is
 * the compact pill, `m = 1` is the expanded panel. The service turns `m` back
 * into a per-run fraction and renders the window size, the corner radius and
 * every piece of content from that one fraction, so the shell and its contents
 * can never disagree.
 *
 * Two ways of travelling that axis:
 *
 *  - [easeRun] is a transition that starts from a rest state. It runs the
 *    pill's own Decelerate curve for the duration the service chose (260 ms
 *    out, 210 ms back) and produces exactly the fractions the pill has always
 *    rendered.
 *  - [springRun] takes over a morph that was interrupted. It starts from the
 *    position the interrupted run actually reached *and the speed it had there*,
 *    which is the whole point: a restart from fraction zero was positionally
 *    correct but began at the easing curve's maximum slope, so every reversal
 *    kicked. A critically damped spring carries the speed through the seam,
 *    needs no fixed duration, and cannot oscillate.
 *
 * Velocity is in **morph units per second** (a full morph is 1.0 unit, so
 * 40 units/s is the speed that would cross the whole axis in 25 ms). Nothing
 * here knows about pixels, Android, or clocks: the service hands each frame's
 * elapsed time in milliseconds and gets the fraction to render.
 *
 * Overshoot is not a side effect left to chance, it is bounded twice at the
 * moment of capture, in [boundContinuation]: a critically damped spring crosses
 * its target only if its speed exceeds rate x distance, so that alone makes
 * crossing impossible; and momentum that points *away* from the new target is
 * further limited to a fraction of the remaining distance, which is what keeps
 * a reversal reading as immediate instead of as a bounce.
 */
class PillMorphEngine private constructor(
    /** An ease run's duration; for a continuation, the hard cap on how long it may run. */
    val durationMillis: Long,
    private val startM: Float,
    private val targetM: Float,
    initialVelocity: Float,
    private val isSpring: Boolean,
    dampingRatio: Float = 1f,
    springRate: Float = SPRING_RATE_PER_SECOND,
    maxFraction: Float = 1f
) {
    private val damping: Float = dampingRatio
    private val rate: Float = springRate
    private val ceiling: Float = maxFraction

    private var m = startM
    private var velocityPerSecond = initialVelocity
    private var lastElapsedMillis = 0L

    /** True once the run has reached its target; [tick] then answers 1f. */
    var isSettled = false
        private set

    /** Where the morph is on its axis, 0 = compact and 1 = expanded. */
    val morph: Float get() = m

    /** The current speed in morph units per second, sign included. */
    val velocity: Float get() = velocityPerSecond

    /**
     * Advance the clock to [elapsedMillis] since this run started and return the
     * fraction the service should render: 0 at the run's own start, 1 at its
     * target, and never outside that segment.
     *
     * Frames are not assumed to be even: the difference between the elapsed time
     * handed over and the last one seen is integrated in sub-steps, so a slow
     * frame advances the physics by exactly that much time and no more.
     *
     * The physics keeps its momentum - [morph] may still travel past the seam
     * while a reversal brakes - but the rendered answer is clamped to the run's
     * own endpoints, because the endpoints here are a window's size: a fraction
     * below the compact pill means a height below zero, and a window with no
     * height is an invisible pill that no further tap can reach. Braking reads
     * as one or two frames of hold, which is what momentum looks like when the
     * object is already at its smallest.
     */
    fun tick(elapsedMillis: Long): Float {
        if (isSettled) return 1f
        val frameMillis = (elapsedMillis - lastElapsedMillis).coerceAtLeast(0L)
        if (elapsedMillis > lastElapsedMillis) lastElapsedMillis = elapsedMillis
        if (isSpring) integrate(frameMillis) else advanceEase(elapsedMillis)
        val span = targetM - startM
        if (span == 0f) {
            settleAtTarget()
            return 1f
        }
        return if (isSettled) 1f else ((m - startM) / span).coerceIn(0f, ceiling)
    }

    private fun advanceEase(elapsedMillis: Long) {
        val progress = if (durationMillis <= 0L) {
            1f
        } else {
            (elapsedMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
        }
        if (progress >= 1f) {
            settleAtTarget()
            return
        }
        val span = targetM - startM
        m = startM + span * decelerate(progress)
        // d/dx of 1-(1-x)^k is k(1-x)^(k-1); per second it is that over the
        // duration, so a reversal can inherit the speed the curve really had.
        velocityPerSecond = span * easeSlope(progress) * 1000f / durationMillis.toFloat()
    }

    private fun integrate(frameMillis: Long) {
        val elapsed = frameMillis.coerceAtMost(MAX_FRAME_MILLIS)
        if (elapsed <= 0L) return
        val targetStepMillis = SUBSTEP_STABILITY / rate * 1000f
        val steps = ceil(elapsed.toFloat() / targetStepMillis)
            .coerceAtMost(MAX_SUBSTEPS.toFloat())
            .toInt()
            .coerceAtLeast(1)
        val h = elapsed.toFloat() / steps / 1000f
        repeat(steps) {
            val acceleration =
                -rate * rate * (m - targetM) -
                    2f * damping * rate * velocityPerSecond
            velocityPerSecond += acceleration * h
            m += velocityPerSecond * h
        }
        if (abs(targetM - m) <= SETTLE_DISTANCE && abs(velocityPerSecond) <= SETTLE_SPEED) {
            settleAtTarget()
        }
    }

    private fun settleAtTarget() {
        m = targetM
        velocityPerSecond = 0f
        isSettled = true
    }

    companion object {

        /** The curve factor the pill has always used. */
        const val EASE_FACTOR = 1.8f

        /**
         * Spring rate in units per second. Left to itself a continuation settles
         * a full-length morph here in about 250 ms - the length the eased runs
         * already have - so a reversal neither stalls nor snaps. Softer rates
         * make the pill hesitate mid-reversal; stiffer ones read as a different
         * object. One knob, chosen against the durations it replaces, and still
         * a hypothesis until the device has judged it.
         */
        const val SPRING_RATE_PER_SECOND = 40f

        /**
         * How much of the *remaining* distance a reversal may still travel in the
         * direction it was already going. 0.15 keeps the follow-through to about
         * a tenth of the morph: enough that the pill does not stop dead, not
         * enough to read as a bounce.
         */
        const val MAX_FOLLOW_THROUGH = 0.15f

        /** Where a continuation is considered arrived, in distance and in speed. */
        const val SETTLE_DISTANCE = 0.002f
        const val SETTLE_SPEED = 0.02f

        /**
         * Sub-stepping keeps rate x step well under 1, which is what makes the
         * integrator decay instead of ring, whatever frame time arrives.
         */
        const val SUBSTEP_STABILITY = 0.125f

        /** A frame later than this is a stall, not motion: integrate this much only. */
        const val MAX_FRAME_MILLIS = 1000L

        /** The most work one [tick] may do, so a stalled clock cannot spin the loop. */
        const val MAX_SUBSTEPS = 200

        /** A transition from rest, on the pill's own curve and duration. */
        fun easeRun(durationMillis: Long, startM: Float, targetM: Float): PillMorphEngine =
            PillMorphEngine(durationMillis, startM, targetM, 0f, false)

        /**
         * A transition that takes over a live one. [startM] and
         * [velocityPerSecond] are the state of the run being replaced, and
         * [durationMillis] is the cap the caller will tick it for.
         */
        fun springRun(
            startM: Float,
            velocityPerSecond: Float,
            targetM: Float,
            durationMillis: Long
        ): PillMorphEngine {
            val bounded = boundContinuation(velocityPerSecond, targetM - startM)
            val engine = PillMorphEngine(durationMillis, startM, targetM, bounded, true)
            if (targetM == startM) engine.settleAtTarget()
            return engine
        }

        /**
         * Speed a continuation is allowed to keep. Toward the new target, up to
         * rate x remaining distance - the exact condition under which a critically
         * damped spring cannot cross its target. Away from it, a further fraction
         * of that, so a reversal sheds most of its momentum within a frame or two.
         */
        private fun boundContinuation(velocity: Float, remaining: Float): Float {
            if (remaining == 0f || velocity == 0f) return 0f
            val distanceCap = SPRING_RATE_PER_SECOND * abs(remaining)
            val limit = if (velocity.sign == remaining.sign) distanceCap
            else distanceCap * MAX_FOLLOW_THROUGH
            return velocity.sign * minOf(abs(velocity), limit)
        }

        /** How fast the pill's curve is moving at [progress], in curve units. */
        private fun easeSlope(progress: Float): Float {
            val k = 2 * EASE_FACTOR
            return k * (1f - progress).pow(k - 1f)
        }

        /**
         * The pill's easing curve - the same expression, in the same operation
         * order, that [android.view.animation.DecelerateInterpolator] evaluates
         * for [EASE_FACTOR]: (1.0f - Math.pow((1.0f - input), 2 * factor))
         * cast to float. Keeping the order matters: it is what makes the
         * engine's output the identical bytes the service used to render.
         */
        fun decelerate(progress: Float): Float {
            val exponent = 2 * EASE_FACTOR
            val powered = Math.pow((1f - progress).toDouble(), exponent.toDouble())
            return (1.0 - powered).toFloat()
        }

        /**
         * The run an expansion starts: a step response from rest, not an ease.
         *
         * An ease is already at its top speed on the first frame, which reads as
         * weightless - the island simply appears at the new size. A spring starts
         * from zero speed and has to be accelerated, which is the difference
         * between "it appeared" and "it opened". Damping below 1 lets the step pass
         * its target by a few percent before settling, and [maxFraction] lets that
         * overshoot reach the window instead of being clamped into a hold.
         *
         * The heavier launch: the peak lands around 440 ms, just ~1.5% past the
         * target, so the shell has weight without the old rebound. It rests
         * inside the same 740 ms cap. The owner asked for the heavier open; the
         * earlier 15/0.62 spring is deliberately no longer the contract.
         */
        const val LAUNCH_RATE_PER_SECOND = 12f
        const val LAUNCH_DAMPING = 0.8f

        fun launchRun(
            durationMillis: Long,
            targetM: Float = 1f,
            rate: Float = LAUNCH_RATE_PER_SECOND,
            damping: Float = LAUNCH_DAMPING
        ): PillMorphEngine = PillMorphEngine(
            durationMillis, 0f, targetM, 0f, true, damping, rate, 1.12f
        )
    }
}
