package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind the three views of one day.
 *
 * The one that can go wrong silently: a package whose time was partly charged
 * and partly swallowed by a focus session sits on both lists, and the total view
 * has to add those two rows. Replacing one with the other would under-report the
 * day by exactly the minutes someone spent focusing - the app would look like it
 * lost an hour of their life.
 */
class UsageBucketsTest {

    @Test
    fun `a package on both sides is one row in the total, added up`() {
        val charged = listOf("com.tiktok" to 25L, "com.zhiliaoapp.musically" to 10L)
        val unblocked = listOf("com.tiktok" to 5L, "com.whatsapp" to 30L)

        // Equal minutes are broken by name, not by which list happened to come
        // first: two runs over the same day must draw the same chart.
        assertEquals(
            listOf("com.tiktok" to 30L, "com.whatsapp" to 30L, "com.zhiliaoapp.musically" to 10L),
            UsageBuckets.merge(charged, unblocked)
        )
    }

    @Test
    fun `the total of the day is the two figures the report already states`() {
        val charged = listOf("com.tiktok" to 25L)
        val unblocked = listOf("com.whatsapp" to 40L)

        assertEquals(65L, UsageBuckets.totalMinutes(UsageBuckets.merge(charged, unblocked)))
    }

    @Test
    fun `a day with nothing in it has no rows and no minutes`() {
        assertEquals(emptyList<Pair<String, Long>>(), UsageBuckets.merge(emptyList(), emptyList()))
        assertEquals(0L, UsageBuckets.totalMinutes(UsageBuckets.merge(emptyList(), emptyList())))
    }

    @Test
    fun `each lens shows its own side of the day`() {
        val charged = listOf("com.tiktok" to 25L)
        val unblocked = listOf("com.whatsapp" to 40L)

        assertEquals(charged, UsageBuckets.rows(charged, unblocked, UsageBuckets.Bucket.ENTERTAINMENT))
        assertEquals(unblocked, UsageBuckets.rows(charged, unblocked, UsageBuckets.Bucket.UNBLOCKED))
        assertEquals(
            listOf("com.whatsapp" to 40L, "com.tiktok" to 25L),
            UsageBuckets.rows(charged, unblocked, UsageBuckets.Bucket.TOTAL)
        )
    }
}
