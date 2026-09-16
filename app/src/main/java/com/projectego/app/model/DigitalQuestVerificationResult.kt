package com.projectego.app.model

/**
 * Sealed hierarchy representing Digital Quest verification decisions.
 * No Android dependencies. Represents final decision of DigitalQuestVerifier evaluation.
 */
sealed class DigitalQuestVerificationResult {
    data class Verified(val reason: String) : DigitalQuestVerificationResult()
    data class NotReady(val reason: String) : DigitalQuestVerificationResult()
    data class Failed(val reason: FailureReason) : DigitalQuestVerificationResult()

    enum class FailureReason {
        NON_DIGITAL_QUEST,
        MISSING_ATTEMPT_ID,
        MISSING_LINKED_FOCUS_SESSION_ID,
        MISSING_FOCUS_SESSION_ID,
        NO_LINKED_QUEST_ATTEMPT_ID,
        CORRELATION_MISMATCH,
        FOCUS_ID_MISMATCH,
        START_TIMESTAMP_MISMATCH,
        VIOLATION_DETECTED,
        MONITORING_LOST,
        ACTIVITY_TYPE_MISMATCH
    }

    companion object {
        fun verified(reason: String): DigitalQuestVerificationResult = Verified(reason)
        fun notReady(reason: String): DigitalQuestVerificationResult = NotReady(reason)
        fun failed(reason: FailureReason): DigitalQuestVerificationResult = Failed(reason)
        fun failedNonDigital(): DigitalQuestVerificationResult = Failed(FailureReason.NON_DIGITAL_QUEST)
        fun failedMissingAttemptId(): DigitalQuestVerificationResult = Failed(FailureReason.MISSING_ATTEMPT_ID)
        fun failedMissingLinkedFocusSessionId(): DigitalQuestVerificationResult = Failed(FailureReason.MISSING_LINKED_FOCUS_SESSION_ID)
        fun failedMissingFocusSessionId(): DigitalQuestVerificationResult = Failed(FailureReason.MISSING_FOCUS_SESSION_ID)
        fun failedNoLinkedQuestAttemptId(): DigitalQuestVerificationResult = Failed(FailureReason.NO_LINKED_QUEST_ATTEMPT_ID)
        fun failedCorrelationMismatch(): DigitalQuestVerificationResult = Failed(FailureReason.CORRELATION_MISMATCH)
        fun failedFocusIdMismatch(): DigitalQuestVerificationResult = Failed(FailureReason.FOCUS_ID_MISMATCH)
        fun failedStartTimestampMismatch(): DigitalQuestVerificationResult = Failed(FailureReason.START_TIMESTAMP_MISMATCH)
        fun failedViolationDetected(): DigitalQuestVerificationResult = Failed(FailureReason.VIOLATION_DETECTED)
        fun failedMonitoringLost(): DigitalQuestVerificationResult = Failed(FailureReason.MONITORING_LOST)
        fun failedActivityTypeMismatch(): DigitalQuestVerificationResult = Failed(FailureReason.ACTIVITY_TYPE_MISMATCH)
    }
}
