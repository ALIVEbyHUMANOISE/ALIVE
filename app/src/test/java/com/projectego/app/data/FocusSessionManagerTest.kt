package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.FocusActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FocusSessionManager]'s Phase 2.3.3 Step 2 persistence
 * of focusSessionId, linkedQuestAttemptId, and violationCount.
 *
 * Run under Robolectric because FocusSessionManager depends on real
 * Context/SharedPreferences, same as QuestRepositoryTest/TimeBankTest.
 *
 * @Config(sdk = [36]) pins the simulated Android version, matching the
 * rest of this project's Robolectric tests.
 *
 * No violation increment/decrement logic, no correlation-linking wiring,
 * and no AccessibilityService integration is tested here - that is
 * deferred to a later Phase 2.3.3 step. This file only verifies that the
 * identity/correlation fields introduced in Step 1 persist correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusSessionManagerTest {

    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var focusSessionManager: FocusSessionManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Isolation: clear only Project EGO's own Focus/time-bank
        // preferences before each test.
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE)
            .edit().clear().apply()

        timeBankRepository = TimeBankRepository(context)
        focusSessionManager = FocusSessionManager(context, timeBankRepository)
    }

    @Test
    fun `a newly started Focus session has a non-blank focusSessionId`() {
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)

        val session = focusSessionManager.getFocusSession()

        assertTrue(session?.focusSessionId?.isNotBlank() == true)
    }

    @Test
    fun `focusSessionId is preserved across manager reconstruction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)
        val originalId = focusSessionManager.getFocusSession()?.focusSessionId

        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedFocusSessionManager = FocusSessionManager(context, recreatedTimeBankRepository)

        assertEquals(originalId, recreatedFocusSessionManager.getFocusSession()?.focusSessionId)
    }

    @Test
    fun `reading the current Focus session multiple times returns the same focusSessionId each time`() {
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)

        val first = focusSessionManager.getFocusSession()?.focusSessionId
        val second = focusSessionManager.getFocusSession()?.focusSessionId

        assertEquals(first, second)
    }

    @Test
    fun `a newly started Focus session has no linked quest attempt by default`() {
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)

        val session = focusSessionManager.getFocusSession()

        assertNull(session?.linkedQuestAttemptId)
    }

    @Test
    fun `linkedQuestAttemptId is preserved across manager reconstruction when present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)
        // Directly inject a linked attempt id, since the linking wiring
        // itself is a later Phase 2.3.3 step - this test only checks
        // that IF the key is present, it is restored correctly.
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit()
            .putString("key_linked_quest_attempt_id", "fixed-attempt-id")
            .apply()

        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedFocusSessionManager = FocusSessionManager(context, recreatedTimeBankRepository)

        assertEquals("fixed-attempt-id", recreatedFocusSessionManager.getFocusSession()?.linkedQuestAttemptId)
    }

    @Test
    fun `a newly started Focus session has violationCount zero`() {
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)

        val session = focusSessionManager.getFocusSession()

        assertEquals(0, session?.violationCount)
    }

    @Test
    fun `violationCount is preserved across manager reconstruction when present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit()
            .putInt("key_violation_count", 2)
            .apply()

        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedFocusSessionManager = FocusSessionManager(context, recreatedTimeBankRepository)

        assertEquals(2, recreatedFocusSessionManager.getFocusSession()?.violationCount)
    }

    @Test
    fun `a legacy Focus session persisted before Step 1 is migrated safely on read`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        // Simulate a session persisted by pre-Step-1 code: only the
        // original 6 keys exist, none of the 3 new keys have ever been
        // written.
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("key_is_active", true)
            .putString("key_activity_type", FocusActivityType.STUDY.name)
            .putString("key_goal", "Legacy goal")
            .putLong("key_duration_minutes", 30L)
            .putLong("key_start_timestamp", now)
            .putLong("key_end_timestamp", now + (30L * 60_000L))
            .apply()

        val migrated = focusSessionManager.getFocusSession()

        assertEquals("Legacy goal", migrated?.goal)
        assertTrue(migrated?.focusSessionId?.isNotBlank() == true)
        assertNull(migrated?.linkedQuestAttemptId)
        assertEquals(0, migrated?.violationCount)
    }

    @Test
    fun `starting a new Focus session clears any previous linkedQuestAttemptId and violationCount`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Simulate leftover state from a previous, quest-linked session
        // that had violations, stored directly to isolate this check from
        // the (not-yet-implemented) linking/violation wiring itself.
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit()
            .putString("key_linked_quest_attempt_id", "stale-attempt-id")
            .putInt("key_violation_count", 3)
            .apply()

        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Fresh goal", 30L)

        val session = focusSessionManager.getFocusSession()
        assertNull(session?.linkedQuestAttemptId)
        assertEquals(0, session?.violationCount)
    }

    // ============================================================
    // Phase 2.3.3 Step 3a: startFocusSession with linkedQuestAttemptId
    // ============================================================

    @Test
    fun `startFocusSession can persist linkedQuestAttemptId when provided`() {
        focusSessionManager.startFocusSession(
            FocusActivityType.STUDY,
            "Test goal",
            30L,
            "fixed-attempt-id-123"
        )

        val session = focusSessionManager.getFocusSession()

        assertEquals("fixed-attempt-id-123", session?.linkedQuestAttemptId)
    }

    @Test
    fun `diagnostic check for linkedQuestAttemptId persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Start first session with link
        focusSessionManager.startFocusSession(
            FocusActivityType.STUDY,
            "First goal",
            30L,
            "first-attempt-id"
        )

        // Check immediately after first call
        val prefs1 = context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
        val rawAfterFirst = prefs1.getString("key_linked_quest_attempt_id", "MISSING")
        assertEquals("first-attempt-id", rawAfterFirst)

        // Start second session without link parameter (should use default null)
        focusSessionManager.startFocusSession(
            FocusActivityType.CODING,
            "Second goal",
            60L
        )

        // Check immediately after second call
        val prefs2 = context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
        val rawAfterSecond = prefs2.getString("key_linked_quest_attempt_id", "MISSING")
        assertEquals("", rawAfterSecond)

        // Now check via getFocusSession()
        val session = focusSessionManager.getFocusSession()
        assertNull(session?.linkedQuestAttemptId)
    }

    @Test
    fun `null linkedQuestAttemptId in startFocusSession does not inherit previous session link`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Start first session with explicit link
        focusSessionManager.startFocusSession(
            FocusActivityType.WRITING,
            "First goal",
            45L,
            "writing-attempt-id"
        )

        // Start second session with explicit null
        focusSessionManager.startFocusSession(
            FocusActivityType.CREATIVE,
            "Second goal",
            90L,
            null
        )

        val session = focusSessionManager.getFocusSession()

        assertEquals(null, session?.linkedQuestAttemptId)
    }

    @Test
    fun `diagnostic check for violationCount persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // First session has violations (set directly for isolation)
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "First", 30L)

        // Check immediately after first call
        val prefs1 = context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
        val violationAfterFirst = prefs1.getInt("key_violation_count", -999)
        assertEquals(0, violationAfterFirst)

        // Manually inject violation
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit()
            .putInt("key_violation_count", 5)
            .apply()

        // Check manual write took effect
        val prefs2 = context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
        val violationAfterManual = prefs2.getInt("key_violation_count", -999)
        assertEquals(5, violationAfterManual)

        // Second session starts fresh
        focusSessionManager.startFocusSession(FocusActivityType.CODING, "Second", 30L)

        // Check immediately after second call
        val prefs3 = context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
        val violationAfterSecond = prefs3.getInt("key_violation_count", -999)
        assertEquals(0, violationAfterSecond)

        // Now check via getFocusSession()
        val session = focusSessionManager.getFocusSession()
        assertEquals(0, session?.violationCount)
    }

    // ============================================================
    // Phase 2.3.3 Step 3a: recordViolation() API testing.
    // ============================================================

    @Test
    fun `recordViolation increments count from 0 to 1`() {
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)

        val recorded = focusSessionManager.recordViolation()

        assertTrue(recorded)
        assertEquals(1, focusSessionManager.getFocusSession()?.violationCount)
    }

    @Test
    fun `recordViolation increments repeated violations 1 → 2 → 3`() {
        focusSessionManager.startFocusSession(FocusActivityType.CODING, "Test goal", 60L)

        focusSessionManager.recordViolation()
        focusSessionManager.recordViolation()
        val afterTwo = focusSessionManager.getFocusSession()?.violationCount

        focusSessionManager.recordViolation()
        val afterThree = focusSessionManager.getFocusSession()?.violationCount

        assertEquals(2, afterTwo)
        assertEquals(3, afterThree)
    }

    @Test
    fun `recordViolation persists across manager reconstruction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)
        focusSessionManager.recordViolation()
        val originalCount = focusSessionManager.getFocusSession()?.violationCount

        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedFocusSessionManager = FocusSessionManager(context, recreatedTimeBankRepository)

        assertEquals(originalCount, recreatedFocusSessionManager.getFocusSession()?.violationCount)
    }

    @Test
    fun `recordViolation safely fails when no session exists`() {
        // Clear any existing session
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit().clear().apply()

        val recorded = focusSessionManager.recordViolation()

        assertFalse(recorded)
        assertNull(focusSessionManager.getFocusSession())
    }

    @Test
    fun `recordViolation does not modify isActive or timestamps`() {
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Test goal", 30L)
        val sessionBefore = focusSessionManager.getFocusSession()

        focusSessionManager.recordViolation()

        val sessionAfter = focusSessionManager.getFocusSession()

        assertEquals(sessionBefore?.isActive, sessionAfter?.isActive)
        assertEquals(sessionBefore?.startTimestamp, sessionAfter?.startTimestamp)
        assertEquals(sessionBefore?.endTimestamp, sessionAfter?.endTimestamp)
        assertEquals(sessionBefore?.durationMinutes, sessionAfter?.durationMinutes)
    }

    // ============================================================
    // Phase 2.3.3 Step 3a: getLastFocusSession() API testing.
    // ============================================================

    @Test
    fun `getLastFocusSession returns active session`() {
        focusSessionManager.startFocusSession(FocusActivityType.STUDY, "Active goal", 30L)

        val session = focusSessionManager.getLastFocusSession()

        assertNotNull(session)
        assertEquals("Active goal", session?.goal)
        assertTrue(session?.isActive == true)
    }

    @Test
    fun `getLastFocusSession returns expired inactive persisted session`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()

        // Manually create an inactive session directly in prefs
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("key_is_active", false)
            .putString("key_activity_type", FocusActivityType.CODING.name)
            .putString("key_goal", "Expired goal")
            .putLong("key_duration_minutes", 60L)
            .putLong("key_start_timestamp", now - (120L * 60_000L))
            .putLong("key_end_timestamp", now - (60L * 60_000L))
            .putString("key_focus_session_id", "expired-session-id")
            .putString("key_linked_quest_attempt_id", "expired-attempt-id")
            .putInt("key_violation_count", 7)
            .apply()

        val session = focusSessionManager.getLastFocusSession()

        assertNotNull(session)
        assertEquals(FocusActivityType.CODING, session?.activityType)
        assertEquals("Expired goal", session?.goal)
        assertEquals(60L, session?.durationMinutes)
        assertEquals("expired-session-id", session?.focusSessionId)
        assertEquals("expired-attempt-id", session?.linkedQuestAttemptId)
        assertEquals(7, session?.violationCount)
        // This should return even though isActive is false
        assertEquals(false, session?.isActive)
    }

    @Test
    fun `getLastFocusSession preserves correlation and violation data for expired session`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()

        // Create session with all fields set, then mark as inactive
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("key_is_active", false)
            .putString("key_activity_type", FocusActivityType.WRITING.name)
            .putString("key_goal", "Stale goal")
            .putLong("key_duration_minutes", 45L)
            .putLong("key_start_timestamp", now - (90L * 60_000L))
            .putLong("key_end_timestamp", now - (45L * 60_000L))
            .putString("key_focus_session_id", "stale-focus-id")
            .putString("key_linked_quest_attempt_id", "stale-attempt-id")
            .putInt("key_violation_count", 9)
            .apply()

        val session = focusSessionManager.getLastFocusSession()

        assertNotNull(session)
        assertEquals("stale-focus-id", session?.focusSessionId)
        assertEquals("stale-attempt-id", session?.linkedQuestAttemptId)
        assertEquals(9, session?.violationCount)
    }

    @Test
    fun `getLastFocusSession returns null when no session was ever started`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit().clear().apply()

        val session = focusSessionManager.getLastFocusSession()

        assertNull(session)
    }
}
