package com.projectego.app.domain

/**
 * How far the character has grown, as a rule rather than as storage.
 *
 * The form is built from a lifetime count, never from a streak: a form that can
 * be lost would mean one bad day erases three months, and someone who loses
 * three months does not become disciplined - they stop opening the app. A point
 * earned stays earned. What a broken promise costs is the day's condition, not
 * the body.
 *
 * One point per kind of day, so the daily ceiling is three without any ceiling
 * having to be enforced: a quest paid out, a focus session finished, a promise
 * kept. Three quests in one afternoon still count once, because what the
 * character grows on is the day the person had, not the pile of tasks in it.
 *
 * Dates are the same `yyyy-MM-dd` keys the rest of the app uses.
 */
object CharacterGrowth {

    /**
     * Points at which the form changes. The first threshold is deliberately
     * close: attachment is built from a small win in the first week, not from a
     * large one in the third month.
     */
    const val STAGE_TWO_POINTS = 7
    const val STAGE_THREE_POINTS = 30
    const val STAGE_FOUR_POINTS = 100

    enum class Stage { ONE, TWO, THREE, FOUR }

    /** Which form a lifetime of [points] has earned. */
    fun stageFor(points: Int): Stage = when {
        points >= STAGE_FOUR_POINTS -> Stage.FOUR
        points >= STAGE_THREE_POINTS -> Stage.THREE
        points >= STAGE_TWO_POINTS -> Stage.TWO
        else -> Stage.ONE
    }

    /** The three points where a form changes, for a screen that shows the ladder. */
    val THRESHOLDS = listOf(STAGE_TWO_POINTS, STAGE_THREE_POINTS, STAGE_FOUR_POINTS)

    /** The points at which the next form begins, or null at the last form. */
    fun nextThresholdFor(points: Int): Int? = THRESHOLDS.firstOrNull { points < it }

    /** How many points are still missing, or null at the last form. */
    fun pointsToNext(points: Int): Int? = nextThresholdFor(points)?.let { it - points }

    /**
     * The bar's own scale: how far into the current form [points] is, and how
     * long this form is - `3 to 7`, `8 to 30`. Null at the last form, which has
     * no next threshold to be measured against.
     *
     * The screen shows this as "3 dari 7 poin" under the bar, because a bar
     * without its range is a decoration rather than a measurement.
     */
    fun progressWithin(points: Int): Pair<Int, Int>? {
        val next = nextThresholdFor(points) ?: return null
        val floor = THRESHOLDS.lastOrNull { points >= it } ?: 0
        return (points - floor) to (next - floor)
    }

    /**
     * How full the bar towards the next form is, from 0 to 1.
     *
     * Each stage's bar starts empty at its own threshold, so 7 points reads as a
     * new bar rather than a nearly-full one - the same reason a level bar in a
     * game resets: what it measures is the distance to the next form, not the
     * distance from the beginning.
     */
    fun shareToNext(points: Int): Float {
        val next = nextThresholdFor(points) ?: return 1f
        val floor = THRESHOLDS.lastOrNull { points >= it } ?: 0
        return ((points - floor).toFloat() / (next - floor)).coerceIn(0f, 1f)
    }

    /**
     * Whether a day that ended on [lastCountedDay] still owes a point for
     * [todayKey].
     *
     * Null means nothing of this kind has ever been counted, which is not the
     * same as counted on some other day - both owe the point, but only one of
     * them can happen on a first run.
     */
    fun owesPoint(lastCountedDay: String?, todayKey: String): Boolean =
        lastCountedDay != todayKey
}
