package com.projectego.app.domain

import kotlin.math.min

/**
 * The character's five-minute visit, as pure arithmetic.
 *
 * The pill is idle almost all of the time. Every [INTERVAL_MILLIS] the eyes
 * emerge, spend half a minute looking around, blinking and changing shape,
 * fall asleep under a rising Zzz, and fade back out. Thirty seconds is
 * deliberate: the character is the only thing in the island that is alive, and
 * a six-second flash read as a glitch rather than as a visit.
 *
 * Keeping the performance here rather than in the service has two payoffs: the
 * whole thirty seconds are testable without an Android clock, and the service
 * only answers one question per frame - [eyesAt] - instead of holding a state
 * machine of its own.
 */
object PillCharacterCycle {

    /** How often a visit begins while the pill is running. */
    const val INTERVAL_MILLIS: Long = 5 * 60 * 1000L

    /** The eyes fade up from the shell. */
    const val REVEAL_MILLIS: Long = 400L

    /** Awake: looking about, blinking, narrowing and rounding again. */
    const val EXPRESS_MILLIS: Long = 21_000L

    /** Settled into the sleeping expression under the Zzz. */
    const val SLEEP_MILLIS: Long = 8_000L

    /** The eyes fade back out; the pill is ordinary again. */
    const val FADE_MILLIS: Long = 600L

    const val TOTAL_MILLIS: Long =
        REVEAL_MILLIS + EXPRESS_MILLIS + SLEEP_MILLIS + FADE_MILLIS

    enum class Phase { HIDDEN, REVEAL, EXPRESS, SLEEP, FADE }

    /**
     * How the hour colours the visit.
     *
     * Alert in the morning, sleepy late: the awake stretch can grow or shrink,
     * and the sleeping stretch takes exactly what the awake one gave up, so the
     * visit is always the same total length. The eyes also narrow and blink
     * differently - the character reads the clock without a single new word.
     */
    data class Mood(
        val expressDelta: Long,
        val narrowFactor: Float,
        val blinkFactor: Float
    )

    val NEUTRAL_MOOD = Mood(expressDelta = 0L, narrowFactor = 1f, blinkFactor = 1f)

    fun moodFor(hour: Int): Mood = when (hour) {
        in 5..11 -> Mood(expressDelta = 1_000L, narrowFactor = 1f, blinkFactor = 0.85f)
        in 18..23 -> Mood(expressDelta = -1_500L, narrowFactor = 0.9f, blinkFactor = 1.25f)
        in 0..4 -> Mood(expressDelta = -2_500L, narrowFactor = 0.82f, blinkFactor = 1.5f)
        else -> NEUTRAL_MOOD
    }

    private fun expressEnd(mood: Mood): Long = REVEAL_MILLIS + EXPRESS_MILLIS + mood.expressDelta

    private fun sleepEnd(mood: Mood): Long = expressEnd(mood) + SLEEP_MILLIS - mood.expressDelta

    /**
     * One frame of the performance.
     *
     * [heightScale] is the eye's height as a fraction of its open shape: 1 is
     * the round oval, ~0.68 is the flat one. [gaze] is -1 looking left, 0 at
     * the viewer, +1 looking right. [sleepPhase] runs 0..1 through the sleeping
     * window and drives the Zzz.
     */
    data class EyeFrame(
        val expression: EyeExpression,
        val alpha: Float,
        val heightScale: Float,
        val gaze: Float,
        val sleepPhase: Float
    )

    enum class EyeExpression { OPEN, BLINK, SLEEP }

    /** Blinks land between looks, where they read as alive rather than as ticks. */
    private val BLINKS = longArrayOf(4_200L, 8_900L, 13_600L, 17_800L)
    private const val BLINK_MILLIS = 130L

    /** Where the eyes look, in milliseconds into the awake phase. */
    private val GAZE = arrayOf(
        0L to 0f,
        1_200L to 0f,
        1_800L to -0.8f,
        3_200L to -0.8f,
        4_000L to 0f,
        5_200L to 0f,
        6_000L to 0.85f,
        7_800L to 0.85f,
        8_600L to 0f,
        10_000L to 0f,
        11_000L to -0.5f,
        12_500L to -0.5f,
        13_300L to 0f,
        15_000L to 0.6f,
        16_500L to 0.6f,
        17_300L to 0f,
        19_000L to 0f,
        EXPRESS_MILLIS to 0f
    )

