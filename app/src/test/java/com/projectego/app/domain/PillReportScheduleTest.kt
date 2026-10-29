package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report-read schedule: one read out at a time, newest wins, nothing after
 * teardown. These are the rules the pill's background UsageStats read relies on;
 * they are tested here without threads so they cannot pass by timing luck.
 */
class PillReportScheduleTest {

    private val schedule = PillReportSchedule()

    @Test
    fun `a request yields a token`() {
        val token = schedule.request()
        assertTrue(token != null)
        assertTrue(schedule.isBusy)
    }

    @Test
    fun `a second request while one is out is refused`() {
        schedule.request()
        assertNull("the tick must not queue work behind a slow query", schedule.request())
        assertNull(schedule.request())
    }

    @Test
    fun `a completed read may publish exactly once`() {
        val token = schedule.request()!!
        assertTrue(schedule.complete(token))
        assertFalse("a repeated callback must not apply twice", schedule.complete(token))
        assertFalse(schedule.isBusy)
    }

    @Test
    fun `a failed read still frees the slot`() {
        val token = schedule.request()!!
        // The service calls complete() for failures too, then ignores the null
        // report; the next tick must be able to ask again.
        schedule.complete(token)
        assertFalse(schedule.isBusy)
        assertTrue(schedule.request() != null)
    }

    @Test
    fun `an older result can never overwrite a newer one`() {
        val older = schedule.request()!!
        schedule.complete(older)
        val newer = schedule.request()!!
        assertTrue(schedule.complete(newer))
        assertFalse("a late older result must be dropped", schedule.complete(older))
    }

    @Test
    fun `nothing publishes after teardown`() {
        val token = schedule.request()!!
        schedule.close()
        assertFalse(schedule.complete(token))
        assertNull("and no new read may start", schedule.request())
        assertFalse(schedule.isBusy)
    }

    @Test
    fun `a read that completes after teardown cannot free a slot that no longer exists`() {
        val first = schedule.request()!!
        schedule.close()
        schedule.complete(first)
        assertFalse(schedule.isBusy)
        assertNull(schedule.request())
    }
}
