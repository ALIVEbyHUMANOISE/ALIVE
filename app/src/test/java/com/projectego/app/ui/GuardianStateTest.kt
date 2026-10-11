package com.projectego.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The four states, and the one precedence that decides them.
 *
 * Guardian is not a second state machine: it is the guard the app already had,
 * with a name and a face. So the only thing worth testing here is that the three
 * facts it is handed always land on the right state - in particular that a day
 * which actually went unguarded is never quietly reported as merely degraded.
 */
class GuardianStateTest {

    private fun state(off: Boolean, gap: Boolean, repaired: Boolean) =
        Guardian.stateOf(protectionOff = off, gapOpen = gap, repairedToday = repaired)

    @Test
    fun `protection off on a day that owed nothing is degraded, not recovery`() {
        assertEquals(GuardianState.DEGRADED, state(off = true, gap = false, repaired = false))
    }

    @Test
    fun `a marked day outranks the service being off`() {
        // Both facts are true at once; the cost is what the person needs to see.
        assertEquals(GuardianState.RECOVERY_REQUIRED, state(off = true, gap = true, repaired = false))
    }

    @Test
    fun `a paid repair reads as restored, and a quiet day reads as simply on watch`() {
        assertEquals(GuardianState.RESTORED, state(off = false, gap = false, repaired = true))
        assertEquals(GuardianState.ACTIVE, state(off = false, gap = false, repaired = false))
    }

    @Test
    fun `every state has its own words, and only the broken ones have an action`() {
        GuardianState.values().forEach { state ->
            assertNotNull(Guardian.headlineFor(state))
            assertNotNull(Guardian.bodyFor(state))
            assertNotNull(Guardian.noteFor(state))
            assertNotNull(Guardian.descriptionFor(state))
            assertNotNull(Guardian.spriteFor(state))
        }

        assertNull("nothing to press while the watch is whole", Guardian.actionFor(GuardianState.ACTIVE))
        assertNull("nothing to press once it is restored", Guardian.actionFor(GuardianState.RESTORED))
        assertNotNull(Guardian.actionFor(GuardianState.DEGRADED))
        assertNotNull(Guardian.actionFor(GuardianState.RECOVERY_REQUIRED))
    }

    @Test
    fun `the two halves of the watch do not share a face`() {
        val sprites = GuardianState.values().map { Guardian.spriteFor(it) }
        assertEquals("four states, four sprites", sprites.size, sprites.toSet().size)
    }
}
