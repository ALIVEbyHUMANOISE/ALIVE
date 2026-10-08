package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule ALIVE applies to a quest it cannot see: did the committed time
 * actually go by. Everything else about a physical quest is the person's word,
 * and this is where the line between those two is pinned down.
 */
class PhysicalQuestVerifierTest {

    private val minute = 60_000L
    private val start = 1_760_000_000_000L

    @Test
    fun `the gate counts down in whole minutes`() {
        assertEquals(20L, remaining(start, start, 20))
        assertEquals(19L, remaining(start, start + minute, 20))
        assertEquals(1L, remaining(start, start + 19 * minute, 20))
        assertEquals(0L, remaining(start, start + 20 * minute, 20))
        assertEquals(0L, remaining(start, start + 60 * minute, 20))
    }

    @Test
    fun `an attempt with no start has no gate to wait on`() {
        // 0 rather than a huge number, so the screen never shows a countdown that
        // can never end. The verdict then fails the attempt for the missing start,
        // which is the honest answer to a question that cannot be measured.
        assertEquals(0L, remaining(null, start, 20))
        assertTrue(PhysicalQuestVerifier.verify(null, start, 20) is PhysicalQuestVerifier.Result.Failed)
    }

    @Test
    fun `declaring after the committed time has gone by passes`() {
        val result = PhysicalQuestVerifier.verify(start, start + 20 * minute, 20)
        assertTrue(result.toString(), result is PhysicalQuestVerifier.Result.Passed)
    }

    @Test
    fun `declaring early fails, and says by how much`() {
        val result = PhysicalQuestVerifier.verify(start, start + 4 * minute, 20)
        assertTrue(result.toString(), result is PhysicalQuestVerifier.Result.Failed)
        val reason = (result as PhysicalQuestVerifier.Result.Failed).reason
        assertTrue(reason, reason.contains("4 of 20"))
    }

    @Test
    fun `the gate and the verdict agree at every minute`() {
        for (elapsed in 0L..25L) {
            val at = start + elapsed * minute
            val mayDeclare = remaining(start, at, 20) == 0L
            val passes = PhysicalQuestVerifier.verify(start, at, 20) is PhysicalQuestVerifier.Result.Passed
            assertEquals("at $elapsed minutes", mayDeclare, passes)
        }
    }

    private fun remaining(startedAt: Long?, now: Long, committed: Long): Long =
        PhysicalQuestVerifier.remainingMinutes(startedAt, now, committed)
}
