package com.projectego.app.domain

import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The difference between "nothing was used" and "I could not tell you".
 *
 * A failed query must never become a zero day: these tests pin the mapping from
 * what the platform did to what the app may conclude, and the two rules the rest
 * of ALIVE follows when the answer is not [UsageRead.Success].
 */
class UsageReadTest {

    private fun report(used: Long, limit: Long) = UsageReport(
        totalEntertainmentMinutes = used,
        totalEntertainmentMillis = used * 60_000L,
        unblockedMinutes = 0L,
        totalScreenMinutes = used,
        limitMinutes = limit,
        isLimitExceeded = used >= limit,
        remainingMinutes = (limit - used).coerceAtLeast(0L),
        criticalZoneTier = if (used >= limit) 5 else 0,
        timeStatus = if (used >= limit) TimeStatus.LOCKED else TimeStatus.ACTIVE,
        dayType = DayType.NORMAL_DAY,
        currentStreak = 0,
        timeBank = TimeBank(),
        entertainmentAppDetails = emptyList(),
        unblockedAppDetails = emptyList()
    )

    // ---------- the read ------------------------------

    @Test
    fun `a missing grant is its own answer, not an empty day`() {
        val read = UsageReadGuard.read(
            permissionGranted = false,
            query = { error("the query must not even run") },
            build = { error("nothing to build") }
        )
        assertEquals(UsageRead.PermissionUnavailable, read)
        assertFalse(read.isAvailable)
    }

    @Test
    fun `a successful empty query is a real zero`() {
        val read = UsageReadGuard.read(
            permissionGranted = true,
            query = { emptyList() },
            build = { report(used = 0L, limit = 120L) }
        )
        assertTrue(read is UsageRead.Success)
        assertEquals(0L, (read as UsageRead.Success).report.totalEntertainmentMinutes)
    }

    @Test
    fun `a successful query keeps the numbers it produced`() {
        val read = UsageReadGuard.read(
            permissionGranted = true,
            query = { emptyList() },
            build = { report(used = 137L, limit = 120L) }
        ) as UsageRead.Success
        assertEquals(137L, read.report.totalEntertainmentMinutes)
        assertEquals(120L, read.report.limitMinutes)
        assertTrue(read.report.isLimitExceeded)
    }

    @Test
    fun `a query that returns nothing is a failure, not zero usage`() {
        val read = UsageReadGuard.read(
            permissionGranted = true,
            query = { null },
            build = { error("nothing to build") }
        )
        assertEquals(UsageRead.QueryFailed, read)
        assertFalse(read.isAvailable)
    }

    @Test
    fun `a refused query is reported as permission, a broken one as a query failure`() {
        assertEquals(
            UsageRead.PermissionUnavailable,
            UsageReadGuard.read(true, query = { throw SecurityException("revoked") }, build = { error("x") })
        )
        assertEquals(
            UsageRead.QueryFailed,
            UsageReadGuard.read(true, query = { throw IllegalStateException("binder") }, build = { error("x") })
        )
    }

    @Test
    fun `a report that cannot be built is not a zero day`() {
        val read = UsageReadGuard.read(
            permissionGranted = true,
            query = { emptyList() },
            build = { throw IllegalStateException("bad aggregation") }
        )
        assertEquals(UsageRead.ProcessingFailed, read)
        assertFalse(read.isAvailable)
    }

    // ---------- the enforcement rule ------------------------------

    @Test
    fun `unavailable data only locks when something else already says so`() {
        assertTrue("a persisted lock needs no usage log", UsageSourcePolicy.lockFromKnownStateOnly(alreadyLocked = true, zeroDay = false))
        assertTrue("a zero day needs no usage log", UsageSourcePolicy.lockFromKnownStateOnly(alreadyLocked = false, zeroDay = true))
        assertFalse(
            "no lock and no zero day means no evidence to lock on",
            UsageSourcePolicy.lockFromKnownStateOnly(alreadyLocked = false, zeroDay = false)
        )
    }

    // ---------- the claim rule ------------------------------

    @Test
    fun `a claim under the new limit clears the lock`() {
        assertTrue(UsageSourcePolicy.claimClearsLock(UsageRead.Success(report(used = 130L, limit = 140L))))
        assertTrue(UsageSourcePolicy.claimClearsLock(UsageRead.Success(report(used = 0L, limit = 120L))))
    }

    @Test
    fun `a claim still over the limit keeps the lock`() {
        assertFalse(UsageSourcePolicy.claimClearsLock(UsageRead.Success(report(used = 130L, limit = 120L))))
        assertFalse(
            "equal usage is a spent day",
            UsageSourcePolicy.claimClearsLock(UsageRead.Success(report(used = 120L, limit = 120L)))
        )
    }

    @Test
    fun `a claim whose verification could not be read does not unlock`() {
        // Earned minutes are real, but "earned" is not "verified under the limit":
        // unlocking on a failed read would let enforcement be bypassed by making
        // the read fail. The recovery is the latch rule below, not this one.
        assertFalse(UsageSourcePolicy.claimClearsLock(UsageRead.PermissionUnavailable))
        assertFalse(UsageSourcePolicy.claimClearsLock(UsageRead.QueryFailed))
        assertFalse(UsageSourcePolicy.claimClearsLock(UsageRead.ProcessingFailed))
    }

    @Test
    fun `a lock is set while the day is spent and kept while it is spent`() {
        assertEquals(UsageSourcePolicy.Latch.LOCK, UsageSourcePolicy.latchAction(exceeded = true, locked = false))
        assertEquals(UsageSourcePolicy.Latch.LOCK, UsageSourcePolicy.latchAction(exceeded = true, locked = true))
    }

    @Test
    fun `a verified under-limit read clears a latched lock`() {
        // Usage only grows within a day, so under-limit plus a latched lock means
        // the limit was raised after the lock - a claim whose own verification
        // read failed. This is the recovery that keeps the claim safe.
        assertEquals(UsageSourcePolicy.Latch.CLEAR, UsageSourcePolicy.latchAction(exceeded = false, locked = true))
    }

    @Test
    fun `an unremarkable under-limit read leaves the latch alone`() {
        assertEquals(UsageSourcePolicy.Latch.LEAVE, UsageSourcePolicy.latchAction(exceeded = false, locked = false))
    }

    @Test
    fun `a display stand-in may not move the latch in either direction`() {
        // The dashboard's fallback for an unreadable day is an empty report. It
        // may be drawn, but it is not evidence: not to lock, and not to unlock.
        assertEquals(
            UsageSourcePolicy.Latch.LEAVE,
            UsageSourcePolicy.latchAction(exceeded = true, locked = false, mayDecide = false)
        )
        assertEquals(
            UsageSourcePolicy.Latch.LEAVE,
            UsageSourcePolicy.latchAction(exceeded = false, locked = true, mayDecide = false)
        )
    }

    @Test
    fun `yesterday's window cannot exceed today's limit`() {
        assertFalse(
            UsageSourcePolicy.countsAsExceeded(totalMinutes = 200L, limitMinutes = 120L, windowIsToday = false)
        )
        assertFalse(
            UsageSourcePolicy.countsAsExceeded(totalMinutes = 120L, limitMinutes = 120L, windowIsToday = false)
        )
        assertTrue(
            UsageSourcePolicy.countsAsExceeded(totalMinutes = 120L, limitMinutes = 120L, windowIsToday = true)
        )
        assertFalse(
            UsageSourcePolicy.countsAsExceeded(totalMinutes = 119L, limitMinutes = 120L, windowIsToday = true)
        )
    }
}
