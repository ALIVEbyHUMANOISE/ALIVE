package com.projectego.app.domain

import java.util.Calendar

/**
 * When the water reminder is allowed to speak, as arithmetic.
 *
 * A reminder that ignores the hour is a reminder people turn off: the point of
 * the active window is that ALIVE stays quiet at night without needing a second
 * switch for it. The policy owns the clock; the service only stores its answer
 * in the alarm manager, and the page only prints it.
 *
 * The window is half-open - [startHour, endHour) - so "07 until 22" means the
 * last reminder of the day is due before ten, not at ten.
 */
object DrinkReminderPolicy {

    val INTERVAL_CHOICES = intArrayOf(45, 60, 90, 120)

    /** The three windows the page offers, as start/end pairs. */
    val WINDOW_CHOICES = arrayOf(7 to 22, 8 to 22, 9 to 23)

    const val DEFAULT_INTERVAL_MINUTES = 60
    const val DEFAULT_START_HOUR = 7
    const val DEFAULT_END_HOUR = 22

    /** A snooze is fifteen minutes, the same promise the notification makes. */
    const val SNOOZE_MINUTES = 15

    /**
     * A due time inside the window, or the next window's opening.
     *
     * [anchorMillis] is the later of "the last glass" and "the last time ALIVE
     * spoke"; the person ignoring a reminder must still be reminded again, so
     * the service moves that anchor when it fires rather than waiting forever
     * for a glass it was told about.
     *
     * A minute of lead is deliberate: a due time already in the past would make
     * the alarm manager deliver instantly and the receiver re-arm instantly,
     * which is a busy loop rather than a reminder.
     */
    fun nextDueMillis(
        nowMillis: Long,
        anchorMillis: Long,
        intervalMinutes: Int,
        startHour: Int,
        endHour: Int,
        snoozeUntilMillis: Long = 0L
    ): Long {
        val (start, end) = normalizedWindow(startHour, endHour)
        val interval = intervalMinutes.coerceIn(15, 240) * 60_000L
        val regular = anchorMillis + interval
        val earliest = nowMillis + 60_000L
        val due = maxOf(regular, earliest, snoozeUntilMillis.coerceAtLeast(0L))
        return clipToWindow(due, start, end)
    }

    /** Whether [nowMillis] is inside the active window, no clipping involved. */
    fun isWithinWindow(nowMillis: Long, startHour: Int, endHour: Int): Boolean {
        val (start, end) = normalizedWindow(startHour, endHour)
        val hour = hourOf(nowMillis)
        return hour in start until end
    }

    private fun clipToWindow(dueMillis: Long, startHour: Int, endHour: Int): Long {
        val dayStart = atHour(dueMillis, startHour)
        val dayEnd = atHour(dueMillis, endHour)
        return when {
            dueMillis < dayStart -> dayStart
            dueMillis >= dayEnd -> atHour(nextDay(dueMillis), startHour)
            else -> dueMillis
        }
    }

    /**
     * A malformed pair falls back to the default window rather than throwing:
     * a stored preference can outlive the build that wrote it, and a reminder
     * that keeps working is better than one that crashes at boot.
     */
    private fun normalizedWindow(startHour: Int, endHour: Int): Pair<Int, Int> {
        val start = startHour.takeIf { it in 0..23 } ?: DEFAULT_START_HOUR
        val end = endHour.takeIf { it in 0..24 } ?: DEFAULT_END_HOUR
        return if (end > start) start to end else DEFAULT_START_HOUR to DEFAULT_END_HOUR
    }

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    private fun atHour(millis: Long, hour: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun nextDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
}
