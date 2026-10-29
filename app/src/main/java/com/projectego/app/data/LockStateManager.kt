package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.DayType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LockStateManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Last night's promise, when there is one in force. See the limit below. */
    private val nightContract = NightContractRepository(context)

    companion object {
        private const val PREFS_FILE = "project_ego_state"

        private const val KEY_LAST_TRACKED_DATE = "key_last_tracked_date"
        private const val KEY_IS_LOCKED = "key_is_locked"
        private const val KEY_DEV_TEST_MODE = "key_dev_test_mode"
        private const val KEY_LAST_WARNING_TIER = "key_last_warning_tier"
        private const val KEY_CURRENT_STREAK = "key_current_streak"
        private const val KEY_DAY_TYPE = "key_day_type"
        private const val KEY_LAST_DAY_FAILED = "key_last_day_failed"

        // Which day the last rollover actually judged, and the streak that day
        // cost. Kept so a mission that lands late - finished yesterday, claimed
        // after midnight - can give that day back instead of losing it.
        private const val KEY_LAST_JUDGED_DAY = "key_last_judged_day"
        private const val KEY_STREAK_BEFORE_FAIL = "key_streak_before_fail"

        // The guard gap: a stretch during one day when the accessibility service
        // that does the closing was off. Keyed by day so it scopes itself, and so
        // no reset has to remember to clear it.
        private const val KEY_UNGUARDED_DAY = "key_unguarded_day"
        private const val KEY_GUARD_REPAIR_BASELINE = "key_guard_repair_baseline"
        private const val KEY_GUARD_REPAIRED = "key_guard_repaired"

        // The last day a quest actually paid out. Keyed by day like the guard gap,
        // so it scopes itself: what decides tomorrow is whether *that* day earned,
        // not when the record happened to be written.
        private const val KEY_QUEST_DONE_DAY = "key_quest_done_day"

        // Whether tomorrow has ever been judged by the quest rule. The first
        // rollover after the rule ships is deliberately not judged - yesterday was
        // lived under the old one, and a rule nobody could have read is not a
        // reason to take someone's day away.
        private const val KEY_QUEST_RULE_ANNOUNCED = "key_quest_rule_announced"

        // Minutes claimed from the Time Bank onto today's entertainment
        // allowance, keyed by the day they were claimed on - the same
        // self-scoping trick as the guard gap: a different day makes the number
        // stale, so no rollover has to remember to clear it.
        private const val KEY_CLAIMED_DAY = "key_claimed_day"
        private const val KEY_CLAIMED_MINUTES = "key_claimed_minutes"

        const val PRODUCTION_LIMIT_MINUTES = 120L
        const val DEV_TEST_LIMIT_MINUTES = 1L

        /**
         * The single `yyyy-MM-dd` key for a moment in time.
         *
         * The usage history is keyed by this, and the day-rollover check below
         * compares against it. Two separate date formatters in those two places
         * would drift silently and the chart would look empty to the engine, so
         * both use this one.
         *
         * The locale is pinned to US on purpose. The key is an identifier, not
         * text a person reads: with the device locale, a Thai phone writes the
         * Buddhist year (2569-10-26) and an Arabic one writes localized digits,
         * so changing the phone's language renamed every day ALIVE had stored -
         * the next rollover read a different "yesterday" and judged it failed.
         * Pinning keeps the calendar Gregorian and the digits ASCII; the
         * timezone is still the device's, so midnight is still local.
         */
        fun dayKeyOf(timeMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMillis))

        /**
         * Mark [dayKey] as a day on which a quest paid out.
         *
         * A companion called from `QuestRepository.claimReward()`, because that is
         * the one place a reward exists and the repository that grants it should not
         * have to build a second one just to record the fact. The caller passes the
         * day the mission was *lived* (see `QuestDayAttribution`); the default is
         * only right for callers that genuinely mean "right now".
         *
         * If that day is the one the morning's rollover just judged as failed, the
         * judgement is repaired: a mission finished at 23:50 and claimed after
         * midnight used to cost the next day, which reads as the app forgetting
         * something the person actually did.
         */
        @Synchronized
        fun recordQuestCompleted(
            context: Context,
            dayKey: String = dayKeyOf(System.currentTimeMillis())
        ) {
            val appPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            appPrefs.edit()
                .putString(KEY_QUEST_DONE_DAY, dayKey)
                .apply()
            // The same moment is the character's: a quest that paid out is a day
            // the person actually did something, which is what the form grows on.
            CharacterGrowthRepository(context).recordQuestDay(dayKey)

            restoreJudgedDay(appPrefs, dayKey)
        }

        /**
         * Gives back a day that was judged failed and then earned its quest.
         *
         * Deliberately narrow: only the day the most recent rollover judged, only
         * while that judgement is still today's, and never across an unrepaired
         * guard gap - the gap rule outranks late evidence, exactly as it outranks
         * a claim. A quest for any other day changes nothing, so finishing a
         * mission this afternoon can never unlock a day that was already spent.
         */
        private fun restoreJudgedDay(appPrefs: SharedPreferences, questDay: String) {
            val today = dayKeyOf(System.currentTimeMillis())
            if (appPrefs.getString(KEY_LAST_TRACKED_DATE, null) != today) return
            if (appPrefs.getString(KEY_LAST_JUDGED_DAY, null) != questDay) return
            if (!appPrefs.getBoolean(KEY_LAST_DAY_FAILED, false)) return
            if (storedDayType(appPrefs) != DayType.ZERO_ENTERTAINMENT_DAY) return

            val gapOpen = appPrefs.getString(KEY_UNGUARDED_DAY, null) == questDay &&
                !appPrefs.getBoolean(KEY_GUARD_REPAIRED, false)
            if (gapOpen) return

            val streakBefore = appPrefs.getInt(KEY_STREAK_BEFORE_FAIL, 0)
            appPrefs.edit()
                .putString(KEY_DAY_TYPE, DayType.NORMAL_DAY.name)
                .putBoolean(KEY_IS_LOCKED, false)
                .putBoolean(KEY_LAST_DAY_FAILED, false)
                .putInt(KEY_CURRENT_STREAK, streakBefore + 1)
                .putInt(KEY_STREAK_BEFORE_FAIL, 0)
                .putInt(KEY_LAST_WARNING_TIER, 0)
                .apply()
        }

        private fun storedDayType(appPrefs: SharedPreferences): DayType {
            val name = appPrefs.getString(KEY_DAY_TYPE, DayType.NORMAL_DAY.name)
            return try {
                DayType.valueOf(name ?: DayType.NORMAL_DAY.name)
            } catch (_: Exception) {
                DayType.NORMAL_DAY
            }
        }
    }

    private fun getTodayDateString(): String = dayKeyOf(System.currentTimeMillis())

    /**
     * Checks if a new day has arrived, and judges the one that just went.
     *
     * Deterministic and safe even if the app was closed across midnight: the only
     * evidence consulted is what was written during the day itself. It shares
     * the Time Bank's transaction boundary because a claim prices the day and
     * writes its claimed minutes as one unit - a rollover may not slip between
     * those two steps.
     */
    fun checkAndPerformDailyReset(): Boolean = TimeBankLedger.run { evaluateDailyReset() }

    private fun evaluateDailyReset(): Boolean {
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_TRACKED_DATE, null)

        if (lastDate == null) {
            // First run initialization
            prefs.edit()
                .putString(KEY_LAST_TRACKED_DATE, today)
                .putBoolean(KEY_IS_LOCKED, false)
                .putInt(KEY_LAST_WARNING_TIER, 0)
                .putInt(KEY_CURRENT_STREAK, 0)
                .putString(KEY_DAY_TYPE, DayType.NORMAL_DAY.name)
                .putBoolean(KEY_LAST_DAY_FAILED, false)
                .apply()
            return true
        }

        if (lastDate != today) {
            // Date has changed! Evaluate previous day.
            val currentStreak = prefs.getInt(KEY_CURRENT_STREAK, 0)

            // A day is kept by doing one watched thing, not by under-using the
            // allowance. The rule before this one punished the day a person had,
            // and rewarded the one where they simply never opened the app.
            //
            // An unrepaired guard gap disqualifies the quest rather than acting as
            // a second sentence: time earned while nothing was watching is not time
            // ALIVE can stand behind.
            val landed = isQuestCompletedOn(lastDate) && !isGuardGapOpenOn(lastDate)

            // The character grows on a day that was actually kept, and the
            // judgement above is the only place that knows one happened. A point
            // is added, never taken back: the form is a lifetime, and a day that
            // failed costs the condition, not the body.
            if (landed) CharacterGrowthRepository(context).recordPromiseDay(lastDate)

            // The first rollover after this rule exists is not judged, because
            // yesterday was lived under the old one. From the day after, the rule is
            // one the person has already been told - and an unjudged day is a kept
            // one, so nobody starts a run already in debt.
            val judged = prefs.getBoolean(KEY_QUEST_RULE_ANNOUNCED, false)
            val dayFailed = judged && !landed

            val newStreak: Int
            val newDayType: DayType

            if (dayFailed) {
                // Not kept: the streak starts again from zero and tomorrow has no
                // entertainment time to spend.
                newStreak = 0
                newDayType = DayType.ZERO_ENTERTAINMENT_DAY
            } else {
                // Kept. Streak increments by 1 and tomorrow is an ordinary day.
                newStreak = currentStreak + 1
                newDayType = DayType.NORMAL_DAY
            }

            prefs.edit()
                .putString(KEY_LAST_TRACKED_DATE, today)
                .putBoolean(KEY_IS_LOCKED, newDayType == DayType.ZERO_ENTERTAINMENT_DAY)
                .putInt(KEY_LAST_WARNING_TIER, 0)
                .putInt(KEY_CURRENT_STREAK, newStreak)
                .putString(KEY_DAY_TYPE, newDayType.name)
                .putBoolean(KEY_LAST_DAY_FAILED, dayFailed)
                // What this judgement was about, and what it cost, so late
                // evidence for that day can still be honoured today.
                .putString(KEY_LAST_JUDGED_DAY, lastDate)
                .putInt(KEY_STREAK_BEFORE_FAIL, if (dayFailed) currentStreak else 0)
                // Whatever happened, this rollover was lived under the quest rule -
                // so from here on the next one is judged by it too.
                .putBoolean(KEY_QUEST_RULE_ANNOUNCED, true)
                .apply()

            return true
        }

        return false
    }

    fun getCurrentStreak(): Int {
        return prefs.getInt(KEY_CURRENT_STREAK, 0)
    }

    fun getDayType(): DayType {
        val name = prefs.getString(KEY_DAY_TYPE, DayType.NORMAL_DAY.name)
        return try {
            DayType.valueOf(name ?: DayType.NORMAL_DAY.name)
        } catch (_: Exception) {
            DayType.NORMAL_DAY
        }
    }

    fun wasLastDayFailed(): Boolean {
        return prefs.getBoolean(KEY_LAST_DAY_FAILED, false)
    }

    /**
     * Records that the guard was found off during [dayKey].
     *
     * [earnedAtDiscovery] is the Time Bank's lifetime earned total at that
     * moment, and it is the repair receipt: a quest reward is the only thing
     * that ever moves that number, so "finish one quest to clear this" needs no
     * new path into the reward pipeline.
     *
     * Calling this again on the same day keeps the original baseline, so a user
     * who opens ALIVE ten times while the service is off cannot push the goalpost
     * further away.
     */
    @Synchronized
    fun markGuardGap(dayKey: String, earnedAtDiscovery: Long) {
        if (dayKey.isBlank()) return
        if (prefs.getString(KEY_UNGUARDED_DAY, null) == dayKey) return
        prefs.edit()
            .putString(KEY_UNGUARDED_DAY, dayKey)
            .putLong(KEY_GUARD_REPAIR_BASELINE, earnedAtDiscovery)
            .putBoolean(KEY_GUARD_REPAIRED, false)
            .apply()
    }

    /** The day the guard was last found off, or null if it never has been. */
    @Synchronized
    fun unguardedDay(): String? = prefs.getString(KEY_UNGUARDED_DAY, null)

    @Synchronized
    fun isGuardGapOpenOn(dayKey: String): Boolean =
        prefs.getString(KEY_UNGUARDED_DAY, null) == dayKey && !prefs.getBoolean(KEY_GUARD_REPAIRED, false)

    /** The last day a quest paid out, or null if none ever has. */
    @Synchronized
    fun questCompletedDay(): String? = prefs.getString(KEY_QUEST_DONE_DAY, null)

    /** Whether a quest paid out on [dayKey]. This is what keeps tomorrow. */
    @Synchronized
    fun isQuestCompletedOn(dayKey: String): Boolean =
        prefs.getString(KEY_QUEST_DONE_DAY, null) == dayKey

    /**
     * Closes the repair once a quest reward has actually landed.
     *
     * Strictly greater, never equal: the baseline is the total at the moment the
     * gap was noticed, and a reward that has not arrived yet must not count as
     * though it had.
     */
    @Synchronized
    fun repairGuardGapIfEarned(earnedMinutes: Long): Boolean {
        if (prefs.getString(KEY_UNGUARDED_DAY, null) == null) return false
        if (prefs.getBoolean(KEY_GUARD_REPAIRED, false)) return false
        if (earnedMinutes <= prefs.getLong(KEY_GUARD_REPAIR_BASELINE, 0L)) return false
        prefs.edit().putBoolean(KEY_GUARD_REPAIRED, true).apply()
        return true
    }

    fun isDevTestModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DEV_TEST_MODE, false)
    }

    fun setDevTestModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_TEST_MODE, enabled).apply()
    }

    /**
     * The minutes claimed from the Time Bank onto today's allowance.
     *
     * Reads through the stored day, so yesterday's number reads as zero here
     * without anything having cleared it.
     */
    fun claimedMinutesToday(): Long = TimeBankLedger.run {
        if (prefs.getString(KEY_CLAIMED_DAY, null) == getTodayDateString()) {
            prefs.getLong(KEY_CLAIMED_MINUTES, 0L)
        } else {
            0L
        }
    }

    /**
     * Adds [minutes] to the claimed total for [dayKey].
     *
     * The stored day is consulted before adding, so the first claim after
     * midnight starts from zero rather than from yesterday's number. The
     * defaulted day exists for tests - production always claims onto today.
     */
    fun addClaimedMinutesToday(minutes: Long, dayKey: String = getTodayDateString()) {
        TimeBankLedger.run {
            if (minutes <= 0L) return@run
            val current = if (prefs.getString(KEY_CLAIMED_DAY, null) == dayKey) {
                prefs.getLong(KEY_CLAIMED_MINUTES, 0L)
            } else {
                0L
            }
            prefs.edit()
                .putString(KEY_CLAIMED_DAY, dayKey)
                .putLong(KEY_CLAIMED_MINUTES, current + minutes)
                .apply()
        }
    }

    fun getDailyLimitMinutes(): Long {
        if (getDayType() == DayType.ZERO_ENTERTAINMENT_DAY) {
            return 0L
        }
        val base = if (isDevTestModeEnabled()) {
            DEV_TEST_LIMIT_MINUTES
        } else {
            // What last night promised, when a promise is in force at all.
            //
            // This is the whole weight of the nightly contract: the day's base
            // is the number the person chose while calm, and the only thing that
            // raises it is a minute claimed from the Time Bank - which is filled
            // by quests that actually paid out. The version of them that wants
            // more at three in the afternoon pays with time the evening version
            // earned. No lock is added anywhere; only a price.
            nightContract.inForceOn(getTodayDateString())?.allowanceMinutes
                ?: PRODUCTION_LIMIT_MINUTES
        }
        return base + claimedMinutesToday()
    }

    fun isLocked(): Boolean {
        return prefs.getBoolean(KEY_IS_LOCKED, false)
    }

    fun setLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).apply()
    }

    fun getLastWarningTier(): Int {
        return prefs.getInt(KEY_LAST_WARNING_TIER, 0)
    }

    fun setLastWarningTier(tier: Int) {
        prefs.edit().putInt(KEY_LAST_WARNING_TIER, tier).apply()
    }

    /**
     * Testing aid for developer simulation of rollover.
     */
    fun simulateRollover(simulatedLastDate: String) {
        // Only the clock is rewound. Whether yesterday was kept is decided by
        // [checkAndPerformDailyReset] from what actually landed on it, so a
        // simulator that also set the lock flag would be pretending to decide.
        prefs.edit()
            .putString(KEY_LAST_TRACKED_DATE, simulatedLastDate)
            .apply()
        checkAndPerformDailyReset()
    }
}
