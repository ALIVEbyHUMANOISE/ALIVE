package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.QuestHistoryDay
import java.time.LocalDate

/**
 * The last seven days of finished missions.
 *
 * Its own preferences file, for the reason [UsageHistoryRepository] has one:
 * a store whose only job is to remember what was earned must never be able to
 * reach the day-rollover state machine that decides whether a streak survives.
 * Nothing here is authoritative for enforcement, the allowance, or the clock -
 * it is a copy of a fact [QuestRepository] already granted.
 *
 * A day that was never rolled to has no entry, and reads back as unrecorded
 * rather than empty: ALIVE cannot claim a day was idle when it was never
 * opened to see it.
 */
class QuestHistoryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "project_ego_quest_history"

        /** How far back the log reaches; the eighth day is dropped on write. */
        const val WINDOW_DAYS = 7L

        private const val FIELD_SEPARATOR = '|'

        /** Length of a `yyyy-MM-dd` key, so a stray key is ignored rather than parsed. */
        private const val DATE_LENGTH = "yyyy-MM-dd".length
    }

    /**
     * Adds one finished mission and the minutes it paid to [dayKey].
     *
     * The day is opened first, so the count and the empty-day marker are written
     * by the same call, and the caller's day key decides the day - a payout that
     * belongs to yesterday is filed under yesterday even when the rollover that
     * noticed it runs this morning.
     */
    @Synchronized
    fun recordCompletion(dayKey: String, minutes: Long) {
        if (dayKey.length != DATE_LENGTH) return
        ensureDay(dayKey)
        val day = day(dayKey) ?: return
        prefs.edit()
            .putString(dayKey, encode(day.completedCount + 1, day.earnedMinutes + minutes.coerceAtLeast(0L)))
            .apply()
    }

    /** Creates the day at zero when nothing has written it yet. */
    @Synchronized
    fun ensureDay(dayKey: String) {
        if (dayKey.length != DATE_LENGTH) return
        if (prefs.getString(dayKey, null) == null) {
            prefs.edit().putString(dayKey, encode(0, 0L)).apply()
        }
        // Pruned here as well as on a payout, because a week with nothing earned
        // is still a week of writes, and rows nobody can see are rows nobody asked
        // for.
        prune(dayKey)
    }

    /**
     * The record as it goes to disk.
     *
     * A function rather than two inline templates, because the shape of a stored
     * day has to be decided once: `"$count$FIELD_SEPARATOR$0"` would not be that
     * shape - a `$` followed by a digit is a literal dollar sign in a Kotlin
     * string, so the second field of the very first row written here was the text
     * "$0", which no longer parses back into a number.
     */
    private fun encode(count: Int, minutes: Long) = "$count$FIELD_SEPARATOR$minutes"

    /** Null means no entry at all, which is not the same claim as a zero day. */
    @Synchronized
    fun day(dayKey: String): QuestHistoryDay? {
        val raw = prefs.getString(dayKey, null) ?: return null
        val fields = raw.split(FIELD_SEPARATOR)
        if (fields.size != 2) return null
        val count = fields[0].toIntOrNull() ?: return null
        val minutes = fields[1].toLongOrNull() ?: return null
        return QuestHistoryDay(dayKey, count, minutes, recorded = true)
    }

    /**
     * The window, newest first, with a row for every day in it whether or not
     * ALIVE saw that day.
     *
     * Seven keys counted back from today by calendar, so a week off travelling
     * still shows seven rows and the gap in them is the information.
     */
    @Synchronized
    fun recent(todayKey: String, windowDays: Int = WINDOW_DAYS.toInt()): List<QuestHistoryDay> {
        val today = try {
            LocalDate.parse(todayKey)
        } catch (_: Exception) {
            return emptyList()
        }
        return (0 until windowDays).map { back ->
            val key = today.minusDays(back.toLong()).toString()
            day(key) ?: QuestHistoryDay(key, 0, 0L, recorded = false)
        }
    }

    /**
     * `yyyy-MM-dd` is zero-padded, so string comparison is date comparison and
     * the drop needs no work beyond a validity check.
     *
     * Anchored on the newest day the log holds rather than on the day being
     * written: a payout so old that it belongs outside the window is then dropped
     * instead of dragging the whole window back eight days to make room for it.
     */
    private fun prune(dayKey: String) {
        val reference = (prefs.all.keys.filter { it.length == DATE_LENGTH } + dayKey).maxOrNull()
            ?: return
        val cutoff = try {
            LocalDate.parse(reference).minusDays(WINDOW_DAYS - 1).toString()
        } catch (_: Exception) {
            return
        }
        val stale = prefs.all.keys.filter { it.length == DATE_LENGTH && it < cutoff }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }
}