    /** How tall the eyes are: 1 = round oval, 0.68 = narrowed. */
    private val SHAPE = arrayOf(
        0L to 1f,
        2_500L to 1f,
        3_300L to 0.68f,
        6_000L to 0.68f,
        6_800L to 1f,
        11_000L to 1f,
        12_000L to 0.7f,
        15_000L to 0.7f,
        15_800L to 1f,
        EXPRESS_MILLIS to 1f
    )

    fun phaseAt(elapsedMillis: Long, mood: Mood = NEUTRAL_MOOD): Phase = when {
        elapsedMillis < 0L || elapsedMillis >= TOTAL_MILLIS -> Phase.HIDDEN
        elapsedMillis < REVEAL_MILLIS -> Phase.REVEAL
        elapsedMillis < expressEnd(mood) -> Phase.EXPRESS
        elapsedMillis < sleepEnd(mood) -> Phase.SLEEP
        else -> Phase.FADE
    }

    /**
     * Everything the compact pill needs for the frame at [elapsedMillis] since
     * the visit began. Outside the visit the answer is a fully transparent
     * frame, so a late or duplicated tick can never leave eyes on screen.
     */
    fun eyesAt(elapsedMillis: Long, mood: Mood = NEUTRAL_MOOD): EyeFrame =
        when (phaseAt(elapsedMillis, mood)) {
            Phase.HIDDEN -> EyeFrame(EyeExpression.OPEN, 0f, 1f, 0f, 0f)

            Phase.REVEAL -> EyeFrame(
                expression = EyeExpression.OPEN,
                alpha = (elapsedMillis.toFloat() / REVEAL_MILLIS.toFloat()).coerceIn(0f, 1f),
                heightScale = mood.narrowFactor,
                gaze = 0f,
                sleepPhase = 0f
            )

            Phase.EXPRESS -> {
                val awake = (elapsedMillis - REVEAL_MILLIS)
                    .coerceIn(0L, EXPRESS_MILLIS)
                EyeFrame(
                    expression = if (isBlinking(awake, mood)) EyeExpression.BLINK
                    else EyeExpression.OPEN,
                    alpha = 1f,
                    heightScale = (sample(SHAPE, awake) * mood.narrowFactor).coerceIn(0.4f, 1.2f),
                    gaze = sample(GAZE, awake),
                    sleepPhase = 0f
                )
            }

            Phase.SLEEP, Phase.FADE -> {
                val start = expressEnd(mood)
                val length = sleepEnd(mood) - start
                val intoSleep = (elapsedMillis - start).coerceIn(0L, length)
                EyeFrame(
                    expression = EyeExpression.SLEEP,
                    alpha = sleepAlpha(elapsedMillis, mood),
                    heightScale = mood.narrowFactor,
                    gaze = 0f,
                    sleepPhase = intoSleep.toFloat() / length.toFloat().coerceAtLeast(1f)
                )
            }
        }

    /** When the next visit is owed, given when the current one started. */
    fun nextDueAt(startedAtMillis: Long): Long = startedAtMillis + INTERVAL_MILLIS

    /** True when a visit that started at [startedAtMillis] has finished. */
    fun isFinished(elapsedMillis: Long): Boolean = elapsedMillis >= TOTAL_MILLIS

    /** Progress through the whole visit, 0..1. */
    fun progressAt(elapsedMillis: Long): Float =
        if (elapsedMillis <= 0L) 0f else min(1f, elapsedMillis.toFloat() / TOTAL_MILLIS)

    private fun isBlinking(intoExpress: Long, mood: Mood): Boolean {
        val length = (BLINK_MILLIS * mood.blinkFactor).toLong().coerceAtLeast(60L)
        return BLINKS.any { blinkAt -> intoExpress >= blinkAt && intoExpress < blinkAt + length }
    }

    /** Linear read of a small keyframe table; both tables start at 0 and end at EXPRESS. */
    private fun sample(frames: Array<Pair<Long, Float>>, at: Long): Float {
        if (at <= frames.first().first) return frames.first().second
        for (i in 1 until frames.size) {
            val (time, value) = frames[i]
            if (at <= time) {
                val (prevTime, prevValue) = frames[i - 1]
                val span = (time - prevTime).toFloat().coerceAtLeast(1f)
                val t = (at - prevTime).toFloat() / span
                return prevValue + (value - prevValue) * t
            }
        }
        return frames.last().second
    }

    private fun sleepAlpha(elapsedMillis: Long, mood: Mood): Float {
        val fadeStart = sleepEnd(mood)
        if (elapsedMillis < fadeStart) return 1f
        val intoFade = (elapsedMillis - fadeStart).toFloat() / FADE_MILLIS.toFloat()
        return (1f - intoFade).coerceIn(0f, 1f)
    }
}
