package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The usage-access note appears once and leaves once.
 *
 * The guard ticks every five seconds, so the transition rule is what keeps a
 * missing grant from becoming a notification every five seconds - and what keeps
 * the note from staying on after the person has turned access back on.
 */
class UsageAccessNotePolicyTest {

    @Test
    fun `a missing grant is announced once`() {
        assertEquals(
            UsageAccessNotePolicy.Action.SHOW,
            UsageAccessNotePolicy.action(shown = false, granted = false)
        )
    }

    @Test
    fun `a grant that is still missing says nothing new`() {
        assertEquals(
            UsageAccessNotePolicy.Action.NONE,
            UsageAccessNotePolicy.action(shown = true, granted = false)
        )
    }

    @Test
    fun `a restored grant takes the note down`() {
        assertEquals(
            UsageAccessNotePolicy.Action.CLEAR,
            UsageAccessNotePolicy.action(shown = true, granted = true)
        )
    }

    @Test
    fun `a grant that was never missing has nothing to clear`() {
        assertEquals(
            UsageAccessNotePolicy.Action.NONE,
            UsageAccessNotePolicy.action(shown = false, granted = true)
        )
    }
}
