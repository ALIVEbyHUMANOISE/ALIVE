package com.projectego.app.model

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Phase 2.3.3 Step 1 additions to [FocusSession]:
 * focusSessionId, linkedQuestAttemptId, violationCount.
 *
 * Pure Kotlin, zero Android dependency - plain JVM unit tests, same
 * style as TimeBankTest.kt/QuestAttemptTest.kt. No FocusSessionManager
 * persistence, correlation wiring, or violation increment/decrement
 * behavior is tested here - that is deferred to a later Phase 2.3.3 step.
 */
class FocusSessionTest {

    private fun sampleSession(
        focusSessionId: String = UUID.randomUUID().toString(),
        linkedQuestAttemptId: String? = null
    ): FocusSession {
        val start = 1_000_000L
        return FocusSession(
            activityType = FocusActivityType.STUDY,
            goal = "Test goal",
            durationMinutes = 30L,
            startTimestamp = start,
            endTimestamp = start + (30L * 60_000L),
            focusSessionId = focusSessionId,
            linkedQuestAttemptId = linkedQuestAttemptId
        )
    }

    @Test
    fun `a FocusSession has a non-blank focusSessionId`() {
        val session = sampleSession()

        assertTrue(session.focusSessionId.isNotBlank())
    }

    @Test
    fun `two separately constructed sessions get different default focusSessionId values`() {
        val first = FocusSession(
            activityType = FocusActivityType.STUDY,
            goal = "Goal A",
            durationMinutes = 30L,
            startTimestamp = 1_000L,
            endTimestamp = 2_000L
        )
        val second = FocusSession(
            activityType = FocusActivityType.STUDY,
            goal = "Goal B",
            durationMinutes = 30L,
            startTimestamp = 1_000L,
            endTimestamp = 2_000L
        )

        assertNotEquals(first.focusSessionId, second.focusSessionId)
    }

    @Test
    fun `linkedQuestAttemptId is null by default for an ordinary Focus session`() {
        val session = sampleSession()

        assertNull(session.linkedQuestAttemptId)
    }

    @Test
    fun `linkedQuestAttemptId can be stored for a quest-linked Focus session`() {
        val session = sampleSession(linkedQuestAttemptId = "some-attempt-id")

        assertEquals("some-attempt-id", session.linkedQuestAttemptId)
    }

    @Test
    fun `violationCount defaults to zero`() {
        val session = sampleSession()

        assertEquals(0, session.violationCount)
    }

    @Test
    fun `existing FocusSession fields are unaffected by the new identity fields`() {
        val session = sampleSession()

        assertEquals(FocusActivityType.STUDY, session.activityType)
        assertEquals("Test goal", session.goal)
        assertEquals(30L, session.durationMinutes)
        assertTrue(session.isActive)
    }
}
