package com.projectego.app.domain

import com.projectego.app.model.FocusActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seam a self-written mission comes in through.
 *
 * The property that matters is the boring one: with no override - which is
 * every session that exists today - the answer is byte-for-byte the catalog's
 * list. The interesting case is that an override replaces it completely,
 * including apps the catalog would have refused.
 */
class FocusOverrideTest {

    private val study = FocusActivityType.STUDY

    @Test
    fun `with no override the catalog still answers`() {
        assertEquals(
            FocusAppPolicy.getApprovedPackages(study),
            FocusAppPolicy.approvedFor(study, null)
        )
    }

    @Test
    fun `an override replaces the catalog list entirely`() {
        val mine = setOf("com.android.chrome")
        val approved = FocusAppPolicy.approvedFor(study, mine)

        assertEquals(mine, approved)
        assertTrue(approved.contains("com.android.chrome"))
        // The catalog would have refused the browser outright; the person's own
        // list is not a suggestion to be merged with it.
        assertFalse(approved.containsAll(FocusAppPolicy.getApprovedPackages(study)))
    }

    @Test
    fun `an empty override is a choice, not a missing value`() {
        // "No app allowed" is a legitimate mission - reading a book, walking -
        // and it must not silently fall back to the catalog's list.
        val approved = FocusAppPolicy.approvedFor(study, emptySet())
        assertTrue(approved.isEmpty())
    }
}
