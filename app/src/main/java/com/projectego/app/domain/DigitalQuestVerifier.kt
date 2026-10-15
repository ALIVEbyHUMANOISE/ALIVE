package com.projectego.app.domain

import com.projectego.app.model.DigitalQuestEvidence
import com.projectego.app.model.DigitalQuestVerificationResult
import com.projectego.app.model.VerificationMethod

/**
 * Pure verification logic for Digital Quests.
 *
 * No Android dependencies, no persistence, no mutation. Evaluates a
 * complete immutable evidence snapshot and returns a deterministic decision.
 *
 * Supported quest categories: STUDY, CODING, WRITING, CREATIVE.
 *
 * NOT_READY when committed duration not yet elapsed.
 * FAILED for any other rule violation.
 * Time Bank extensions do NOT affect committedDurationMinutes requirement.
 */
object DigitalQuestVerifier {

    fun verify(evidence: DigitalQuestEvidence?): DigitalQuestVerificationResult {
        if (evidence?.method != VerificationMethod.DIGITAL) {
            return DigitalQuestVerificationResult.failedNonDigital()
        }
        if (evidence.attemptId.isBlank()) {
            return DigitalQuestVerificationResult.failedMissingAttemptId()
        }
        if (evidence.linkedFocusSessionId.isNullOrBlank()) {
            return DigitalQuestVerificationResult.failedMissingLinkedFocusSessionId()
        }
        if (evidence.focusSessionId.isNullOrBlank()) {
            return DigitalQuestVerificationResult.failedMissingFocusSessionId()
        }
        if (evidence.linkedQuestAttemptId == null) {
            return DigitalQuestVerificationResult.failedNoLinkedQuestAttemptId()
        }
        if (evidence.linkedQuestAttemptId != evidence.attemptId) {
            return DigitalQuestVerificationResult.failedCorrelationMismatch()
        }
        if (evidence.linkedFocusSessionId != evidence.focusSessionId) {
            return DigitalQuestVerificationResult.failedFocusIdMismatch()
        }
        if (evidence.activityStartTimestamp == null) {
            return DigitalQuestVerificationResult.failedStartTimestampMismatch()
        }

        // Category/activity type must match. A self-written mission carries its kind
        // as its category, so this one question covers it too - there is no id
        // to special-case anywhere in this file.
        val expected = QuestStartCoordinator.focusActivityTypeFor(evidence.questCategory)
            ?: return DigitalQuestVerificationResult.failedNonDigital()
        if (evidence.focusActivityType != expected) {
            return DigitalQuestVerificationResult.failedActivityTypeMismatch()
        }

        if (evidence.violationCount > 0) {
            return DigitalQuestVerificationResult.failedViolationDetected()
        }
        if (!evidence.wasMonitoringActiveDuringAttempt) {
            return DigitalQuestVerificationResult.failedMonitoringLost()
        }

        // Authoritative clock: activityStartTimestamp
        val elapsedMinutes = (evidence.evaluationTimestamp - evidence.activityStartTimestamp) / 60_000L
        val remainingMinutes = (evidence.committedDurationMinutes - elapsedMinutes).coerceAtLeast(0L)
        if (remainingMinutes > 0) {
            return DigitalQuestVerificationResult.notReady(
                "Remaining commitment: $remainingMinutes minute${if (remainingMinutes > 1) "s" else ""}"
            )
        }
        return DigitalQuestVerificationResult.verified("Committed duration satisfied with 0 violations")
    }
}
