package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.FocusActivityType
import org.junit.Assert.assertEquals
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
}
