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
