package com.projectego.app.domain

import com.projectego.app.model.AppCategory
import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The character's pose, chosen from states the app can actually prove.
 *
 * The app name is the only context available - not what the person is doing
 * inside it - so VIDEO and SOCIAL both wear the phone pose in the UI, and a
 * package nobody mapped gets the neutral pose instead of a guess.
 */
class PillPosePolicyTest {

    private val video = "com.google.android.youtube"
    private val social = "com.instagram.android"
    private val game = "com.supercell.clashofclans"
    private val browser = "com.android.chrome"

    private fun report(
        used: Long = 60L,
        limit: Long = 120L,
        remaining: Long = 60L,
        exceeded: Boolean = false,
        tier: Int = 0,
        status: TimeStatus = TimeStatus.ACTIVE,
        dayType: DayType = DayType.NORMAL_DAY
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
        entertainmentAppDetails = emptyList(),
        unblockedAppDetails = emptyList()
    )

    @Test
    fun `each supported application context has its pose`() {
        assertEquals(PillPose.VIDEO, PillPosePolicy.poseFor(report(), false, video))
        assertEquals(PillPose.SOCIAL, PillPosePolicy.poseFor(report(), false, social))
        assertEquals(PillPose.GAMING, PillPosePolicy.poseFor(report(), false, game))
        assertEquals(PillPose.BROWSING, PillPosePolicy.poseFor(report(), false, browser))
    }

    @Test
    fun `an unknown or absent app falls back to idle`() {
        assertEquals(PillPose.IDLE, PillPosePolicy.poseFor(report(), false, null))
        assertEquals(PillPose.IDLE, PillPosePolicy.poseFor(report(), false, "com.example.unknown"))
        // Entertainment the curated contexts do not name (music apps) is also
        // neutral: there is no honest pose for it in the sprite set.
        assertEquals(PillPose.IDLE, PillPosePolicy.poseFor(report(), false, "com.spotify.music"))
    }

    @Test
    fun `a reached limit outranks an application pose`() {
        assertEquals(
            PillPose.LIMIT,
            PillPosePolicy.poseFor(
                report(remaining = 0L, exceeded = true, tier = 5, status = TimeStatus.LOCKED),
                locked = false,
                packageName = video
            )
        )
    }

    @Test
    fun `the guard's lock outranks the application pose even with a stale report`() {
        assertEquals(PillPose.LIMIT, PillPosePolicy.poseFor(report(), true, game))
    }

    @Test
    fun `approaching the limit outranks the application pose`() {
        assertEquals(
            PillPose.APPROACHING,
            PillPosePolicy.poseFor(report(tier = 2), false, game)
        )
    }

    @Test
    fun `a recovery day is rest, not a spent limit and not an app pose`() {
        // The report marks a recovery day exceeded by construction; the pose
        // must still say "rest", matching PillPanelPolicy's own ordering.
        assertEquals(
            PillPose.RECOVERY,
            PillPosePolicy.poseFor(
                report(exceeded = true, tier = 5, status = TimeStatus.ZERO_ENTERTAINMENT_DAY,
                    dayType = DayType.ZERO_ENTERTAINMENT_DAY),
                locked = true,
                packageName = video
            )
        )
    }

    @Test
    fun `without a report the app context still answers`() {
        assertEquals(PillPose.VIDEO, PillPosePolicy.poseFor(null, false, video))
        assertEquals(PillPose.IDLE, PillPosePolicy.poseFor(null, false, null))
    }

    @Test
    fun `rapid context changes do not retain the old pose`() {
        assertEquals(PillPose.VIDEO, PillPosePolicy.poseFor(report(), false, video))
        assertEquals(PillPose.SOCIAL, PillPosePolicy.poseFor(report(), false, social))
        assertEquals(PillPose.IDLE, PillPosePolicy.poseFor(report(), false, "com.example.unknown"))
        assertEquals(PillPose.GAMING, PillPosePolicy.poseFor(report(), false, game))
    }

    @Test
    fun `every mapped context package is entertainment to the classifier`() {
        val wrong = PillPosePolicy.contextEntries()
            .filter { (packageName, _) ->
                AppClassifier.curated(packageName) != AppCategory.ENTERTAINMENT
            }
            .map { it.first }
        assertTrue("not entertainment: $wrong", wrong.isEmpty())
    }
}
