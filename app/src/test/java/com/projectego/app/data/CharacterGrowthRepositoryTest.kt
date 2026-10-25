package com.projectego.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.CharacterGrowthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The books the growth dashboard reads.
 *
 * Two things have to be true for the screen to be honest: a day pays once per
 * kind however many times it is recorded, and the "counted today" flag is about
 * the day the point was earned rather than the last time anything was written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CharacterGrowthRepositoryTest {

    private lateinit var context: Context
    private lateinit var growth: CharacterGrowthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("project_ego_character_growth", Context.MODE_PRIVATE)
            .edit().clear().apply()
        growth = CharacterGrowthRepository(context)
    }

    @Test
    fun `an empty ledger is stage one with nothing counted`() {
        val ledger = growth.ledger("2026-10-17")

        assertEquals(0, ledger.points)
        assertEquals(CharacterGrowth.Stage.ONE, ledger.stage)
        assertEquals(0, ledger.questDays)
        assertFalse(ledger.questCountedToday)
        assertFalse(ledger.focusCountedToday)
        assertFalse(ledger.promiseCountedToday)
    }

    @Test
    fun `one day pays each kind once, and each lands on its own counter`() {
        growth.recordQuestDay("2026-10-17")
        growth.recordQuestDay("2026-10-17")
        growth.recordFocusDay("2026-10-17")
        growth.recordPromiseDay("2026-10-17")

        val ledger = growth.ledger("2026-10-17")
        assertEquals(3, ledger.points)
        assertEquals(CharacterGrowth.Stage.ONE, ledger.stage)
        assertEquals(1, ledger.questDays)
        assertEquals(1, ledger.focusDays)
        assertEquals(1, ledger.promiseDays)
        assertTrue(ledger.questCountedToday)
        assertTrue(ledger.focusCountedToday)
        assertTrue(ledger.promiseCountedToday)
    }

    @Test
    fun `a new day pays again, and the old day stops reading as today`() {
        growth.recordQuestDay("2026-10-16")
        growth.recordQuestDay("2026-10-17")

        assertEquals(2, growth.ledger("2026-10-17").questDays)
        assertTrue(growth.ledger("2026-10-17").questCountedToday)
        assertFalse(growth.ledger("2026-10-18").questCountedToday)
    }

    @Test
    fun `seven counted days move the form to the next stage`() {
        for (day in 1..7) {
            growth.recordFocusDay("2026-10-%02d".format(day))
        }

        val ledger = growth.ledger("2026-10-07")
        assertEquals(7, ledger.points)
        assertEquals(CharacterGrowth.Stage.TWO, ledger.stage)
    }
}