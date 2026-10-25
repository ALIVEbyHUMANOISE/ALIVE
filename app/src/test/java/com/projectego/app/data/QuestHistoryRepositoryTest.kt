package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The seven-day mission log.
 *
 * The rule this file exists to hold is the one every other day-scoped store in
 * ALIVE already obeys: a day that was never watched is not a day that was idle.
 * A log that cannot tell those apart is a log that quietly flatters, so the
 * unrecorded rows are tested as hard as the counted ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var history: QuestHistoryRepository

    /** A fixed mid-month day, so the window arithmetic has no surprises. */
    private val today = "2026-10-25"

    private fun key(offsetDays: Long) = LocalDate.parse(today).minusDays(offsetDays).toString()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(QuestHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
        history = QuestHistoryRepository(context)
    }

    @Test
    fun `a day nobody opened stays unknown rather than becoming empty`() {
        history.ensureDay(today)

        assertNull("yesterday was never lived in this log", history.day(key(1)))

        val rows = history.recent(today)
        assertEquals(7, rows.size)
        assertTrue(rows.first().recorded)
        assertTrue(rows.last().let { !it.recorded && it.completedCount == 0 })
    }

    @Test
    fun `an opened day with nothing earned reads as zero, not as unknown`() {
        history.ensureDay(today)

        val day = history.day(today)
        assertNotNull(day)
        assertEquals(0, day!!.completedCount)
        assertEquals(0L, day.earnedMinutes)
        assertTrue(day.recorded)
    }

    @Test
    fun `a payout adds one finished mission and the minutes it paid`() {
        history.recordCompletion(today, 15L)
        history.recordCompletion(today, 20L)

        val day = history.day(today)!!
        assertEquals(2, day.completedCount)
        assertEquals(35L, day.earnedMinutes)
    }

    @Test
    fun `a payout filed under yesterday lands on yesterday`() {
        // The claim runs after the rollover noticed the new day, but the attempt
        // it is paying belongs to the day before.
        history.recordCompletion(key(1), 20L)

        assertEquals(1, history.day(key(1))!!.completedCount)
        assertNull("today earned nothing of its own", history.day(today))
    }

    @Test
    fun `the window is seven days counted back from today, newest first`() {
        val rows = history.recent(today)

        assertEquals(7, rows.size)
        assertEquals(today, rows.first().dayKey)
        assertEquals(key(6), rows.last().dayKey)
        assertEquals(
            listOf("2026-10-25", "2026-10-24", "2026-10-23", "2026-10-22", "2026-10-21", "2026-10-20", "2026-10-19"),
            rows.map { it.dayKey }
        )
    }

    @Test
    fun `a payout older than the window is dropped instead of dragging the window back`() {
        history.recordCompletion(today, 15L)

        // Eight days back is outside what the log promises to hold. The old write
        // goes, and today's count is untouched by the attempt to make it.
        history.recordCompletion(key(7), 15L)
        assertNull("the eighth day is not kept", history.day(key(7)))
        assertEquals(1, history.day(today)!!.completedCount)

        // A day inside the window is kept on its own merits.
        history.recordCompletion(key(6), 15L)
        assertEquals(1, history.day(key(6))!!.completedCount)
    }

    @Test
    fun `an unparseable day key writes nothing and asks nothing`() {
        history.recordCompletion("not-a-date", 15L)
        history.ensureDay("")

        assertTrue(history.recent("not-a-date").isEmpty())
    }

    @Test
    fun `a corrupt record reads as no record instead of throwing`() {
        context.getSharedPreferences(QuestHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(today, "3").apply()

        assertNull(history.day(today))
    }
}
