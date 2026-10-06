package com.projectego.app.ui

import com.projectego.app.R
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The mark table, with no Android runtime.
 *
 * A mark is a promise that a row can be recognised without reading it, so the
 * table has to stay one-to-one: two activities sharing a mark would make the
 * shape lie about which one a row is.
 */
class PixelMarksTest {

    @Test
    fun `every focus activity has its own mark`() {
        val marks = FocusActivityType.values().map { PixelMarks.focus(it) }
        assertEquals(marks.size, marks.distinct().size)
    }

    @Test
    fun `every quest category has a mark and only OTHER borrows one`() {
        val marks = QuestCategory.values().map { PixelMarks.quest(it) }
        assertEquals(marks.size, marks.distinct().size)
        assertEquals(R.drawable.alive_accent_star, PixelMarks.quest(QuestCategory.OTHER))
    }

    @Test
    fun `the same verb carries the same mark across focus and quests`() {
        assertEquals(PixelMarks.focus(FocusActivityType.STUDY), PixelMarks.quest(QuestCategory.STUDY))
        assertEquals(PixelMarks.focus(FocusActivityType.CODING), PixelMarks.quest(QuestCategory.CODING))
        assertEquals(PixelMarks.focus(FocusActivityType.CREATIVE), PixelMarks.quest(QuestCategory.CREATIVE))
        assertEquals(PixelMarks.focus(FocusActivityType.WRITING), PixelMarks.quest(QuestCategory.WRITING))
    }

    @Test
    fun `reading and studying are not the same picture`() {
        assertNotEquals(PixelMarks.quest(QuestCategory.READING), PixelMarks.quest(QuestCategory.STUDY))
    }

    @Test
    fun `every category can be described out loud`() {
        QuestCategory.values().forEach { category ->
            assertNotEquals(0, PixelMarks.description(category))
        }
    }
}
