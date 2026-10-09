package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Where claimed minutes live: with the day they were claimed on, and only in
 * that day's allowance.
 *
 * The claim itself is capped by `EntertainmentClaimPolicy`; what these tests
 * hold is the storage half of the bargain - a fresh day reads zero and a
 * recovery day reads none, so neither the cap nor the lock can be worn down
 * by waiting or by claiming at midnight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LockStateManagerClaimTest {

    private lateinit var context: Context
    private lateinit var manager: LockStateManager

    private val yesterday get() = LocalDate.now().minusDays(1).toString()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("project_ego_state", Context.MODE_PRIVATE).edit().clear().commit()
        manager = LockStateManager(context)
    }

    @Test
    fun `a fresh install has claimed nothing`() {
        assertEquals(0L, manager.claimedMinutesToday())
        assertEquals(120L, manager.getDailyLimitMinutes())
    }

    @Test
    fun `claimed minutes raise today's allowance`() {
        manager.addClaimedMinutesToday(20L)

        assertEquals(20L, manager.claimedMinutesToday())
        assertEquals(140L, manager.getDailyLimitMinutes())
    }

    @Test
    fun `claims add up within the day`() {
        manager.addClaimedMinutesToday(5L)
        manager.addClaimedMinutesToday(7L)

        assertEquals(12L, manager.claimedMinutesToday())
        assertEquals(132L, manager.getDailyLimitMinutes())
    }

    @Test
    fun `yesterday's claim does not raise today's allowance`() {
        manager.addClaimedMinutesToday(20L, dayKey = yesterday)

        assertEquals(0L, manager.claimedMinutesToday())
        assertEquals(120L, manager.getDailyLimitMinutes())
    }

    @Test
    fun `the first claim of a day starts from zero, not from yesterday's number`() {
        manager.addClaimedMinutesToday(20L, dayKey = yesterday)
        manager.addClaimedMinutesToday(5L)

        assertEquals(5L, manager.claimedMinutesToday())
        assertEquals(125L, manager.getDailyLimitMinutes())
    }

    @Test
    fun `a recovery day has no allowance for a claim to raise`() {
        // Two rollovers: the first is the unjudged grace day, the second judges
        // a day nothing landed on.
        manager.simulateRollover(yesterday)
        manager.simulateRollover(yesterday)
        manager.addClaimedMinutesToday(20L)

        assertEquals(DayType.ZERO_ENTERTAINMENT_DAY, manager.getDayType())
        assertEquals(0L, manager.getDailyLimitMinutes())
    }
}
