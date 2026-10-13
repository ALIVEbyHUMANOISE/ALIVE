package com.projectego.app.domain

import com.projectego.app.data.LockStateManager
import java.util.Calendar

/**
 * The promise made the night before, as a rule rather than as storage.
 *
 * ALIVE cannot make anyone keep a resolution: the guard can be switched off,
 * and pretending otherwise would be a lie the app tells about itself. What it
 * can do is move the decision to the hour when the person is calm, and make the
 * impulsive version of them pay to undo it.
 *
 * Two rules live here, and both are arithmetic rather than policy:
 *
 * - When a contract takes effect. A promise written at 21:00 on Tuesday governs
 *   Wednesday, never the rest of Tuesday - the person is still living the day
 *   they already agreed to. But a promise written at 00:30 belongs to the night
 *   that just ended, so the clock is read four hours earlier and it governs the
 *   day it was written on. Without that shift, a contract made after midnight
 *   would wait a whole day to matter, which is precisely when it is no longer
 *   wanted.
 *
 * - That a contract never expires on its own. It is a standing order, replaced
 *   by the next night's contract; someone who falls asleep without writing one
 *   does not wake up punished for it.
 *
 * Dates are the same `yyyy-MM-dd` keys the rest of the app uses, so comparing
 * them as strings is comparing days.
 */
object NightContract {

    /** Four hours: the width of "tonight" that belongs to the day before. */
    const val NIGHT_SHIFT_MILLIS = 4 * 3_600_000L

    /** When the app is willing to ask about tomorrow, and when it stops. */
    const val NIGHT_START_HOUR = 21
    const val NIGHT_END_HOUR = 4

    /**
     * Whether this is an hour for asking about tomorrow.
     *
     * The same window the night shift describes, said as clock hours: from nine
     * in the evening until four in the morning. Outside it, a card offering to
     * write tomorrow's promise is a card offering to undo an afternoon.
     */
    fun isNight(millis: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }

    /** The night a moment belongs to, as the day key the promise is written on. */
    fun nightKeyOf(millis: Long): String = LockStateManager.dayKeyOf(millis - NIGHT_SHIFT_MILLIS)

    /**
     * Whether a contract written on [nightKey] governs [todayKey].
     *
     * Strictly after: the day it was written is already spoken for.
     */
    fun isInForce(nightKey: String, todayKey: String): Boolean = todayKey > nightKey
}
