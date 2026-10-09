package com.projectego.app.domain

import com.projectego.app.model.PhysicalQuestEvidence
import com.projectego.app.model.PhysicalQuestRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signals gate as a pure rule: evidence in, decision out.
 *
 * A null field is "could not be measured", which is a different fact from
 * "measured and wrong" — the gate must skip the first and fail the second.
 */
class PhysicalQuestSignalsVerifierTest {

    private val runRequirement = PhysicalQuestPolicy.requirementFor(
        "quest_run_20",
        com.projectego.app.model.QuestCategory.EXERCISE
    )
    private val stillnessRequirement = PhysicalQuestPolicy.requirementFor(
        "quest_meditate_10",
        com.projectego.app.model.QuestCategory.MINDFULNESS
    )

    private fun verify(
        requirement: PhysicalQuestRequirement,
        evidence: PhysicalQuestEvidence,
        committedMinutes: Long = 20L
    ) = PhysicalQuestSignalsVerifier.verify(requirement, evidence, committedMinutes)

    @Test
    fun `steps exactly at the floor pass`() {
        // 100 steps/min * 20 min = 2000, and no distractions.
        val result = verify(
            runRequirement,
            PhysicalQuestEvidence(0, 2000, emptyList())
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Passed)
        assertTrue((result as PhysicalQuestSignalsVerifier.Result.Passed).reason.contains("2000 steps"))
    }

    @Test
    fun `steps below the floor fail and say the numbers`() {
        val result = verify(
            runRequirement,
            PhysicalQuestEvidence(0, 300, emptyList())
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Failed)
        val reason = (result as PhysicalQuestSignalsVerifier.Result.Failed).reason
        assertTrue(reason.contains("steps"))
        assertTrue(reason.contains("100"))
    }

    @Test
    fun `stillness above the ceiling fails`() {
        // 30 steps/min over a 10-minute meditation: walking, not sitting.
        val result = verify(
            stillnessRequirement,
            PhysicalQuestEvidence(0, 300, emptyList()),
            committedMinutes = 10L
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Failed)
        assertTrue((result as PhysicalQuestSignalsVerifier.Result.Failed).reason.contains("10 allowed"))
    }

    @Test
    fun `stillness with a few steps passes`() {
        val result = verify(
            stillnessRequirement,
            PhysicalQuestEvidence(0, 40, emptyList()),
            committedMinutes = 10L
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Passed)
    }

    @Test
    fun `a counter that went backwards means a reset, not a pass`() {
        val result = verify(
            runRequirement,
            PhysicalQuestEvidence(900, 200, emptyList())
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Failed)
        assertTrue((result as PhysicalQuestSignalsVerifier.Result.Failed).reason.contains("reset"))
    }

    @Test
    fun `a recorded start with no declare read fails rather than skips`() {
        val result = verify(
            runRequirement,
            PhysicalQuestEvidence(100, null, emptyList())
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Failed)
        assertTrue((result as PhysicalQuestSignalsVerifier.Result.Failed).reason.contains("could not be read"))
    }

    @Test
    fun `steps unavailable from the start are skipped but distractions still count`() {
        val passed = verify(
            runRequirement,
            PhysicalQuestEvidence(null, null, emptyList())
        )
        assertTrue(passed is PhysicalQuestSignalsVerifier.Result.Passed)
        assertTrue((passed as PhysicalQuestSignalsVerifier.Result.Passed).reason.contains("not measurable"))

        val failed = verify(
            runRequirement,
            PhysicalQuestEvidence(null, null, listOf("com.instagram.android"))
        )
        assertTrue(failed is PhysicalQuestSignalsVerifier.Result.Failed)
        assertTrue((failed as PhysicalQuestSignalsVerifier.Result.Failed).reason.contains("com.instagram.android"))
    }

    @Test
    fun `a distraction fails and names the package`() {
        val result = verify(
            runRequirement,
            PhysicalQuestEvidence(0, 2000, listOf("com.instagram.android"))
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Failed)
        assertTrue((result as PhysicalQuestSignalsVerifier.Result.Failed).reason.contains("com.instagram.android"))
    }

    @Test
    fun `audio apps are not distractions for meditation`() {
        listOf(
            listOf("com.spotify.music"),
            listOf("com.google.android.youtube"),
            listOf("com.spotify.music", "com.google.android.apps.youtube.music")
        ).forEach { packages ->
            val result = verify(
                stillnessRequirement,
                PhysicalQuestEvidence(0, 20, packages),
                committedMinutes = 10L
            )
            assertTrue("$packages must pass meditation", result is PhysicalQuestSignalsVerifier.Result.Passed)
        }
    }

    @Test
    fun `audio exemption does not cover other entertainment`() {
        val result = verify(
            stillnessRequirement,
            PhysicalQuestEvidence(0, 20, listOf("com.spotify.music", "com.instagram.android")),
            committedMinutes = 10L
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Failed)
    }

    @Test
    fun `usage data unavailable is skipped, not failed`() {
        val result = verify(
            runRequirement,
            PhysicalQuestEvidence(0, 2000, null)
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Passed)
        assertTrue((result as PhysicalQuestSignalsVerifier.Result.Passed).reason.contains("could not be checked"))
    }

    @Test
    fun `a requirement with nothing to check is never the gate itself`() {
        val result = verify(
            PhysicalQuestRequirement.NONE,
            PhysicalQuestEvidence(0, 0, listOf("com.instagram.android"))
        )
        assertTrue(result is PhysicalQuestSignalsVerifier.Result.Passed)
        assertEquals("", (result as PhysicalQuestSignalsVerifier.Result.Passed).reason)
    }
}
