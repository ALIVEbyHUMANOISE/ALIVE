package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.UsageDay
import java.time.LocalDate

/**
 * Remembers how much entertainment time each past day actually used.
 *
 * Kept in its own preferences file rather than inside [LockStateManager] on
 * purpose: that class is a day-rollover state machine whose writes decide
 * whether a streak continues and whether the app is locked. A store whose only
 * job is to remember numbers must never be able to break it.
 *
 * Nothing here is authoritative for enforcement. Every value in this file is a
 * copy of something `UsageStatsManager` already reported; the lock, the tiers
 * and the quests all keep reading the live source.
 */
class UsageHistoryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "project_ego_usage_history"

        /** Enough for a month-to-date view plus slack; a few KB at most. */
        const val MAX_RETAINED_DAYS = 40L

        const val FIELD_SEPARATOR = '|'
        const val APP_SEPARATOR = ','
        const val KEY_VALUE_SEPARATOR = '='

        /** Length of a `yyyy-MM-dd` key; used to ignore any non-date entry. */
        const val DATE_LENGTH = "yyyy-MM-dd".length

        /**
         * What ALIVE's accounting believed when the record was written.
         *
         * Version 1 charged messaging and study apps; version 2 charges only
         * what the app actually closes, and started recording the day's whole
         * screen time. The never-shrink rule below exists to stop the platform
         * from un-drawing an hour already shown, but a change of rule makes
         * today's honest reading *smaller* than the record an older rule left -
         * and without this number the new rule could never replace the old one,
         * freezing the day at figures ALIVE no longer stands behind.
         */
        const val RULE_VERSION = 2
    }

    /**
     * Records today's figures.
     *
     * Largest-wins, never additive: `UsageStats.totalTimeInForeground` is
     * already the cumulative total for the day, so a second reading must
     * replace the first. Taking the largest rather than the latest matters
     * because an OEM can trim `UsageStats` mid-day, and a bar that shrinks
     * would be a visible lie about a day that has already happened.
     *
     * Writes only when the stored value would actually change, because this is
     * called from the accessibility path on every entertainment window change.
     */
    @Synchronized
    fun recordDay(
        date: String,
        totalMillis: Long,
        limitMinutes: Long,
        perAppMinutes: Map<String, Long>,
        screenMillis: Long
    ) {
        if (date.isBlank() || totalMillis < 0L) return

        val storedMillis = storedMillis(date)
        val storedVersion = storedVersion(date) ?: 1
        val writtenByThisRule = storedVersion >= RULE_VERSION
        if (storedMillis != null && totalMillis < storedMillis && writtenByThisRule) return

        // The screen figure grows on its own schedule: an hour of chatting after
        // the last reading moves it while the entertainment total stands still,
        // and a day whose entertainment was trimmed by the platform keeps the
        // largest screen figure ALIVE saw for it too.
        val storedScreen = storedScreenMillis(date)
        val screen = maxOf(screenMillis, storedScreen ?: 0L)

        val serialized = serialize(totalMillis, limitMinutes, perAppMinutes, screen)
        if (serialized == prefs.getString(date, null)) return

        prefs.edit().putString(date, serialized).apply()
        trimOlderThan(date)
    }

    /** Null means ALIVE never saw that day. It does not mean the day was zero. */
    @Synchronized
    fun day(date: String): UsageDay? = parse(date, prefs.getString(date, null))

    @Synchronized
    fun daysFor(dates: List<String>): Map<String, UsageDay> {
        val stored = prefs.all
        return dates.mapNotNull { date ->
            parse(date, stored[date] as? String)?.let { date to it }
        }.toMap()
    }

    @Synchronized
    fun oldestRecordedDate(): String? =
        prefs.all.keys.filter { it.length == DATE_LENGTH }.minOrNull()

    @Synchronized
    fun recordedCount(): Int = prefs.all.keys.count { it.length == DATE_LENGTH }

    /**
     * `yyyy-MM-dd` is zero-padded, so string comparison is date comparison and
     * the trim needs no parsing beyond a validity check.
     */
    private fun trimOlderThan(todayKey: String) {
        val cutoff = try {
            LocalDate.parse(todayKey).minusDays(MAX_RETAINED_DAYS).toString()
        } catch (_: Exception) {
            return
        }
        val stale = prefs.all.keys.filter { it.length == DATE_LENGTH && it < cutoff }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    private fun serialize(
        totalMillis: Long,
        limitMinutes: Long,
        perAppMinutes: Map<String, Long>,
        screenMillis: Long
    ): String {
        // Package names are [A-Za-z0-9_.], so no separator can appear in one;
        // filtered anyway so a hostile or buggy value cannot corrupt the record.
        val apps = perAppMinutes
            .filter { (pkg, minutes) ->
                minutes > 0L && !pkg.contains(FIELD_SEPARATOR) &&
                    !pkg.contains(APP_SEPARATOR) && !pkg.contains(KEY_VALUE_SEPARATOR)
            }
            .entries
            .joinToString(APP_SEPARATOR.toString()) { (pkg, minutes) ->
                "$pkg$KEY_VALUE_SEPARATOR$minutes"
            }
        return "$totalMillis$FIELD_SEPARATOR$limitMinutes$FIELD_SEPARATOR$apps" +
            "$FIELD_SEPARATOR$screenMillis$FIELD_SEPARATOR$RULE_VERSION"
    }

    /** Anything unparseable reads as missing rather than as zero, and never throws. */
    private fun parse(date: String, raw: String?): UsageDay? {
        if (raw.isNullOrBlank()) return null
        val fields = raw.split(FIELD_SEPARATOR)
        // Three fields is a day recorded before the screen total existed. It is
        // not a day with no screen time, so that figure stays null rather than 0.
        if (fields.size < 3 || fields.size > 5) return null
        val totalMillis = fields[0].toLongOrNull() ?: return null
        val limitMinutes = fields[1].toLongOrNull() ?: return null
        val screenMillis = fields.getOrNull(3)?.toLongOrNull()

        val perApp = if (fields[2].isBlank()) {
            emptyMap()
        } else {
            fields[2].split(APP_SEPARATOR).mapNotNull { entry ->
                val parts = entry.split(KEY_VALUE_SEPARATOR)
                if (parts.size != 2) return@mapNotNull null
                val minutes = parts[1].toLongOrNull() ?: return@mapNotNull null
                parts[0] to minutes
            }.toMap()
        }

        return UsageDay(
            date = date,
            totalMinutes = totalMillis / 60_000L,
            limitMinutes = limitMinutes,
            perAppMinutes = perApp,
            screenMinutes = screenMillis?.let { it / 60_000L }
        )
    }

    /** The raw stored total for a day, or null when there is no usable record. */
    private fun storedMillis(date: String): Long? =
        fieldOf(date, 0)

    /** The stored screen total, or null when the day has none or does not exist. */
    private fun storedScreenMillis(date: String): Long? =
        fieldOf(date, 3)

    /** The rule that wrote the record; one without a version predates them all. */
    private fun storedVersion(date: String): Int? =
        prefs.getString(date, null)
            ?.split(FIELD_SEPARATOR)
            ?.getOrNull(4)
            ?.toIntOrNull()

    private fun fieldOf(date: String, index: Int): Long? =
        prefs.getString(date, null)
            ?.split(FIELD_SEPARATOR)
            ?.getOrNull(index)
            ?.toLongOrNull()
}
