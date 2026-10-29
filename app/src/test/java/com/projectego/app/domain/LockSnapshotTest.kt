package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lock screen's numbers, carried from the decision that caused the lock.
 * The rule under test is what "no numbers" means: it is never zero.
 */
class LockSnapshotTest {

    @Test
    fun `both numbers present means the screen can print them`() {
        val snapshot = LockSnapshot.of(usedMinutes = 137L, limitMinutes = 120L)
        assertTrue(snapshot.hasNumbers)
        assertEquals(137L, snapshot.usedMinutes)
        assertEquals(120L, snapshot.limitMinutes)
    }

    @Test
    fun `zero is a real number, not a missing one`() {
        val snapshot = LockSnapshot.of(usedMinutes = 0L, limitMinutes = 0L)
        assertTrue("a zero-entertainment day must not look like missing data", snapshot.hasNumbers)
        assertEquals(0L, snapshot.usedMinutes)
        assertEquals(0L, snapshot.limitMinutes)
    }

    @Test
    fun `a missing number hides the pair`() {
        assertFalse(LockSnapshot.of(LockSnapshot.MISSING, 120L).hasNumbers)
        assertFalse(LockSnapshot.of(137L, LockSnapshot.MISSING).hasNumbers)
        assertFalse(LockSnapshot.of(LockSnapshot.MISSING, LockSnapshot.MISSING).hasNumbers)
    }

    @Test
    fun `an invalid negative is treated as missing`() {
        assertFalse(LockSnapshot.of(-5L, 120L).hasNumbers)
        assertFalse(LockSnapshot.of(137L, -5L).hasNumbers)
    }
}
