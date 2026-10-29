package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * A mission that was lived yesterday and paid after midnight.
 *
 * The rollover judges yesterday from what was written while it was yesterday, so a
 * claim that arrives this morning used to be too late: the day was already a
 * no-entertainment day, and the person who had exercised was told they had done
 * nothing. These tests hold the repair and, just as important, its limits: only
 * the day that was just judged, only while that judgement is still today's, and
 * never across an unrepaired guard gap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LockStateManagerLateQuestTest {

    private lateinit var context: Context
    private lateinit var manager: LockStateManager

    private val today get() = LocalDate.now().toString()
    private val yesterday get() = LocalDate.now().minusDays(1).toString()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        manager = LockStateManager(context)
    }

    private fun prefs() =
        context.getSharedPreferences("project_ego_state", Context.MODE_PRIVATE)

    /** Yesterday went by without a quest, and this morning's rollover judged it. */
    private fun judgeYesterdayAsFailed(streakBefore: Int = 4) {
        // The rule has to be in force: the first rollover after it ships is
        // deliberately not judged.
        prefs().edit()
            .putBoolean("key_quest_rule_announced", true)
            .putInt("key_current_streak", streakBefore)
            .commit()

        manager.simulateRollover(yesterday)

        assertEquals(DayType.ZERO_ENTERTAINMENT_DAY, manager.getDayType())
        assertEquals(0, manager.getCurrentStreak())
        assertTrue(manager.wasLastDayFailed())
    }

    @Test
    fun `a mission claimed after midnight gives back the day it was lived on`() {
        judgeYesterdayAsFailed(streakBefore = 4)

        LockStateManager.recordQuestCompleted(context, yesterday)

        assertEquals(DayType.NORMAL_DAY, manager.getDayType())
        assertFalse(manager.isLocked())
        assertFalse(manager.wasLastDayFailed())
        assertEquals("the streak the failed rollover reset comes back, plus the day", 5, manager.getCurrentStreak())
        assertTrue(manager.isQuestCompletedOn(yesterday))
    }

    @Test
    fun `repairing twice does not grow the streak twice`() {
        judgeYesterdayAsFailed(streakBefore = 4)

        LockStateManager.recordQuestCompleted(context, yesterday)
        LockStateManager.recordQuestCompleted(context, yesterday)

        assertEquals(5, manager.getCurrentStreak())
        assertEquals(DayType.NORMAL_DAY, manager.getDayType())
    }

    @Test
    fun `a mission lived today cannot unlock today`() {
        judgeYesterdayAsFailed(streakBefore = 4)

        LockStateManager.recordQuestCompleted(context, today)

        assertEquals(
            "only the day that was judged can be repaired",
            DayType.ZERO_ENTERTAINMENT_DAY,
            manager.getDayType()
        )
        assertEquals(0, manager.getCurrentStreak())
    }

    @Test
    fun `an unrepaired guard gap still outranks the claim`() {
        judgeYesterdayAsFailed(streakBefore = 4)
        manager.markGuardGap(yesterday, earnedAtDiscovery = 0L)

        LockStateManager.recordQuestCompleted(context, yesterday)

        assertEquals(DayType.ZERO_ENTERTAINMENT_DAY, manager.getDayType())
        assertEquals(0, manager.getCurrentStreak())
    }

    @Test
    fun `a repaired guard gap lets the claim restore the day`() {
        judgeYesterdayAsFailed(streakBefore = 2)
        manager.markGuardGap(yesterday, earnedAtDiscovery = 0L)
        assertTrue(manager.repairGuardGapIfEarned(earnedMinutes = 1L))

        LockStateManager.recordQuestCompleted(context, yesterday)

        assertEquals(DayType.NORMAL_DAY, manager.getDayType())
        assertEquals(3, manager.getCurrentStreak())
    }
}
