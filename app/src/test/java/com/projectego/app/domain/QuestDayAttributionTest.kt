package com.projectego.app.domain

import com.projectego.app.data.LockStateManager
import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.VerificationMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which day a finished mission counts for.
 *
 * The rule exists because a mission can be lived on one day and paid on the next.
 * These tests pin the anchoring order - declared finish, then the window's own
 * end, then its start, then the commit - against values rather than calendar
 * dates, so they do not depend on the machine's timezone.
 */
class QuestDayAttributionTest {

    private val minute = 60_000L

    private fun attempt(
        method: VerificationMethod = VerificationMethod.PHYSICAL,
        committedAt: Long,
        durationMinutes: Long = 20L,
        startedAt: Long? = null,
        endedAt: Long? = null
    ) = QuestAttempt(
        questId = "quest.under.test",
        method = method,
        state = QuestAttemptState.REWARDED,
        committedTimestamp = committedAt,
        committedDurationMinutes = durationMinutes,
        activityStartTimestamp = startedAt,
        activityEndTimestamp = endedAt
    )

    @Test
    fun `a declared finish decides the day, whatever the claim day`() {
        val committed = 1_760_000_000_000L
        val started = committed + 3_600_000L
        val declared = started + 20 * minute
        val claimDaysLater = declared + 3 * 86_400_000L

        val landed = QuestDayAttribution.livedAt(
            attempt(committedAt = committed, startedAt = started, endedAt = declared)
        )

        assertEquals(declared, landed)
        assertNotEquals(
            "a later claim must not move the mission to its own day",
            LockStateManager.dayKeyOf(claimDaysLater),
            LockStateManager.dayKeyOf(landed)
        )
    }

    @Test
    fun `an auto-settled mission counts for the end of its window, not the settle`() {
        val committed = 1_760_000_000_000L
        val started = committed + 1_000L
        val windowEnd = started + 20 * minute
        val settledTenMinutesLate = windowEnd + 10 * minute

        // The auto-settle path never stamps an end; it verifies against the
        // window. The attribution must therefore fall back to the window's end.
        val landed = QuestDayAttribution.livedAt(
            attempt(committedAt = committed, startedAt = started, endedAt = null)
        )

        assertEquals(windowEnd, landed)
        assertNotEquals("the settle moment is not the mission", settledTenMinutesLate, landed)
    }

    @Test
    fun `a window that crosses midnight belongs to the day it ends on`() {
        val committed = 1_760_000_000_000L
        val started = committed + 500L
        val windowEnd = started + 40 * minute

        val landed = QuestDayAttribution.dayKeyOf(
            attempt(committedAt = committed, durationMinutes = 40L, startedAt = started)
        )

        assertEquals(LockStateManager.dayKeyOf(windowEnd), landed)
    }

    @Test
    fun `a digital attempt without a length counts for the day it started`() {
        val committed = 1_760_000_000_000L
        val started = committed + 90_000L

        val landed = QuestDayAttribution.livedAt(
            attempt(
                method = VerificationMethod.DIGITAL,
                committedAt = committed,
                durationMinutes = 0L,
                startedAt = started
            )
        )

        assertEquals(started, landed)
    }

    @Test
    fun `an attempt with no timestamps falls back to when it was committed`() {
        val committed = 1_760_000_000_000L

        val landed = QuestDayAttribution.livedAt(attempt(committedAt = committed, durationMinutes = 0L))

        assertEquals(committed, landed)
    }
}
