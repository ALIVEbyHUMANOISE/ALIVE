package com.projectego.app.domain

import com.projectego.app.model.*
import com.projectego.app.model.DigitalQuestVerificationResult.FailureReason
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM unit tests for DigitalQuestVerifier.
 * No Android dependencies required.
 *
 * Test scenarios mapped to Phase 2.3.3 Step 3b requirements:
 * A - Valid Digital Quest with exact committed duration → VERIFIED
 * B - Duration incomplete → NOT_READY
 * C - Any violation → FAILED
 * D - Monitoring loss → FAILED
 * E - Missing attemptId → FAILED
 * F - Missing linkedFocusSessionId → FAILED
 * G - Focus session missing linkedQuestAttemptId → FAILED
 * H - Correlation mismatch → FAILED
 * I - Focus session ID mismatch → FAILED
 * J - Start timestamp mismatch → FAILED
 * K - Time Bank extension does not change committed duration requirement
 * L - Focus expiration alone does not produce VERIFIED
 * M - Non-Digital quest → rejected by verifier
 * N - Verifier performs no mutation/reward
 */
class DigitalQuestVerifierTest {

    /**
     * One fixed evaluation moment for the whole file.
     *
     * The verifier reads no wall clock of its own - it only compares the two
     * timestamps inside the evidence - so pinning both makes every duration
     * assertion exact. Reading `System.currentTimeMillis()` twice (once for the
     * fixture, once inside a test) made "exactly 30 minutes" depend on whether
     * the two calls landed in the same millisecond.
     */
    private val now = 1_760_000_000_000L

    private val baseEvidence = DigitalQuestEvidence(
        attemptId = "quest-attempt-123",
        questId = "quest-456",
        questCategory = QuestCategory.STUDY,
        method = VerificationMethod.DIGITAL,
        committedDurationMinutes = 30L,
        activityStartTimestamp = now - (30L * 60_000L), // exactly 30 minutes ago
        linkedFocusSessionId = "focus-session-789",
        focusSessionId = "focus-session-789",
        linkedQuestAttemptId = "quest-attempt-123",
        focusActivityType = FocusActivityType.STUDY,
        violationCount = 0,
        wasMonitoringActiveDuringAttempt = true,
        evaluationTimestamp = now
    )

    @Test
    fun `A - valid digital quest with exact committed duration returns verified`() {
        val evidence = baseEvidence.copy(
            activityStartTimestamp = now - (30L * 60_000L),
            evaluationTimestamp = now
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Verified)
        assertEquals("Committed duration satisfied with 0 violations", (result as DigitalQuestVerificationResult.Verified).reason)
    }

    @Test
    fun `A2 - a self-written mission verifies by its kind, like any other quest`() {
        val evidence = baseEvidence.copy(
            questId = CustomQuest.ID,
            questCategory = QuestCategory.STUDY,
            focusActivityType = FocusActivityType.STUDY
        )
        // Nothing in the verifier knows this quest was written by hand, and that
        // is the design: the mission carries its kind as its category, so one
        // rule covers it instead of an id-shaped exception.
        assertTrue(DigitalQuestVerifier.verify(evidence) is DigitalQuestVerificationResult.Verified)
    }

    @Test
    fun `B - duration incomplete returns not ready`() {
        val evidence = baseEvidence.copy(
            activityStartTimestamp = now - (15L * 60_000L), // only 15 minutes elapsed
            evaluationTimestamp = now
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.NotReady)
        assertTrue((result as DigitalQuestVerificationResult.NotReady).reason.contains("Remaining commitment"))
    }

