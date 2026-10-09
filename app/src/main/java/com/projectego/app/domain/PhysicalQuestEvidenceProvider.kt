package com.projectego.app.domain

/**
 * Reads the device signals a physical quest is verified against.
 *
 * Both methods return null for "this signal cannot be measured here", which
 * is a fact the verifier treats separately from "measured and wrong".
 */
interface PhysicalQuestEvidenceProvider {

    /** Cumulative step count, or null when there is no sensor or no permission. */
    fun readStepCount(): Int?

    /**
     * Distinct entertainment packages seen in the foreground between the two
     * timestamps, or null when usage data is unavailable.
     */
    fun entertainmentPackagesBetween(startMillis: Long, endMillis: Long): List<String>?

    companion object {
        /** A provider that can measure nothing; the honest fallback. */
        val Unavailable: PhysicalQuestEvidenceProvider = object : PhysicalQuestEvidenceProvider {
            override fun readStepCount(): Int? = null
            override fun entertainmentPackagesBetween(startMillis: Long, endMillis: Long): List<String>? = null
        }
    }
}
