package com.projectego.app.model

/**
 * What the device could actually measure about a physical attempt.
 *
 * Every field is nullable on purpose: a null is "this signal could not be
 * measured", which is a fact the verifier must handle separately from
 * "measured and wrong". A phone without a step sensor is not a cheater.
 */
data class PhysicalQuestEvidence(
    /** Cumulative step count at the attempt start; null = not measurable then. */
    val stepsAtStart: Int?,
    /** Cumulative step count at the declaration; null = not measurable then. */
    val stepsAtDeclare: Int?,
    /**
     * Distinct entertainment packages seen in the foreground during the
     * attempt window; null = usage data was unavailable.
     */
    val distractionPackages: List<String>?
)
