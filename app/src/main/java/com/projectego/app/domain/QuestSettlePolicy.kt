package com.projectego.app.domain

import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.VerificationMethod

/**
 * When a mission closes itself because nobody pressed done.
 *
 * The gap this covers is the honest one: a person cleans their room, walks away,
 * and never opens ALIVE again that day - so the one quest they actually did pays
 * nothing, and the app reads as a place where finishing something is not enough
 * because you forgot a button. The grace is deliberately long: a mission that
 * closed the second its time was up would be closing on the clock's schedule
 * rather than the person's.
 *
 * Only a PHYSICAL window is ever closed here. A DIGITAL one is already settled by
 * [DigitalQuestVerifier] against its Focus session, and a second rule reaching the
 * same attempt would mean two answers to whether it was earned.
 *
 * Pure: an attempt and an instant in, a decision out. The alarm and the screen both
 * ask this object the same question, so neither can drift from the other.
 */
object QuestSettlePolicy {

    /** How long after the committed window the person still has to say they are done. */
    const val GRACE_MINUTES = 10L

    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * The instant the window stops being the person's to close, or null when there
     * is no window at all - no attempt, an attempt that never started, or one with
     * no length committed - because nothing can be late for a time that was never
     * set.
     */
    fun dueAt(attempt: QuestAttempt?): Long? {
        val startedAt = attempt?.activityStartTimestamp ?: return null
        if (attempt.committedDurationMinutes <= 0L) return null
        return startedAt + (attempt.committedDurationMinutes + GRACE_MINUTES) * MILLIS_PER_MINUTE
    }

    /** True from the moment the grace runs out, for any attempt that has a window. */
    fun isPastDue(attempt: QuestAttempt?, now: Long): Boolean =
        dueAt(attempt)?.let { now >= it } == true

    /**
     * The whole rule, in one question: is this a running physical window whose
     * grace has run out?
     *
     * `IN_PROGRESS` is part of it, not a detail: an attempt already stamped closed
     * has an end timestamp that measured a real declaration, and re-stamping it
     * would rewrite the moment the person actually finished.
     */
    fun isAutoSettleable(attempt: QuestAttempt, now: Long): Boolean =
        attempt.method == VerificationMethod.PHYSICAL &&
            attempt.state == QuestAttemptState.IN_PROGRESS &&
            isPastDue(attempt, now)

    /**
     * The sentence recorded as the evidence of an auto-settled pass.
     *
     * It says what was checked and what was not, because a verdict that reads like
     * a declaration would claim a moment that never happened.
     */
    fun autoSettleReason(committedMinutes: Long): String =
        "Self-reported after the committed $committedMinutes minutes; " +
            "closed by the clock $GRACE_MINUTES minutes later with no declaration"
}
