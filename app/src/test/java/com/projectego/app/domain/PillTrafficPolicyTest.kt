package com.projectego.app.domain

import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The traffic lights must be status, not decoration: one active light, chosen
 * from the same report the guard uses, with no invented thresholds.
 */
class PillTrafficPolicyTest {

    private fun report(
        used: Long = 30L,
        limit: Long = 120L,
        remaining: Long = 90L,
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
    fun `an ordinary day selects yellow`() {
        val state = PillTrafficPolicy.from(report(), locked = false)
        assertEquals(TrafficLight.YELLOW, state.active)
        assertFalse(state.pulse)
    }

    @Test
    fun `the critical zone selects a pulsing red`() {
        val state = PillTrafficPolicy.from(report(tier = 2), locked = false)
        assertEquals(TrafficLight.RED, state.active)
        assertTrue(state.pulse)
    }

    @Test
    fun `the report's critical status alone is enough`() {
        val state = PillTrafficPolicy.from(
            report(tier = 0, status = TimeStatus.CRITICAL),
            locked = false
        )
        assertEquals(TrafficLight.RED, state.active)
        assertTrue(state.pulse)
    }

    @Test
    fun `a reached limit is steady red, not the approaching pulse`() {
        val state = PillTrafficPolicy.from(
            report(used = 120L, remaining = 0L, exceeded = true, tier = 5,
                status = TimeStatus.LOCKED),
            locked = false
        )
        assertEquals(TrafficLight.RED, state.active)
        assertFalse(state.pulse)
    }

    @Test
    fun `the guard's lock alone is a steady red`() {
        val state = PillTrafficPolicy.from(report(), locked = true)
        assertEquals(TrafficLight.RED, state.active)
        assertFalse(state.pulse)
    }

    @Test
    fun `a recovery day is green even though its report is constructed as exceeded`() {
        val state = PillTrafficPolicy.from(
            report(used = 0L, remaining = 0L, exceeded = true, tier = 5,
                status = TimeStatus.ZERO_ENTERTAINMENT_DAY,
                dayType = DayType.ZERO_ENTERTAINMENT_DAY),
            locked = true
        )
        assertEquals(TrafficLight.GREEN, state.active)
        assertFalse(state.pulse)
    }

    @Test
    fun `no report and no lock is unknown, never a false normal`() {
        val state = PillTrafficPolicy.from(null, locked = false)
        assertEquals(TrafficLight.NONE, state.active)
        assertFalse(state.pulse)
    }

    @Test
    fun `no report but a locked guard is still a limit`() {
        val state = PillTrafficPolicy.from(null, locked = true)
        assertEquals(TrafficLight.RED, state.active)
        assertFalse(state.pulse)
    }

    @Test
    fun `state transitions do not retain the previous light`() {
        assertEquals(TrafficLight.YELLOW, PillTrafficPolicy.from(report(), false).active)
        assertEquals(
            TrafficLight.RED,
            PillTrafficPolicy.from(report(tier = 2), false).active
        )
        assertEquals(
            TrafficLight.RED,
            PillTrafficPolicy.from(report(exceeded = true, tier = 5,
                status = TimeStatus.LOCKED), false).active
        )
        assertEquals(TrafficLight.YELLOW, PillTrafficPolicy.from(report(), false).active)
    }

    @Test
    fun `reduced motion keeps the status but stops the pulse`() {
        val approaching = TrafficLightState(TrafficLight.RED, pulse = true)
        assertTrue(PillTrafficPolicy.shouldPulse(approaching, animationsAllowed = true))
        assertFalse(PillTrafficPolicy.shouldPulse(approaching, animationsAllowed = false))

        val limit = TrafficLightState(TrafficLight.RED, pulse = false)
        assertFalse(PillTrafficPolicy.shouldPulse(limit, animationsAllowed = true))

        val normal = TrafficLightState(TrafficLight.YELLOW, pulse = false)
        assertFalse(PillTrafficPolicy.shouldPulse(normal, animationsAllowed = true))
    }

    @Test
    fun `reduced motion gives approaching a static ring cue instead`() {
        val approaching = TrafficLightState(TrafficLight.RED, pulse = true)
        // Motion on: the pulse is the cue, no ring.
        assertFalse(PillTrafficPolicy.shouldRing(approaching, animationsAllowed = true))
        // Motion off: the ring is the cue, no pulse.
        assertTrue(PillTrafficPolicy.shouldRing(approaching, animationsAllowed = false))

        // The other states never wear the ring.
        assertFalse(
            PillTrafficPolicy.shouldRing(
                TrafficLightState(TrafficLight.RED, pulse = false),
                animationsAllowed = false
            )
        )
        assertFalse(
            PillTrafficPolicy.shouldRing(
                TrafficLightState(TrafficLight.YELLOW, pulse = false),
                animationsAllowed = false
            )
        )
        assertFalse(
            PillTrafficPolicy.shouldRing(
                TrafficLightState(TrafficLight.GREEN, pulse = false),
                animationsAllowed = false
            )
        )
    }
}
