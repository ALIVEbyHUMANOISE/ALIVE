package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.FocusSession
import com.projectego.app.model.FocusState
import java.util.UUID

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
    }

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
        val linkedQuestAttemptId = prefs.getString(KEY_LINKED_QUEST_ATTEMPT_ID, null)
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
            violationCount = violationCount
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
     * Starts a new Focus Session.
     * Allowed durations: 30, 60, 90, 120 minutes.
     * Rejects if a session is already active.
     */
    @Synchronized
    fun startFocusSession(
        activityType: FocusActivityType,
        goal: String,
        durationMinutes: Long
    ): Boolean {
        // Validate duration (max 120 minutes in V1)
        if (durationMinutes !in setOf(30L, 60L, 90L, 120L)) {
            return false
        }

        // Validate no active session
        val current = getFocusSession()
        if (current != null) {
            return false
        }

        val now = System.currentTimeMillis()
        val end = now + (durationMinutes * 60 * 1000)

        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putString(KEY_ACTIVITY_TYPE, activityType.name)
            .putString(KEY_GOAL, goal.trim())
            .putLong(KEY_DURATION_MINUTES, durationMinutes)
            .putLong(KEY_START_TIMESTAMP, now)
            .putLong(KEY_END_TIMESTAMP, end)
            .putString(KEY_FOCUS_SESSION_ID, UUID.randomUUID().toString())
            .remove(KEY_LINKED_QUEST_ATTEMPT_ID)
            .putInt(KEY_VIOLATION_COUNT, 0)
            .apply()

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

        return true
    }

    /**
     * Developer-only testing method to simulate short duration.
     */
    fun startTestFocusSession(
        activityType: FocusActivityType,
        goal: String,
        durationSeconds: Long
    ): Boolean {
        val now = System.currentTimeMillis()
        val end = now + (durationSeconds * 1000)

        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putString(KEY_ACTIVITY_TYPE, activityType.name)
            .putString(KEY_GOAL, goal.trim())
            .putLong(KEY_DURATION_MINUTES, 1L)
            .putLong(KEY_START_TIMESTAMP, now)
            .putLong(KEY_END_TIMESTAMP, end)
            .putString(KEY_FOCUS_SESSION_ID, UUID.randomUUID().toString())
            .remove(KEY_LINKED_QUEST_ATTEMPT_ID)
            .putInt(KEY_VIOLATION_COUNT, 0)
            .apply()
        return true
    }
}
