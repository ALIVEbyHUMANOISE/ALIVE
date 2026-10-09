package com.projectego.app.domain

import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When ALIVE is allowed to say "you are running low", with no Android runtime.
 *
 * The window is defined in minutes of the day's own allowance. These tests hold
 * that line: the boundary is inclusive on both ends, a locked or recovery day
 * never gets a warning, and - most importantly - nothing here keys off
 * `criticalZoneTier`, because the tier ladder can collapse entirely in
 * dev-test mode.
 */
class CriticalAlertPolicyTest {

    private fun report(
        remaining: Long,
        limit: Long = 120L,
        tier: Int = 0,
        status: TimeStatus = TimeStatus.CRITICAL,
        dayType: DayType = DayType.NORMAL_DAY,
        used: Long = 120L - remaining
    ) = UsageReport(
        totalEntertainmentMinutes = used.coerceAtLeast(0L),
        totalEntertainmentMillis = used * 60_000L,
        limitMinutes = limit,
        isLimitExceeded = remaining <= 0L,
        remainingMinutes = remaining,
        criticalZoneTier = tier,
        timeStatus = status,
        dayType = dayType,
        currentStreak = 0,
        timeBank = TimeBank(earnedMinutes = 0L, spentMinutes = 0L),
        entertainmentAppDetails = emptyList()
    )

    private fun shows(report: UsageReport, focusActive: Boolean = false) =
        CriticalAlertPolicy.shouldShow(report, focusActive)

    @Test
    fun `the window is thirty minutes wide and includes both ends`() {
        assertFalse(shows(report(31L)))
        assertTrue(shows(report(30L)))
        assertTrue(shows(report(1L)))
    }

    @Test
    fun `the last minute belongs to the lock, not to a warning`() {
        assertFalse(shows(report(0L)))
        assertFalse(shows(report(-5L)))
    }

    @Test
    fun `a locked day never gets a second message`() {
        assertFalse(shows(report(0L, status = TimeStatus.LOCKED)))
    }

    @Test
    fun `a recovery day has no allowance to run short of`() {
        assertFalse(
            shows(
                report(
                    remaining = 0L,
                    limit = 0L,
                    status = TimeStatus.ZERO_ENTERTAINMENT_DAY,
                    dayType = DayType.ZERO_ENTERTAINMENT_DAY,
                    used = 0L
                )
            )
        )
    }

    @Test
    fun `a zero allowance never triggers even when remaining reads positive`() {
        assertFalse(shows(report(remaining = 0L, limit = 0L, used = 0L)))
    }

    @Test
    fun `a focus session is the answer already`() {
        assertTrue(shows(report(20L)))
        assertFalse(shows(report(20L), focusActive = true))
    }

    @Test
    fun `the trigger never reads the critical zone tier`() {
        // Three reports the ladder disagrees about, all with 45 minutes left:
        // none of them is a warning. A tier-based predicate would light up on
        // the third one, which has 45 minutes left and says otherwise.
        assertFalse(shows(report(45L, tier = 0)))
        assertFalse(shows(report(45L, tier = 1)))
        assertFalse(shows(report(45L, tier = 5)))

        // And the reverse: a day the ladder has not noticed yet still counts,
        // because the figure is what the user is being told about.
        assertTrue(shows(report(20L, tier = 0)))
    }

    @Test
    fun `the window follows the day's own allowance rather than a magic number`() {
        // 20 minutes left of a 40-minute allowance is a third of the day gone.
        assertTrue(shows(report(remaining = 20L, limit = 40L, used = 20L)))
        assertFalse(shows(report(remaining = 35L, limit = 40L, used = 5L)))
    }

    @Test
    fun `dev test mode is not a special case`() {
        // A one-minute allowance is not a reason to stay quiet: at 1 minute
        // left the day really is over, and that is inside the window.
        assertTrue(shows(report(remaining = 1L, limit = 1L, tier = 1, used = 0L)))
    }
}
