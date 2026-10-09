package com.projectego.app.domain

import com.projectego.app.domain.HourlyUsage.Event
import com.projectego.app.domain.HourlyUsage.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The hour arithmetic behind the "today" chart.
 *
 * Every case here is one the platform's event log actually produces: an app
 * paused in the next hour, a second app resumed without the first being paused,
 * the screen going off mid-session, and the session that is still running when
 * the chart asks.
 */
class HourlyUsageTest {

    private val minute = TimeUnit.MINUTES.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)

    /** Midnight, so an hour index is just the distance from zero. */
    private val midnight = 0L

    private fun minutes(array: LongArray) = array.map { it / minute }

    @Test
    fun `a session inside one hour is counted in that hour only`() {
        val out = HourlyUsage.bucket(
            midnight,
            listOf(
                Event(9 * hour + 10 * minute, Kind.RESUMED, "game"),
                Event(9 * hour + 30 * minute, Kind.PAUSED, "game")
            ),
            nowMillis = 10 * hour
        )
        assertEquals(20L, minutes(out)[9])
        assertEquals(0L, minutes(out)[8])
        assertEquals(0L, minutes(out)[10])
    }

    @Test
    fun `a session across an hour boundary is split, not rounded to one of them`() {
        val out = HourlyUsage.bucket(
            midnight,
            listOf(
                Event(9 * hour + 50 * minute, Kind.RESUMED, "video"),
                Event(10 * hour + 10 * minute, Kind.PAUSED, "video")
            ),
            nowMillis = 11 * hour
        )
        assertEquals(10L, minutes(out)[9])
        assertEquals(10L, minutes(out)[10])
    }

    @Test
    fun `a second app in front ends the first, whatever the log says about it`() {
        val out = HourlyUsage.bucket(
            midnight,
            listOf(
                Event(8 * hour, Kind.RESUMED, "game"),
                Event(8 * hour + 5 * minute, Kind.RESUMED, "video"),
                Event(8 * hour + 20 * minute, Kind.PAUSED, "video")
            ),
            nowMillis = 9 * hour
        )
        assertEquals(20L, minutes(out)[8])
    }

    @Test
    fun `the screen going off closes the session even without a pause`() {
        val out = HourlyUsage.bucket(
            midnight,
            listOf(
                Event(21 * hour, Kind.RESUMED, "game"),
                Event(21 * hour + 15 * minute, Kind.SCREEN_OFF, "game")
            ),
            nowMillis = 23 * hour
        )
        assertEquals(15L, minutes(out)[21])
        assertEquals(0L, minutes(out)[22])
        assertEquals(0L, minutes(out)[23])
    }

    @Test
    fun `a session still running is counted up to now`() {
        val out = HourlyUsage.bucket(
            midnight,
            listOf(Event(14 * hour, Kind.RESUMED, "game")),
            nowMillis = 14 * hour + 25 * minute
        )
        assertEquals(25L, minutes(out)[14])
    }

    @Test
    fun `time before midnight is clipped, not carried into the first hour`() {
        val dayStart = 10 * hour
        val out = HourlyUsage.bucket(
            dayStart,
            listOf(
                Event(dayStart - 20 * minute, Kind.RESUMED, "game"),
                Event(dayStart + 10 * minute, Kind.PAUSED, "game")
            ),
            nowMillis = dayStart + hour
        )
        assertEquals(10L, minutes(out)[0])
    }

    @Test
    fun `a day is only ever twenty-four hours long`() {
        val out = HourlyUsage.bucket(
            midnight,
            listOf(
                Event(23 * hour, Kind.RESUMED, "game"),
                Event(25 * hour, Kind.PAUSED, "game")
            ),
            nowMillis = 26 * hour
        )
        assertEquals(60L, minutes(out)[23])
        assertEquals(HourlyUsage.HOURS, out.size)
    }

    @Test
    fun `an empty log leaves every hour at zero`() {
        val out = HourlyUsage.bucket(midnight, emptyList(), nowMillis = 12 * hour)
        assertEquals(List(HourlyUsage.HOURS) { 0L }, minutes(out))
    }
}
