package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The guard gap: what happens to a day when the thing that closes apps is off.
 *
 * Android will not let an app stop the user from turning an accessibility service
 * off, so the response lives in ALIVE's own economy. These tests hold the two
 * halves of that bargain: an unrepaired gap is not a kept day, and a repaired one
 * is - so the price is real without being a trap, and a gap from an earlier day
 * never charges today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LockStateManagerGuardTest {

    private lateinit var context: Context
    private lateinit var manager: LockStateManager

    private val today get() = LocalDate.now().toString()
    private val yesterday get() = LocalDate.now().minusDays(1).toString()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("project_ego_state", Context.MODE_PRIVATE).edit().clear().commit()
        manager = LockStateManager(context)
    }

    @Test
    fun `no gap has been recorded before anything happens`() {
        assertNull(manager.unguardedDay())
        assertFalse(manager.isGuardGapOpenOn(today))
    }

    @Test
    fun `a gap belongs to the day it was noticed on`() {
        manager.markGuardGap(today, 0L)

        assertTrue(manager.isGuardGapOpenOn(today))
        assertFalse(manager.isGuardGapOpenOn(yesterday))
        assertEquals(today, manager.unguardedDay())
    }

    @Test
    fun `marking the same day twice does not move the goalpost`() {
        manager.markGuardGap(today, 40L)
        // Opening ALIVE again while the service is still off must not raise the
        // bar to 40 + whatever a later read happens to show.
        manager.markGuardGap(today, 90L)

        assertTrue(manager.repairGuardGapIfEarned(41L))
    }

    @Test
    fun `a repair needs a reward that actually landed`() {
        manager.markGuardGap(today, 40L)

        assertFalse(manager.repairGuardGapIfEarned(40L))
        assertTrue(manager.isGuardGapOpenOn(today))
        assertTrue(manager.repairGuardGapIfEarned(60L))
        assertFalse(manager.isGuardGapOpenOn(today))
    }

    @Test
    fun `a repaired gap stays repaired`() {
        manager.markGuardGap(today, 0L)
        assertTrue(manager.repairGuardGapIfEarned(20L))
        // A later read of the same total must not reopen it.
        assertFalse(manager.repairGuardGapIfEarned(20L))
        assertFalse(manager.isGuardGapOpenOn(today))
    }

    @Test
    fun `an unrepaired gap makes the day it happened in not kept`() {
        manager.markGuardGap(yesterday, 0L)

        manager.simulateRollover(yesterday, simulateLock = false)

        assertTrue(manager.wasLastDayFailed())
        assertEquals(0, manager.getCurrentStreak())
        assertEquals(DayType.ZERO_ENTERTAINMENT_DAY, manager.getDayType())
    }

    @Test
    fun `a repaired gap leaves the day kept`() {
        manager.markGuardGap(yesterday, 0L)
        assertTrue(manager.repairGuardGapIfEarned(20L))

        manager.simulateRollover(yesterday, simulateLock = false)

        assertFalse(manager.wasLastDayFailed())
        assertEquals(1, manager.getCurrentStreak())
        assertEquals(DayType.NORMAL_DAY, manager.getDayType())
    }

    @Test
    fun `a gap from an older day does not charge the day before today`() {
        manager.markGuardGap(LocalDate.now().minusDays(5).toString(), 0L)

        manager.simulateRollover(yesterday, simulateLock = false)

        assertFalse(manager.wasLastDayFailed())
        assertEquals(1, manager.getCurrentStreak())
    }

    @Test
    fun `a locked day still fails on its own without any gap`() {
        manager.simulateRollover(yesterday, simulateLock = true)

        assertTrue(manager.wasLastDayFailed())
        assertEquals(DayType.ZERO_ENTERTAINMENT_DAY, manager.getDayType())
    }
}
