package com.projectego.app.data

import com.projectego.app.model.FocusActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The format that carries yesterday's data.
 *
 * The first test is the one that matters: a window written by the version that
 * shipped this morning must still parse - not approximately, exactly - because
 * a dropped window silently un-exempts the minutes it covered.
 */
class FocusWindowCodecTest {

    private val sep = "\u001E"
    private val f = "\u001F"

    @Test
    fun `a record from before the override still parses, with no override`() {
        val decoded = FocusWindowCodec.decode("1000${f}2000${f}STUDY")
        assertEquals(1, decoded.size)
        assertEquals(1000L, decoded[0].start)
        assertEquals(2000L, decoded[0].end)
        assertEquals(FocusActivityType.STUDY, decoded[0].activityType)
        assertNull(decoded[0].approvedOverride)
    }

    @Test
    fun `a record that names its own apps parses with them`() {
        val decoded = FocusWindowCodec.decode("1000${f}2000${f}STUDY${f}com.a,com.b")
        assertEquals(setOf("com.a", "com.b"), decoded[0].approvedOverride)
    }

    @Test
    fun `an empty list of apps is not the same as no list at all`() {
        val decoded = FocusWindowCodec.decode("1000${f}2000${f}STUDY${f}")
        assertEquals(emptySet<String>(), decoded[0].approvedOverride)
    }

    @Test
    fun `a window with no override is still written in three fields`() {
        val encoded = FocusWindowCodec.encode(
            listOf(FocusWindow(1000, 2000, FocusActivityType.STUDY))
        )
        assertEquals("1000${f}2000${f}STUDY", encoded)
    }

    @Test
    fun `one malformed record does not take the others with it`() {
        val decoded = FocusWindowCodec.decode(
            "1000${f}2000${f}STUDY" + sep +
                "900${f}${f}STUDY" + sep +
                "3000${f}4000${f}NONSENSE" + sep +
                "5000${f}6000${f}WORK"
        )
        assertEquals(2, decoded.size)
        assertEquals(FocusActivityType.STUDY, decoded[0].activityType)
        assertEquals(FocusActivityType.WORK, decoded[1].activityType)
    }

    @Test
    fun `nothing in, nothing out`() {
        assertTrue(FocusWindowCodec.decode(null).isEmpty())
        assertTrue(FocusWindowCodec.decode("").isEmpty())
        assertTrue(FocusWindowCodec.decode("   ").isEmpty())
    }
}
