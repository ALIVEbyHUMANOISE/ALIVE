package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.FocusSession
import com.projectego.app.model.FocusState
import java.util.UUID

/**
 * One stretch of focus, the way the allowance's arithmetic needs it.
 *
 * The session itself expires; the window is what keeps the promise "time spent
 * focusing is not entertainment" true afterwards. The archive is day-scoped, so
 * it prunes itself the way every other daily fact in the app does.
 */
data class FocusWindow(
    val start: Long,
    val end: Long,
    val activityType: FocusActivityType,
    /** The apps the session named itself, or null for the activity's own list. */
    val approvedOverride: Set<String>? = null
)

class FocusSessionManager(
    context: Context,
    private val timeBankRepository: TimeBankRepository
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_ACTIVE = "key_is_active"
        private const val KEY_ACTIVITY_TYPE = "key_activity_type"
        private const val KEY_GOAL = "key_goal"
        private const val KEY_DURATION_MINUTES = "key_duration_minutes"
        private const val KEY_START_TIMESTAMP = "key_start_timestamp"
        private const val KEY_END_TIMESTAMP = "key_end_timestamp"

        // Phase 2.3.3 Step 2: explicit identity/correlation fields.
        private const val KEY_FOCUS_SESSION_ID = "key_focus_session_id"
        private const val KEY_LINKED_QUEST_ATTEMPT_ID = "key_linked_quest_attempt_id"
        private const val KEY_VIOLATION_COUNT = "key_violation_count"

        // Today's focus windows, for the allowance exemption. The day they
        // belong to is written beside them so the archive prunes itself.
        private const val KEY_WINDOWS_DAY = "key_windows_day"
        private const val KEY_WINDOWS = "key_windows"

        // The apps a session named itself. Nothing writes one any more - the one
        // attempt at a person-chosen list was withdrawn as a cheat surface - but
        // the reader stays, because a session persisted by the build that had it
        // may still be running when this one is installed, and dropping its list
        // mid-session would block the app the person is sitting in.
        private const val KEY_APPROVED_OVERRIDE = "key_approved_override"
        private const val PACKAGE_SEPARATOR = ","

        /**
         * The durations a session may run.
         *
         * The picker offers 30/60/90/120; the three shorter ones are the ones a
         * self-written mission offers, and a mission is a real session like any
         * other. A twenty-minute CREATIVE quest was already in the catalog and
         * could not start before this - the mismatch was here, not in the catalog.
         */
        val VALID_DURATIONS = setOf(10L, 20L, 30L, 60L, 90L, 120L)
    }

    private fun encodeOverride(override: Set<String>?): String? =
        override?.joinToString(PACKAGE_SEPARATOR)

    private fun decodeOverride(raw: String?): Set<String>? =
        raw?.split(PACKAGE_SEPARATOR)?.filter { it.isNotBlank() }?.toSet()

    @Synchronized
    fun getFocusSession(): FocusSession? {
        val isActive = prefs.getBoolean(KEY_IS_ACTIVE, false)
        if (!isActive) return null

        val endTimestamp = prefs.getLong(KEY_END_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()

        // Check if session has expired naturally
        if (now >= endTimestamp) {
            // Auto mark inactive
            prefs.edit().putBoolean(KEY_IS_ACTIVE, false).apply()
            return null
        }

        val activityTypeName = prefs.getString(KEY_ACTIVITY_TYPE, FocusActivityType.STUDY.name)
        val activityType = try {
            FocusActivityType.valueOf(activityTypeName ?: FocusActivityType.STUDY.name)
        } catch (_: Exception) {
            FocusActivityType.STUDY
        }

        val goal = prefs.getString(KEY_GOAL, "") ?: ""
        val durationMinutes = prefs.getLong(KEY_DURATION_MINUTES, 0L)
        val startTimestamp = prefs.getLong(KEY_START_TIMESTAMP, 0L)

        // Phase 2.3.3 Step 2: focusSessionId falls back to a freshly
        // generated UUID only when reading a legacy session persisted
        // before Step 1 (the key was never written, so it reads back
        // null here). A session persisted by the current
        // startFocusSession()/startTestFocusSession() always has this key
        // written at creation time and is restored as-is, never
        // regenerated.
        val focusSessionId = prefs.getString(KEY_FOCUS_SESSION_ID, null) ?: UUID.randomUUID().toString()
        val rawLinkedId = prefs.getString(KEY_LINKED_QUEST_ATTEMPT_ID, "")
        val linkedQuestAttemptId = if (rawLinkedId.isNullOrEmpty()) null else rawLinkedId
        val violationCount = prefs.getInt(KEY_VIOLATION_COUNT, 0)

        return FocusSession(
            activityType = activityType,
            goal = goal,
            durationMinutes = durationMinutes,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            isActive = true,
            focusSessionId = focusSessionId,
            linkedQuestAttemptId = linkedQuestAttemptId,
            violationCount = violationCount,
            approvedOverride = decodeOverride(prefs.getString(KEY_APPROVED_OVERRIDE, null))
        )
    }

    fun getFocusState(): FocusState {
        val session = getFocusSession()
        return if (session != null && session.isActive) {
            FocusState.ACTIVE
        } else {
            FocusState.NO_FOCUS
        }
    }

    /**
     * Today's focus windows, oldest first.
     *
     * The archive is what keeps the exemption true after a session has ended:
     * the allowance's arithmetic needs the *window*, and a session that is over
     * is still a window of time the person did not spend on entertainment.
     */
    @Synchronized
    fun getTodayWindows(): List<FocusWindow> {
        val today = LockStateManager.dayKeyOf(System.currentTimeMillis())
        val storedDay = prefs.getString(KEY_WINDOWS_DAY, null)
        if (storedDay != today) return emptyList()
        return deserializeWindows(prefs.getString(KEY_WINDOWS, null))
    }

    @Synchronized
    private fun recordWindow(
        start: Long,
        end: Long,
        activityType: FocusActivityType,
        approvedOverride: Set<String>? = null
    ) {
        val today = LockStateManager.dayKeyOf(start)
        val storedDay = prefs.getString(KEY_WINDOWS_DAY, null)
        val existing = if (storedDay == today) deserializeWindows(prefs.getString(KEY_WINDOWS, null)) else emptyList()
        // A session that was replaced is truncated at the moment its replacement
        // began, so two windows can never overlap and double-count an exemption.
        val truncated = existing.map { it.copy(end = minOf(it.end, start)) }
        val updated = (truncated + FocusWindow(start, end, activityType, approvedOverride)).sortedBy { it.start }
        prefs.edit()
            .putString(KEY_WINDOWS_DAY, today)
            .putString(KEY_WINDOWS, serializeWindows(updated))
            .apply()
    }

    @Synchronized
    private fun extendWindow(start: Long, newEnd: Long) {
        val today = LockStateManager.dayKeyOf(start)
        val storedDay = prefs.getString(KEY_WINDOWS_DAY, null)
        if (storedDay != today) return
        val updated = deserializeWindows(prefs.getString(KEY_WINDOWS, null))
            .map { if (it.start == start) it.copy(end = newEnd) else it }
        prefs.edit().putString(KEY_WINDOWS, serializeWindows(updated)).apply()
    }

    private fun serializeWindows(windows: List<FocusWindow>): String =
        FocusWindowCodec.encode(windows)

    /** Both shapes: the three-field record this app has always written, and the
     *  four-field one a self-written mission adds its app list to. */
    private fun deserializeWindows(raw: String?): List<FocusWindow> =
        FocusWindowCodec.decode(raw)

    /**
     * Starts a new Focus Session.
     * Allowed durations are [VALID_DURATIONS].
     * Rejects if a session is already active.
     *
     * @param linkedQuestAttemptId optional ID of a QuestAttempt to correlate this session with
     *        (null for ordinary standalone Focus sessions unrelated to any quest)
     * @param approvedOverride the apps this session allows, when the person named
     *        them; null - the default - leaves the activity's own list in charge.
     */
    @Synchronized
    fun startFocusSession(
        activityType: FocusActivityType,
        goal: String,
        durationMinutes: Long,
        linkedQuestAttemptId: String? = null,
        approvedOverride: Set<String>? = null
    ): Boolean {
        if (durationMinutes !in VALID_DURATIONS) {
            return false
        }

        // If an active session exists, deactivate it first so the new session
        // fully replaces it (clearing all prior linkage and violation counts).
        val existingSession = getFocusSession()
        if (existingSession != null) {
            // We'll reactivate below anyway, so just ensure we overwrite all fields
        }

        val now = System.currentTimeMillis()
        val end = now + (durationMinutes * 60 * 1000)

        val edit = prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putString(KEY_ACTIVITY_TYPE, activityType.name)
            .putString(KEY_GOAL, goal.trim())
            .putLong(KEY_DURATION_MINUTES, durationMinutes)
            .putLong(KEY_START_TIMESTAMP, now)
            .putLong(KEY_END_TIMESTAMP, end)
            .putString(KEY_FOCUS_SESSION_ID, UUID.randomUUID().toString())
            .putInt(KEY_VIOLATION_COUNT, 0)

        // Use empty string for null to ensure the key is explicitly overwritten
        // Reading code converts empty string back to null
        edit.putString(KEY_LINKED_QUEST_ATTEMPT_ID, linkedQuestAttemptId ?: "")

        // Absent means the catalog's list; an empty string means "none allowed".
        // putString(key, null) removes the key, which is what a catalog session
        // must look like - including one that replaced a mission's session.
        edit.putString(KEY_APPROVED_OVERRIDE, encodeOverride(approvedOverride))

        edit.apply()

        // The allowance's arithmetic keeps the window, not the session: from
        // here on, this stretch of time is the stretch that does not charge.
        recordWindow(now, end, activityType, approvedOverride)

        return true
    }

    /**
     * Extends an already active Focus Session using minutes from Time Bank.
     * Only works while Focus is ACTIVE.
     * Prevents spending more than available Time Bank balance.
     */
    @Synchronized
    fun extendFocusWithTimeBank(minutes: Long): Boolean {
        if (minutes <= 0) return false

        val currentSession = getFocusSession() ?: return false
        if (!currentSession.isActive) return false

        // Spend from Time Bank
        val spentSuccessfully = timeBankRepository.spendMinutes(minutes)
        if (!spentSuccessfully) {
            return false
        }

        val newDuration = currentSession.durationMinutes + minutes
        val newEndTimestamp = currentSession.endTimestamp + (minutes * 60 * 1000)

        prefs.edit()
            .putLong(KEY_DURATION_MINUTES, newDuration)
            .putLong(KEY_END_TIMESTAMP, newEndTimestamp)
            .apply()

        // An extended session is a longer window of time that does not charge.
        extendWindow(currentSession.startTimestamp, newEndTimestamp)

        return true
    }

    /**
     * Record a confirmed violation for the current Focus session.
     *
     * Only operates on the currently persisted FocusSession.
     * Returns false when there is no persisted session to update.
     * Does not create a new FocusSession, modify isActive, or alter timestamps/duration.
     */
    @Synchronized
    fun recordViolation(): Boolean {
        val current = getFocusSession() ?: return false

        val newViolationCount = current.violationCount + 1
        prefs.edit().putInt(KEY_VIOLATION_COUNT, newViolationCount).apply()

        return true
    }

    /**
     * Reads the last persisted Focus session regardless of active status.
     *
     * Unlike getFocusSession(), this returns the complete session data
     * even if expired or inactive — useful for verification that needs
     * to inspect historical Focus sessions.
     *
     * Preserves all fields: focusSessionId, linkedQuestAttemptId,
     * violationCount, activityType, goal, duration, timestamps.
     */
    @Synchronized
    fun getLastFocusSession(): FocusSession? {
        // Check if any session data has ever been persisted
        val activityTypeName = prefs.getString(KEY_ACTIVITY_TYPE, null)
        if (activityTypeName == null) {
            return null
        }

        val activityType = try {
            FocusActivityType.valueOf(activityTypeName)
        } catch (_: Exception) {
            FocusActivityType.STUDY
        }

        val goal = prefs.getString(KEY_GOAL, "") ?: ""
        val durationMinutes = prefs.getLong(KEY_DURATION_MINUTES, 0L)
        val startTimestamp = prefs.getLong(KEY_START_TIMESTAMP, 0L)
        val endTimestamp = prefs.getLong(KEY_END_TIMESTAMP, 0L)

        val isActive = prefs.getBoolean(KEY_IS_ACTIVE, false)

        val focusSessionId = prefs.getString(KEY_FOCUS_SESSION_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        val linkedQuestAttemptIdRaw = prefs.getString(KEY_LINKED_QUEST_ATTEMPT_ID, "")
        val linkedQuestAttemptId = if (linkedQuestAttemptIdRaw.isNullOrEmpty()) null else linkedQuestAttemptIdRaw
        val violationCount = prefs.getInt(KEY_VIOLATION_COUNT, 0)

        return FocusSession(
            activityType = activityType,
            goal = goal,
            durationMinutes = durationMinutes,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            isActive = isActive,
            focusSessionId = focusSessionId,
            linkedQuestAttemptId = linkedQuestAttemptId,
            violationCount = violationCount,
            approvedOverride = decodeOverride(prefs.getString(KEY_APPROVED_OVERRIDE, null))
        )
    }

    /**
     * Developer-only testing method to simulate short duration.
     */
    fun startTestFocusSession(
        activityType: FocusActivityType,
        goal: String,
        durationSeconds: Long,
        linkedQuestAttemptId: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        val end = now + (durationSeconds * 1000)

        val edit = prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putString(KEY_ACTIVITY_TYPE, activityType.name)
            .putString(KEY_GOAL, goal.trim())
            .putLong(KEY_DURATION_MINUTES, 1L)
            .putLong(KEY_START_TIMESTAMP, now)
            .putLong(KEY_END_TIMESTAMP, end)
            .putString(KEY_FOCUS_SESSION_ID, UUID.randomUUID().toString())
            .putInt(KEY_VIOLATION_COUNT, 0)

        if (linkedQuestAttemptId == null) {
            edit.remove(KEY_LINKED_QUEST_ATTEMPT_ID)
        } else {
            edit.putString(KEY_LINKED_QUEST_ATTEMPT_ID, linkedQuestAttemptId)
        }

        edit.apply()
        return true
    }
}
