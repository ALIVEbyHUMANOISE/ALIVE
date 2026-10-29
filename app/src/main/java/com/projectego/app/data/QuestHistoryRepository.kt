package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.QuestCompletion
import com.projectego.app.model.QuestHistoryDay
import java.time.LocalDate

/**
 * The last seven days of finished missions, by name.
 *
 * Its own preferences file, for the reason [UsageHistoryRepository] has one:
 * a store whose only job is to remember what was earned must never be able to
 * reach the day-rollover state machine that decides whether a streak survives.
 * Nothing here is authoritative for enforcement, the allowance, or the clock -
 * it is a copy of a fact [QuestRepository] already granted.
 *
 * Two shapes live in this file, on purpose:
 *  - `yyyy-MM-dd` holds `count|minutes`, and is now only a **day marker**: it is
 *    written once when the day is first seen, so "nothing finished" and "never
 *    watched" stay different claims. Days recorded before this build carried
 *    their totals in this key and never stored names; those totals are kept and
 *    the page says the names were not recorded then.
 *  - `yyyy-MM-dd#attemptId` holds `questId <US> title <US> minutes <US> millis`,
 *    one row per mission that paid. The attempt id in the key is what makes a
 *    double claim impossible to double-record: the same attempt overwrites its
 *    own row instead of adding one.
 */
class QuestHistoryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "project_ego_quest_history"

        /** How far back the log reaches; the eighth day is dropped on write. */
        const val WINDOW_DAYS = 7L

        private const val FIELD_SEPARATOR = '|'
        private const val ROW_SEPARATOR = '#'

        /** ASCII unit separator - the one character a mission title cannot produce. */
        private val RECORD_SEPARATOR = Character.toString(0x1F)

        /** Length of a `yyyy-MM-dd` key, so a stray key is ignored rather than parsed. */
        private const val DATE_LENGTH = "yyyy-MM-dd".length
    }

    /**
     * Adds one finished mission and the minutes it paid to [dayKey].
     *
     * The day is opened first, so the marker and the row are written by the same
     * call, and the caller's day key decides the day - a payout that belongs to
     * yesterday is filed under yesterday even when the rollover that noticed it
     * runs this morning.
     */
    @Synchronized
    fun recordCompletion(
        dayKey: String,
        attemptId: String,
        questId: String,
        title: String,
        minutes: Long,
        completedAt: Long
    ) {
        if (dayKey.length != DATE_LENGTH || attemptId.isBlank() || questId.isBlank()) return
        ensureDay(dayKey)
        val record = listOf(
            questId,
            title.replace(RECORD_SEPARATOR, " ").trim().ifBlank { questId },
            minutes.coerceAtLeast(0L).toString(),
            completedAt.toString()
        ).joinToString(RECORD_SEPARATOR)
        prefs.edit().putString(rowKey(dayKey, attemptId), record).apply()
        // Pruned again after the write, not only before it: the row did not exist
        // when ensureDay pruned, so a payout belonging to a day already outside
        // the window would otherwise survive as the one row nobody can see.
        prune(dayKey)
    }

    /** Creates the day at zero when nothing has written it yet. */
    @Synchronized
    fun ensureDay(dayKey: String) {
        if (dayKey.length != DATE_LENGTH) return
        if (prefs.getString(dayKey, null) == null) {
            prefs.edit().putString(dayKey, encodeMarker(0, 0L)).apply()
        }
        // Pruned here as well as on a payout, because a week with nothing earned
        // is still a week of writes, and rows nobody can see are rows nobody asked
        // for.
        prune(dayKey)
    }

    /** Null means no entry at all, which is not the same claim as a zero day. */
    @Synchronized
    fun day(dayKey: String): QuestHistoryDay? {
        val marker = prefs.getString(dayKey, null)
        val rows = completions(dayKey)
        if (marker == null && rows.isEmpty()) return null
        val legacy = parseMarker(marker)
        return QuestHistoryDay(
            dayKey = dayKey,
            completedCount = legacy.first + rows.size,
            earnedMinutes = legacy.second + rows.sumOf { it.minutes },
            recorded = true,
            completions = rows
        )
    }

    /**
     * Every named mission recorded for [dayKey], oldest first.
     *
     * Rows are stored as separate keys, which SharedPreferences hands back in no
     * particular order, so the order is the moment each one paid - the record
     * reads in the order the day actually happened.
     */
    @Synchronized
    fun completions(dayKey: String): List<QuestCompletion> {
        val prefix = dayKey + ROW_SEPARATOR
        return prefs.all.entries
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { parseRow(it.value as? String) }
            .sortedBy { it.completedAt }
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

    private fun rowKey(dayKey: String, attemptId: String): String =
        dayKey + ROW_SEPARATOR + attemptId

    /**
     * The day marker as two numbers. A marker written by this build is "0|0";
     * an older one carried the day's totals, which are still true and are kept.
     * Anything unreadable counts as nothing rather than as a crash.
     */
    private fun parseMarker(raw: String?): Pair<Int, Long> {
        if (raw.isNullOrBlank()) return 0 to 0L
        val fields = raw.split(FIELD_SEPARATOR)
        if (fields.size != 2) return 0 to 0L
        val count = fields[0].toIntOrNull() ?: return 0 to 0L
        val minutes = fields[1].toLongOrNull() ?: return 0 to 0L
        return count to minutes
    }

    private fun parseRow(raw: String?): QuestCompletion? {
        if (raw.isNullOrBlank()) return null
        val fields = raw.split(RECORD_SEPARATOR)
        if (fields.size != 4) return null
        val questId = fields[0].takeIf { it.isNotBlank() } ?: return null
        val minutes = fields[2].toLongOrNull() ?: return null
        val completedAt = fields[3].toLongOrNull() ?: return null
        return QuestCompletion(questId, fields[1], minutes, completedAt)
    }

    private fun encodeMarker(count: Int, minutes: Long) = "$count$FIELD_SEPARATOR$minutes"

    /**
     * `yyyy-MM-dd` is zero-padded, so string comparison is date comparison and
     * the drop needs no work beyond a validity check.
     *
     * Anchored on the newest day the log holds rather than on the day being
     * written: a payout so old that it belongs outside the window is then dropped
     * instead of dragging the whole window back eight days to make room for it.
     * Both shapes are filtered by the date their key starts with, because a row
     * that outlived its day would otherwise stay in the file forever.
     */
    private fun prune(dayKey: String) {
        val keys = prefs.all.keys.filter { it.length >= DATE_LENGTH }
        val reference = (keys.map { it.take(DATE_LENGTH) } + dayKey).maxOrNull() ?: return
        val cutoff = try {
            LocalDate.parse(reference).minusDays(WINDOW_DAYS - 1).toString()
        } catch (_: Exception) {
            return
        }
        val stale = keys.filter { key ->
            val date = key.take(DATE_LENGTH)
            date[4] == '-' && date[7] == '-' && date < cutoff
        }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }
}