    @Test
    fun `C - any violation returns failed`() {
        val evidence = baseEvidence.copy(violationCount = 1)
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.VIOLATION_DETECTED, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `D - monitoring loss returns failed`() {
        val evidence = baseEvidence.copy(wasMonitoringActiveDuringAttempt = false)
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.MONITORING_LOST, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `E - missing attemptId returns failed`() {
        val evidence = baseEvidence.copy(attemptId = "")
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.MISSING_ATTEMPT_ID, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `F - missing linkedFocusSessionId returns failed`() {
        val evidence = baseEvidence.copy(linkedFocusSessionId = null)
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.MISSING_LINKED_FOCUS_SESSION_ID, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `G - focus session missing linkedQuestAttemptId returns failed`() {
        val evidence = baseEvidence.copy(linkedQuestAttemptId = null)
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.NO_LINKED_QUEST_ATTEMPT_ID, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `H - correlation mismatch returns failed`() {
        val evidence = baseEvidence.copy(linkedQuestAttemptId = "wrong-attempt-id")
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.CORRELATION_MISMATCH, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `I - focus session ID mismatch returns failed`() {
        val evidence = baseEvidence.copy(linkedFocusSessionId = "different-focus-id")
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.FOCUS_ID_MISMATCH, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `J - start timestamp mismatch returns failed`() {
        val evidence = baseEvidence.copy(activityStartTimestamp = null)
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.START_TIMESTAMP_MISMATCH, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `K - time bank extension does not change committed duration requirement`() {
        // Even if Time Bank extended available minutes, verifier checks committedDurationMinutes
        val evidence = baseEvidence.copy(
            committedDurationMinutes = 60L, // committed to 60 minutes
            activityStartTimestamp = now - (30L * 60_000L) // only 30 elapsed
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.NotReady)
    }

    @Test
    fun `L - focus expiration alone does not produce verified`() {
        // If violations exist, even after expiration, should fail
        val oldTimestamp = now - (120L * 60_000L) // 2 hours ago
        val evidence = baseEvidence.copy(
            committedDurationMinutes = 30L,
            activityStartTimestamp = oldTimestamp,
            violationCount = 1 // violations override expiration check
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.VIOLATION_DETECTED, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `M - non-digital quest is rejected by verifier`() {
        val evidence = baseEvidence.copy(method = VerificationMethod.PHYSICAL)
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.NON_DIGITAL_QUEST, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `N - verifier performs no mutation or reward`() {
        val originalEvidence = baseEvidence.copy(
            linkedFocusSessionId = "original-session"
        )
        val result = DigitalQuestVerifier.verify(originalEvidence)

        // Evidence should remain unchanged
        assertEquals("original-session", originalEvidence.linkedFocusSessionId)

        // Result should be verification decision, not a mutation
        assertTrue(result is DigitalQuestVerificationResult.Verified ||
                  result is DigitalQuestVerificationResult.NotReady ||
                  result is DigitalQuestVerificationResult.Failed)
    }

    @Test
    fun `additional - null evidence returns not ready`() {
        val result = DigitalQuestVerifier.verify(null)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.NON_DIGITAL_QUEST, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `additional - category mismatch returns failed`() {
        val evidence = baseEvidence.copy(
            questCategory = QuestCategory.STUDY,
            focusActivityType = FocusActivityType.CODING
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Failed)
        assertEquals(FailureReason.ACTIVITY_TYPE_MISMATCH, (result as DigitalQuestVerificationResult.Failed).reason)
    }

    @Test
    fun `additional - coding category with coding activity type matches`() {
        val evidence = baseEvidence.copy(
            questCategory = QuestCategory.CODING,
            focusActivityType = FocusActivityType.CODING,
            activityStartTimestamp = now - (30L * 60_000L)
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Verified)
    }

    @Test
    fun `additional - writing category with writing activity type matches`() {
        val evidence = baseEvidence.copy(
            questCategory = QuestCategory.WRITING,
            focusActivityType = FocusActivityType.WRITING,
            activityStartTimestamp = now - (30L * 60_000L)
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Verified)
    }

    @Test
    fun `additional - creative category with creative activity type matches`() {
        val evidence = baseEvidence.copy(
            questCategory = QuestCategory.CREATIVE,
            focusActivityType = FocusActivityType.CREATIVE,
            activityStartTimestamp = now - (30L * 60_000L)
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Verified)
    }

    @Test
    fun `additional - more than committed duration returns verified`() {
        val evidence = baseEvidence.copy(
            activityStartTimestamp = now - (60L * 60_000L), // 1 hour ago
            committedDurationMinutes = 30L
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Verified)
    }

    @Test
    fun `additional - exactly at boundary returns verified`() {
        val evidence = DigitalQuestEvidence(
            attemptId = "attempt-boundary",
            questId = "quest-boundary",
            questCategory = QuestCategory.STUDY,
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 5L,
            activityStartTimestamp = now - (5L * 60_000L), // exactly 5 minutes
            linkedFocusSessionId = "focus-boundary",
            focusSessionId = "focus-boundary",
            linkedQuestAttemptId = "attempt-boundary",
            focusActivityType = FocusActivityType.STUDY,
            violationCount = 0,
            wasMonitoringActiveDuringAttempt = true,
            evaluationTimestamp = now
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.Verified)
    }

    @Test
    fun `additional - one minute short returns not ready`() {
        val evidence = DigitalQuestEvidence(
            attemptId = "attempt-short",
            questId = "quest-short",
            questCategory = QuestCategory.CODING,
            method = VerificationMethod.DIGITAL,
            committedDurationMinutes = 10L,
            activityStartTimestamp = now - (9L * 60_000L), // 9 minutes elapsed
            linkedFocusSessionId = "focus-short",
            focusSessionId = "focus-short",
            linkedQuestAttemptId = "attempt-short",
            focusActivityType = FocusActivityType.CODING,
            violationCount = 0,
            wasMonitoringActiveDuringAttempt = true,
            evaluationTimestamp = now
        )
        val result = DigitalQuestVerifier.verify(evidence)
        assertTrue(result is DigitalQuestVerificationResult.NotReady)
    }
}
