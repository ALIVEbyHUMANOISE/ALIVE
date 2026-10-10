package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chart's store.
 *
 * What matters here is that a bar never moves backwards and a gap is never
 * filled in: `UsageStats.totalTimeInForeground` is already cumulative, so a
 * second snapshot of the same day must replace the first, and an OEM that
 * trims the figures mid-day must not be able to shrink a day that has already
 * been drawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: UsageHistoryRepository

    private val today = "2026-10-05"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(UsageHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        repository = UsageHistoryRepository(context)
    }

    private fun record(
        date: String,
        minutes: Long,
        limit: Long = 120L,
        apps: Map<String, Long> = emptyMap(),
        screenMinutes: Long = minutes
    ) = repository.recordDay(date, minutes * 60_000L, limit, apps, screenMinutes * 60_000L)

    @Test
    fun `a later snapshot replaces the day instead of adding to it`() {
        record(today, 10L)
        record(today, 25L)

        // 25, not 35: the incoming figure is already the day's cumulative total.
        assertEquals(25L, repository.day(today)?.totalMinutes)
    }

    @Test
    fun `a smaller total never shrinks a day already drawn`() {
        record(today, 40L)
        record(today, 12L)

        assertEquals(40L, repository.day(today)?.totalMinutes)
    }

    @Test
    fun `a day frozen by an older rule is rewritten rather than kept forever`() {
        // The first rule charged messaging and study apps, so its total is
        // bigger than the one ALIVE now stands behind. Only a platform trimming
        // the same figures mid-day may be refused; a better rule has to be able
        // to correct the day it is being asked about.
        context.getSharedPreferences(UsageHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(today, "12000000|140|com.whatsapp=200").commit()

        record(today, 60L, screenMinutes = 180L)

        assertEquals(60L, repository.day(today)?.totalMinutes)
        assertEquals(180L, repository.day(today)?.screenMinutes)
    }

    @Test
    fun `a day never recorded is null and a recorded zero is a day`() {
        assertNull(repository.day("2026-01-01"))
        record("2026-01-02", 0L)

        val zero = repository.day("2026-01-02")
        assertNotNull(zero)
        assertEquals(0L, zero?.totalMinutes)
        assertEquals(120L, zero?.limitMinutes)
    }

    @Test
    fun `the day's own allowance survives the round trip`() {
        // A recovery day and a day with nothing spent both store zero minutes;
        // the limit is the only thing that tells them apart, forever after.
        record("2026-03-01", 0L, limit = 0L)

        assertEquals(0L, repository.day("2026-03-01")?.limitMinutes)
        assertTrue(repository.day("2026-03-01")?.isZeroEntertainmentDay == true)
    }

    @Test
    fun `per app minutes survive the round trip`() {
        val apps = mapOf("com.google.android.youtube" to 30L, "com.zhiliaoapp.musically" to 5L)
        record(today, 35L, apps = apps)

        val stored = repository.day(today)?.perAppMinutes
        assertEquals(30L, stored?.get("com.google.android.youtube"))
        assertEquals(5L, stored?.get("com.zhiliaoapp.musically"))
    }

    @Test
    fun `a zero-minute app is not stored`() {
        record(today, 10L, apps = mapOf("com.example.one" to 10L, "com.example.two" to 0L))

        assertEquals(setOf("com.example.one"), repository.day(today)?.perAppMinutes?.keys)
    }

    @Test
    fun `the day's whole screen figure survives the round trip`() {
        record(today, 30L, screenMinutes = 150L)

        assertEquals(150L, repository.day(today)?.screenMinutes)
    }

    @Test
    fun `time spent outside the allowance grows the day without moving the figure`() {
        record(today, 30L, screenMinutes = 30L)
        // An hour of chatting after the last reading: the charged figure has not
        // moved, so the record must still be rewritten rather than skipped.
        record(today, 30L, screenMinutes = 90L)

        assertEquals(30L, repository.day(today)?.totalMinutes)
        assertEquals(90L, repository.day(today)?.screenMinutes)
    }

    @Test
    fun `the screen figure never shrinks either`() {
        record(today, 20L, screenMinutes = 200L)
        record(today, 20L, screenMinutes = 60L)

        assertEquals(200L, repository.day(today)?.screenMinutes)
    }

    @Test
    fun `a day recorded before the screen figure existed says nothing about it`() {
        context.getSharedPreferences(UsageHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("2026-05-01", "1800000|120|").commit()

        val legacy = repository.day("2026-05-01")
        assertNotNull(legacy)
        assertEquals(30L, legacy?.totalMinutes)
        // Not zero: a day nobody counted is a gap, not a claim.
        assertNull(legacy?.screenMinutes)
    }

    @Test
    fun `retention drops the oldest day and never the one being written`() {
        record("2026-08-01", 20L)
        record("2026-09-01", 30L)
        record(today, 40L)

        // Writing October 5th puts the cutoff at August 26th, so only the
        // August day falls outside the window.
        assertNull(repository.day("2026-08-01"))
        assertNotNull(repository.day("2026-09-01"))
        assertEquals(40L, repository.day(today)?.totalMinutes)
        assertEquals(2, repository.recordedCount())
    }

    @Test
    fun `unparseable stored data reads as missing and never throws`() {
        val raw = context.getSharedPreferences(UsageHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
        raw.edit()
            .putString("2026-04-01", "not-a-record")
            .putString("2026-04-02", "abc|120|")
            .putString("2026-04-03", "60000|120")
            .putString("2026-04-04", "60000|120|broken-entry")
            .commit()

        assertNull(repository.day("2026-04-01"))
        assertNull(repository.day("2026-04-02"))
        assertNull(repository.day("2026-04-03"))
        // One bad app entry costs the whole per-app map, not the day's total.
        assertEquals(1L, repository.day("2026-04-04")?.totalMinutes)
    }

    @Test
    fun `a non-date key is not counted as a recorded day`() {
        record(today, 10L)
        context.getSharedPreferences(UsageHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("some_other_flag", "1").commit()

        assertEquals(1, repository.recordedCount())
        assertFalse(repository.oldestRecordedDate()!!.contains("flag"))
    }

    @Test
    fun `daysFor returns only the dates that exist, keyed as asked`() {
        record(today, 10L)
        record("2026-10-01", 20L)

        val found = repository.daysFor(listOf("2026-10-01", "2026-10-03", today))

        assertEquals(2, found.size)
        assertEquals(20L, found["2026-10-01"]?.totalMinutes)
        assertNull(found["2026-10-03"])
    }
}
