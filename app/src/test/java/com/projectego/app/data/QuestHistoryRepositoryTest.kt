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
 * Two rules this file exists to hold. The first is the one every day-scoped
 * store in ALIVE already obeys: a day that was never watched is not a day that
 * was idle, so a log that cannot tell those apart quietly flatters. The second
 * is new with the history page - a finished mission is kept under the words it
 * had when it paid, because the person can rewrite their own mission afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var history: QuestHistoryRepository

    /** A fixed mid-month day, so the window arithmetic has no surprises. */
    private val today = "2026-10-25"

    private fun key(offsetDays: Long) = LocalDate.parse(today).minusDays(offsetDays).toString()

    private fun record(
        attemptId: String = "attempt-1",
        questId: String = "quest_walk_20",
        title: String = "Walk 20 Minutes",
        minutes: Long = 15L,
        day: String = today,
        completedAt: Long = 1_000L
    ) = history.recordCompletion(day, attemptId, questId, title, minutes, completedAt)

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
        assertTrue(day.completions.isEmpty())
    }

    @Test
    fun `a finished mission is kept with its name and the minutes it paid`() {
        record(
            attemptId = "a1",
            questId = "quest_walk_20",
            title = "Walk 20 Minutes",
            minutes = 15L,
            completedAt = 1_000L
        )
        record(
            attemptId = "a2",
            questId = "custom.own",
            title = "Beresin kamar",
            minutes = 20L,
            completedAt = 2_000L
        )

        val day = history.day(today)!!
        assertEquals(2, day.completedCount)
        assertEquals(35L, day.earnedMinutes)
        assertEquals(
            listOf("Walk 20 Minutes", "Beresin kamar"),
            day.completions.map { it.title }
        )
        assertEquals(listOf(15L, 20L), day.completions.map { it.minutes })
    }

    @Test
    fun `the day is listed in the order the missions were finished`() {
        record(attemptId = "late", title = "Second", completedAt = 5_000L)
        record(attemptId = "early", title = "First", completedAt = 1_000L)

        assertEquals(
            listOf("First", "Second"),
            history.day(today)!!.completions.map { it.title }
        )
    }

    @Test
    fun `the same attempt cannot be recorded twice`() {
        record(attemptId = "a1", minutes = 15L)
        record(attemptId = "a1", minutes = 15L)

        val day = history.day(today)!!
        assertEquals("a retry must overwrite its own row, not add one", 1, day.completedCount)
        assertEquals(15L, day.earnedMinutes)
    }

    @Test
    fun `a day recorded before names were kept still knows what it earned`() {
        // The shape the previous build wrote: totals in the day key, no rows.
        context.getSharedPreferences(QuestHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(today, "2|30").apply()

        val day = history.day(today)!!
        assertEquals(2, day.completedCount)
        assertEquals(30L, day.earnedMinutes)
        assertTrue("names were never stored for it", day.completions.isEmpty())
    }

    @Test
    fun `a legacy day and a newly named mission add up instead of replacing each other`() {
        context.getSharedPreferences(QuestHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(today, "1|15").apply()
        record(attemptId = "a2", title = "Beresin kamar", minutes = 20L)

        val day = history.day(today)!!
        assertEquals(2, day.completedCount)
        assertEquals(35L, day.earnedMinutes)
        assertEquals(1, day.completions.size)
    }

    @Test
    fun `a payout filed under yesterday lands on yesterday, with its name`() {
        record(day = key(1), title = "Beresin kamar")

        assertEquals("Beresin kamar", history.day(key(1))!!.completions.single().title)
        assertNull("today earned nothing of its own", history.day(today))
    }

    @Test
    fun `a title cannot break the record it is stored in`() {
        // Characters that mean something to the keys and fields around it: the
        // row separator, the marker's separator, a date shape.
        val hostile = "A|B#2026-10-01"
        record(attemptId = "a1", title = hostile)

        assertEquals(hostile, history.day(today)!!.completions.single().title)
    }

    @Test
    fun `a title cannot forge a field separator`() {
        val separator = Character.toString(0x1F)
        record(attemptId = "a1", title = "Rapikan${separator}meja")

        val stored = history.day(today)!!.completions.single()
        assertEquals("Rapikan meja", stored.title)
        assertEquals("the minutes still parse", 15L, stored.minutes)
    }

    @Test
    fun `the window is seven days counted back from today, newest first`() {
        val rows = history.recent(today)

        assertEquals(7, rows.size)
        assertEquals(
            listOf("2026-10-25", "2026-10-24", "2026-10-23", "2026-10-22", "2026-10-21", "2026-10-20", "2026-10-19"),
            rows.map { it.dayKey }
        )
    }

    @Test
    fun `a day older than the window is dropped along with its missions`() {
        history.ensureDay(today)
        record(day = key(7), attemptId = "old", title = "Old mission")

        assertNull("the eighth day is not kept", history.day(key(7)))
        assertTrue(history.completions(key(7)).isEmpty())
        assertEquals(0, history.day(today)!!.completedCount)

        // A day inside the window is kept on its own merits.
        record(day = key(6), attemptId = "inside")
        assertEquals(1, history.day(key(6))!!.completedCount)
    }

    @Test
    fun `an unparseable day key writes nothing and asks nothing`() {
        history.recordCompletion("not-a-date", "a1", "quest_walk_20", "Walk", 15L, 1_000L)
        history.recordCompletion(today, "", "quest_walk_20", "Walk", 15L, 1_000L)
        history.ensureDay("")

        assertTrue(history.recent("not-a-date").isEmpty())
        assertNull(history.day(today))
    }

    @Test
    fun `a corrupt marker reads as nothing instead of throwing`() {
        context.getSharedPreferences(QuestHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(today, "3").apply()

        val day = history.day(today)!!
        assertEquals(0, day.completedCount)
        assertEquals(0L, day.earnedMinutes)
    }
}
