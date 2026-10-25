package com.projectego.app.domain

import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.VerificationMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deadline of a mission nobody closed.
 *
 * The grace is the whole design here: too short and ALIVE answers for the person
 * before they have had the chance to say they are still at it, too long and the
 * reward lands so late it reads as never having landed. These tests pin the
 * boundary at exactly committed + ten, and pin which attempts it may never touch.
 */
class QuestSettlePolicyTest {

    private val start = 1_760_000_000_000L
    private val minute = 60_000L

    private fun attempt(
        method: VerificationMethod = VerificationMethod.PHYSICAL,
        state: QuestAttemptState = QuestAttemptState.IN_PROGRESS,
        committedMinutes: Long = 20L,
        startedAt: Long? = start
    ) = QuestAttempt(
        attemptId = "attempt-1",
        questId = "quest_walk_20",
        method = method,
        state = state,
        committedTimestamp = start,
        committedDurationMinutes = committedMinutes,
        activityStartTimestamp = startedAt
    )

    @Test
    fun `the deadline is the committed window plus ten minutes of grace`() {
        assertEquals(start + 30 * minute, QuestSettlePolicy.dueAt(attempt()))
        assertEquals(10L, QuestSettlePolicy.GRACE_MINUTES)
    }

    @Test
    fun `the deadline belongs to the window that was started, not to its length alone`() {
        val twenty = attempt(committedMinutes = 20L, startedAt = start)
        val sixty = attempt(committedMinutes = 60L, startedAt = start + 100 * minute)

        assertEquals(start + 30 * minute, QuestSettlePolicy.dueAt(twenty))
        assertEquals(start + 170 * minute, QuestSettlePolicy.dueAt(sixty))
    }

    @Test
    fun `a window that never started has no deadline to run out`() {
        assertNull(QuestSettlePolicy.dueAt(attempt(startedAt = null)))
        assertFalse(QuestSettlePolicy.isPastDue(attempt(startedAt = null), now = start + 9_999 * minute))
    }

    @Test
    fun `the tenth minute is the first moment it may be closed`() {
        val due = start + 30 * minute

        assertFalse(QuestSettlePolicy.isAutoSettleable(attempt(), now = due - 1L))
        assertTrue(QuestSettlePolicy.isAutoSettleable(attempt(), now = due))
    }

    @Test
    fun `a watched mission is never closed by this rule`() {
        // A digital window already has an engine that decides it; a second rule
        // reaching the same attempt would be two answers to whether it was earned.
        assertFalse(
            QuestSettlePolicy.isAutoSettleable(
                attempt(method = VerificationMethod.DIGITAL),
                now = start + 24L * 60L * minute
            )
        )
    }

    @Test
    fun `a window that already has an answer is not answered again`() {
        val late = start + 24L * 60L * minute

        assertFalse(
            QuestSettlePolicy.isAutoSettleable(
                attempt(state = QuestAttemptState.PENDING_VERIFICATION),
                now = late
            )
        )
        assertFalse(
            QuestSettlePolicy.isAutoSettleable(attempt(state = QuestAttemptState.REWARDED), now = late)
        )
        assertFalse(
            QuestSettlePolicy.isAutoSettleable(attempt(state = QuestAttemptState.FAILED), now = late)
        )
    }

    @Test
    fun `a commitment with no length is not a window`() {
        assertFalse(QuestSettlePolicy.isAutoSettleable(attempt(committedMinutes = 0L), now = start))
        assertNull(QuestSettlePolicy.dueAt(attempt(committedMinutes = 0L)))
    }

    @Test
    fun `the reason says the clock closed it and no declaration arrived`() {
        val reason = QuestSettlePolicy.autoSettleReason(20L)

        assertTrue(reason.contains("committed 20 minutes"))
        assertTrue(reason.contains("no declaration"))
        assertFalse("a self-report must not be worded like a check", reason.contains("verified"))
    }
}
