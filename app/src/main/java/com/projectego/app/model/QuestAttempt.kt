package com.projectego.app.model

import java.util.UUID

/**
 * Phase 2.3 quest verification lifecycle.
 *
 * Phase 2.3.1: pure model shape only.
 * Phase 2.3.3 Step 1: adds [attemptId] (explicit unique identity) and
 * [linkedFocusSessionId] (explicit correlation to a Focus session),
 * per PHASE_2_3_3_DESIGN.md. Persistence of these two fields (actually
 * saving/restoring them in QuestRepository) and the wiring that creates
 * a correlated Focus session are NOT implemented in this step - that is
 * explicitly deferred to a later Phase 2.3.3 step.
 *
 * No repository, persistence, Focus, OfflineSession, verification, or
 * reward LOGIC beyond what already existed is implemented in this file.
 */

/**
 * How a committed quest attempt will be verified.
 * Digital and Physical are deliberately separate strategies - there is
 * no single generic verifier that claims to prove every kind of activity.
 */
enum class VerificationMethod {
    DIGITAL,
    PHYSICAL
}

/**
 * Lifecycle of a single quest attempt under Phase 2.3:
 *
 *   AVAILABLE -> COMMITTED -> IN_PROGRESS -> PENDING_VERIFICATION -> VERIFIED -> REWARDED
 *
 * Failure can occur from COMMITTED, IN_PROGRESS, or PENDING_VERIFICATION -> FAILED.
 *
 * AVAILABLE represents a quest that has not been committed yet.
 * [QuestAttempt.commit] always starts a new attempt at COMMITTED - nothing
 * in this file enforces which transitions between states are legal; that
 * belongs to a later (repository) step.
 */
enum class QuestAttemptState {
    AVAILABLE,
    COMMITTED,
    IN_PROGRESS,
    PENDING_VERIFICATION,
    VERIFIED,
    REWARDED,
    FAILED
}

/** Outcome of evaluating a quest attempt's evidence. */
enum class VerificationOutcome {
    PASS,
    FAIL
}

/**
 * Record of a verification evaluation.
 *
 * [reason] must describe only what was actually checked (e.g. "Focus
 * session completed with 0 violations" or "Reflection was empty") and
 * never claim stronger proof than the system can actually know - EGO
 * must not claim it knows the user actually studied.
 */
data class VerificationResult(
    val outcome: VerificationOutcome,
    val reason: String,
    val evaluatedTimestamp: Long
)

/**
 * A single quest attempt moving through the Phase 2.3 verification pipeline.
 *
 * [attemptId] is this attempt's own unique, explicit identity (contract:
 * never use a timestamp as identity). It is generated once, at creation,
 * and never changes - two different attempts (even at the same quest)
 * always have different [attemptId] values.
 *
 * [linkedFocusSessionId] is this attempt's explicit correlation to a
 * Focus session's own `focusSessionId` (see FocusSession.kt). It is
 * `null` until a Digital attempt's Focus session has actually been
 * created and linked - that wiring is not implemented in this step.
 *
 * [committedDurationMinutes] is fixed at commit time and is intentionally
 * independent from any linked activity's own duration (e.g. a Focus
 * Session's duration). If a linked Focus Session is later extended via
 * Time Bank, that must NOT change what this attempt considers its
 * committed duration - the two are separate fields by design.
 *
 * [activityStartTimestamp] (already existed before this step) is this
 * attempt's own logical start timestamp - per PHASE_2_3_3_DESIGN.md this
 * is the sole authority for the commitment clock, and is the same
 * logical start moment a linked Focus session must use once that wiring
 * is implemented in a later step.
 */
data class QuestAttempt(
    val attemptId: String = UUID.randomUUID().toString(),
    val questId: String,
    val method: VerificationMethod,
    val state: QuestAttemptState,
    val committedTimestamp: Long,
    val committedDurationMinutes: Long,
    val activityStartTimestamp: Long? = null,
    val activityEndTimestamp: Long? = null,
    val verificationResult: VerificationResult? = null,
    val rewardMinutes: Long? = null,
    val rewardedTimestamp: Long? = null,
    val linkedFocusSessionId: String? = null
) {
    companion object {
        /**
         * Creates a freshly committed attempt. No activity, verification,
         * reward, or Focus correlation has happened yet - all of those
         * fields are null and [state] is [QuestAttemptState.COMMITTED].
         *
         * [now] defaults to the real clock but can be injected for
         * deterministic tests (the same lesson learned from
         * LockStateManager's hardcoded Date() call being untestable).
         *
         * [attemptId] defaults to a fresh [UUID], per the Phase 2.3.3
         * identity contract (never use a timestamp as identity), but can
         * be injected for deterministic tests.
         */
        fun commit(
            questId: String,
            method: VerificationMethod,
            committedDurationMinutes: Long,
            now: Long = System.currentTimeMillis(),
            attemptId: String = UUID.randomUUID().toString()
        ): QuestAttempt {
            return QuestAttempt(
                attemptId = attemptId,
                questId = questId,
                method = method,
                state = QuestAttemptState.COMMITTED,
                committedTimestamp = now,
                committedDurationMinutes = committedDurationMinutes
            )
        }
    }

    /** True only once verification has actually run and passed. */
    val isVerified: Boolean
        get() = verificationResult?.outcome == VerificationOutcome.PASS

    /** True only once a reward has actually been recorded for this attempt. */
    val isRewarded: Boolean
        get() = rewardMinutes != null
}
