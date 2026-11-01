package com.projectego.app.data

import android.content.Context
import com.projectego.app.domain.DrinkReminderPolicy

/**
 * The two reminders' preferences, together because the page that edits them is
 * one page.
 *
 * Nothing here schedules anything. The stores are plain facts - is it on, how
 * often, which hours, when did the last glass happen - so the same values can be
 * read by the alarm, the pill, and the page without one of them owning the
 * others. [DrinkReminderWatch] is the only writer of the alarm.
 */
object ReminderSettings {

    private const val PREFS_FILE = "project_ego_reminders"

    private const val KEY_WATER_ENABLED = "key_water_enabled"
    private const val KEY_WATER_INTERVAL = "key_water_interval"
    private const val KEY_WATER_START = "key_water_start"
    private const val KEY_WATER_END = "key_water_end"
    private const val KEY_LAST_DRINK = "key_last_drink"
    private const val KEY_LAST_FIRED = "key_last_fired"
    private const val KEY_SNOOZE_UNTIL = "key_snooze_until"

    private const val KEY_PROMISE_ENABLED = "key_promise_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // ---------- water ----------

    fun waterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WATER_ENABLED, false)

    fun intervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_WATER_INTERVAL, DrinkReminderPolicy.DEFAULT_INTERVAL_MINUTES)

    fun startHour(context: Context): Int =
        prefs(context).getInt(KEY_WATER_START, DrinkReminderPolicy.DEFAULT_START_HOUR)

    fun endHour(context: Context): Int =
        prefs(context).getInt(KEY_WATER_END, DrinkReminderPolicy.DEFAULT_END_HOUR)

    fun lastDrinkAt(context: Context): Long = prefs(context).getLong(KEY_LAST_DRINK, 0L)

    fun lastFiredAt(context: Context): Long = prefs(context).getLong(KEY_LAST_FIRED, 0L)

    fun snoozeUntil(context: Context): Long = prefs(context).getLong(KEY_SNOOZE_UNTIL, 0L)

    /**
     * Turning the reminder on starts the clock now, not at the last glass of a
     * previous install: a switch that fires the moment it is touched teaches
     * people not to touch it.
     */
    fun enableWater(context: Context, nowMillis: Long) {
        prefs(context).edit()
            .putBoolean(KEY_WATER_ENABLED, true)
            .putLong(KEY_LAST_DRINK, nowMillis)
            .putLong(KEY_LAST_FIRED, 0L)
            .putLong(KEY_SNOOZE_UNTIL, 0L)
            .apply()
    }

    fun disableWater(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_WATER_ENABLED, false)
            .putLong(KEY_SNOOZE_UNTIL, 0L)
            .apply()
    }

    fun setWaterSchedule(context: Context, intervalMinutes: Int, startHour: Int, endHour: Int) {
        prefs(context).edit()
            .putInt(KEY_WATER_INTERVAL, intervalMinutes)
            .putInt(KEY_WATER_START, startHour)
            .putInt(KEY_WATER_END, endHour)
            .apply()
    }

    fun markWaterDrank(context: Context, nowMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_LAST_DRINK, nowMillis)
            .putLong(KEY_LAST_FIRED, 0L)
            .putLong(KEY_SNOOZE_UNTIL, 0L)
            .apply()
    }

    fun markWaterFired(context: Context, nowMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_LAST_FIRED, nowMillis)
            .putLong(KEY_SNOOZE_UNTIL, 0L)
            .apply()
    }

    fun snoozeWater(context: Context, untilMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_SNOOZE_UNTIL, untilMillis)
            .apply()
    }

    /** The next moment the reminder is due, or 0 when it is switched off. */
    fun nextWaterDue(context: Context, nowMillis: Long): Long {
        if (!waterEnabled(context)) return 0L
        val anchor = maxOf(lastDrinkAt(context), lastFiredAt(context))
        return DrinkReminderPolicy.nextDueMillis(
            nowMillis = nowMillis,
            anchorMillis = anchor,
            intervalMinutes = intervalMinutes(context),
            startHour = startHour(context),
            endHour = endHour(context),
            snoozeUntilMillis = snoozeUntil(context)
        )
    }

    // ---------- the nightly promise ----------

    fun promiseEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROMISE_ENABLED, true)

    fun setPromiseEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROMISE_ENABLED, enabled).apply()
    }
}
