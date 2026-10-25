package com.projectego.app.domain

import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expanded pill reads the same report the guard does.
 *
 * These tests pin the two things that would make it lie: inventing numbers when
 * there is no report, and deciding "approaching" from something other than the
 * report's own critical-zone state.
 */
class PillPanelPolicyTest {

    private fun report(
        used: Long = 60L,
        limit: Long = 120L,
        remaining: Long = 60L,
        exceeded: Boolean = false,
        tier: Int = 0,
        status: TimeStatus = TimeStatus.ACTIVE,
        dayType: DayType = DayType.NORMAL_DAY,
        apps: List<Pair<String, Long>> = emptyList()
    ) = UsageReport(
        totalEntertainmentMinutes = used,
        totalEntertainmentMillis = used * 60_000L,
        unblockedMinutes = 0L,
        totalScreenMinutes = used,
        limitMinutes = limit,
        isLimitExceeded = exceeded,
        remainingMinutes = remaining,
        criticalZoneTier = tier,
        timeStatus = status,
        dayType = dayType,
        currentStreak = 0,
        timeBank = TimeBank(earnedMinutes = 0L, spentMinutes = 0L),
        entertainmentAppDetails = apps,
        unblockedAppDetails = emptyList()
    )

    @Test
    fun `a report has a normal mood and carries the day's own figures`() {
        val model = PillPanelPolicy.from(report(), locked = false)
        assertEquals(PillMood.NORMAL, model.mood)
        assertEquals(60L, model.totalMinutes)
        assertEquals(60L, model.remainingMinutes)
    }

    @Test
    fun `the critical zone is the report's zone, not a second calculation`() {
        assertEquals(PillMood.APPROACHING, PillPanelPolicy.from(report(tier = 2), false).mood)
        assertEquals(
            PillMood.APPROACHING,
            PillPanelPolicy.from(report(status = TimeStatus.CRITICAL), false).mood
        )
    }

    @Test
    fun `reaching the limit wins over approaching`() {
        val model = PillPanelPolicy.from(
            report(remaining = 0L, exceeded = true, tier = 5, status = TimeStatus.LOCKED),
            locked = false
        )
        assertEquals(PillMood.LIMIT, model.mood)
        assertEquals(0L, model.remainingMinutes)
    }

    @Test
    fun `a locked state from the guard is a limit even if the report lags`() {
        assertEquals(PillMood.LIMIT, PillPanelPolicy.from(report(), locked = true).mood)
    }

    @Test
    fun `a recovery day is rest, not a spent allowance`() {
        val model = PillPanelPolicy.from(
            report(status = TimeStatus.ZERO_ENTERTAINMENT_DAY, dayType = DayType.ZERO_ENTERTAINMENT_DAY),
            locked = true
        )
        assertEquals(PillMood.RECOVERY, model.mood)
    }

    @Test
    fun `no report means no numbers at all`() {
        val model = PillPanelPolicy.from(null, locked = false)
        assertNull(model.totalMinutes)
        assertNull(model.remainingMinutes)
        assertTrue(model.rows.isEmpty())
        assertEquals(0, model.hiddenApps)
    }

    @Test
    fun `progress is the report's own used share, not a second calculation`() {
        val model = PillPanelPolicy.from(report(used = 30L, limit = 120L), locked = false)
        assertEquals(0.25f, model.progress!!, 0.001f)
    }

    @Test
    fun `a reached limit fills the bar even if the ratio lags`() {
        val model = PillPanelPolicy.from(
            report(used = 100L, remaining = 0L, exceeded = true, tier = 5, status = TimeStatus.LOCKED),
            locked = false
        )
        assertEquals(1f, model.progress!!, 0.0001f)
    }

    @Test
    fun `no usable limit or no report means no bar`() {
        assertNull(PillPanelPolicy.from(report(limit = 0L), locked = false).progress)
        assertNull(PillPanelPolicy.from(null, locked = false).progress)
    }

    @Test
    fun `a recovery day has no bar because entertainment is closed, not spent`() {
        val model = PillPanelPolicy.from(
            report(status = TimeStatus.ZERO_ENTERTAINMENT_DAY, dayType = DayType.ZERO_ENTERTAINMENT_DAY),
            locked = true
        )
        assertNull(model.progress)
    }

    @Test
    fun `apps are ranked, capped and never padded with zeroes`() {
        val model = PillPanelPolicy.from(
            report(
                apps = listOf(
                    "com.instagram.android" to 25L,
                    "com.google.android.youtube" to 35L,
                    "com.zhiliaoapp.musically" to 0L,
                    "com.android.chrome" to 10L,
                    "com.reddit.frontpage" to 5L
                )
            ),
            locked = false
        )
        assertEquals(
            listOf("com.google.android.youtube", "com.instagram.android", "com.android.chrome"),
            model.rows.map { it.packageName }
        )
        assertEquals(1, model.hiddenApps)
    }
}
