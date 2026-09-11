package com.projectego.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Phase 2.3 quest attempt model (QuestAttempt,
 * QuestAttemptState, VerificationMethod, VerificationResult).
 *
 * Pure Kotlin, zero Android dependency - plain JVM unit tests, same style
 * as TimeBankTest.kt, no Robolectric needed.
 *
 * No repository, persistence, Focus, OfflineSession, verification, or
 * reward LOGIC exists yet - these tests only check the model's shape.
 */
class QuestAttemptTest {

    @Test
    fun `a freshly committed attempt is in COMMITTED state`() {
        val attempt = QuestAttempt.commit(
            questId = "quest_study_30",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 30L
        )

        assertEquals(QuestAttemptState.COMMITTED, attempt.state)
    }

    @Test
    fun `a freshly committed attempt is not verified or rewarded by default`() {
        val attempt = QuestAttempt.commit(
            questId = "quest_study_30",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 30L
        )

        assertFalse(attempt.isVerified)
        assertFalse(attempt.isRewarded)
        assertNull(attempt.verificationResult)
        assertNull(attempt.rewardMinutes)
        assertNull(attempt.rewardedTimestamp)
        assertNull(attempt.activityStartTimestamp)
        assertNull(attempt.activityEndTimestamp)
    }

    @Test
    fun `a committed attempt can use the DIGITAL method`() {
        val attempt = QuestAttempt.commit(
            questId = "quest_study_30",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 30L
        )

        assertEquals(VerificationMethod.DIGITAL, attempt.method)
    }

    @Test
    fun `a committed attempt can use the PHYSICAL method`() {
        val attempt = QuestAttempt.commit(
            questId = "quest_read_30",
            method = VerificationMethod.PHYSICAL,
            committedDurationMinutes = 30L
        )

        assertEquals(VerificationMethod.PHYSICAL, attempt.method)
    }

    @Test
    fun `committed duration is independent of activity timestamps`() {
        val attempt = QuestAttempt.commit(
            questId = "quest_study_30",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 30L
        )

        // Simulate the activity later reporting different start/end
        // timestamps (e.g. what a linked Focus Session would eventually
        // carry, including after a Time Bank extension) -
        // committedDurationMinutes must not be derived from or altered
        // by that.
        val withActivity = attempt.copy(
            activityStartTimestamp = 1_000L,
            activityEndTimestamp = 1_000L + (45L * 60_000L) // a 45-minute span
        )

        assertEquals(30L, withActivity.committedDurationMinutes)
    }

    @Test
    fun `committed timestamp uses the injected clock, not a hidden system call`() {
        val fixedNow = 123_456_789L

        val attempt = QuestAttempt.commit(
            questId = "quest_walk_20",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 20L,
            now = fixedNow
        )

        assertEquals(fixedNow, attempt.committedTimestamp)
    }

    @Test
    fun `the model can represent every state in the Phase 2_3 pipeline`() {
        val base = QuestAttempt.commit(
            questId = "quest_study_30",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 30L
        )

        val allStates = listOf(
            QuestAttemptState.AVAILABLE,
            QuestAttemptState.COMMITTED,
            QuestAttemptState.IN_PROGRESS,
            QuestAttemptState.PENDING_VERIFICATION,
            QuestAttemptState.VERIFIED,
            QuestAttemptState.REWARDED,
            QuestAttemptState.FAILED
        )

        for (state in allStates) {
            val attempt = base.copy(state = state)
            assertEquals(state, attempt.state)
        }
    }

    @Test
    fun `isVerified is true only when verification result outcome is PASS`() {
        val attempt = QuestAttempt.commit(
            questId = "quest_study_30",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 30L
        )

        val failedResult = attempt.copy(
            verificationResult = VerificationResult(
                outcome = VerificationOutcome.FAIL,
                reason = "Focus session was interrupted",
                evaluatedTimestamp = 2_000L
            )
        )
        val passedResult = attempt.copy(
            verificationResult = VerificationResult(
                outcome = VerificationOutcome.PASS,
                reason = "Focus session completed with 0 violations",
                evaluatedTimestamp = 3_000L
            )
        )

        assertFalse(failedResult.isVerified)
        assertTrue(passedResult.isVerified)
    }

    @Test
    fun `isRewarded is true only once reward minutes are recorded`() {
        val attempt = QuestAttempt.commit(
            questId = "quest_study_30",
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 30L
        )

        assertFalse(attempt.isRewarded)

        val rewarded = attempt.copy(rewardMinutes = 20L, rewardedTimestamp = 4_000L)

        assertTrue(rewarded.isRewarded)
    }
}
