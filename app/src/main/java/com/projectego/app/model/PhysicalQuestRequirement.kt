package com.projectego.app.model

/**
 * What a physical quest must prove before its reward leaves the bank.
 *
 * Pure data: no Android runtime, no clock, no storage. Lives in `model/` so
 * the UI copy layer can read it without reaching into `domain/`.
 */
data class PhysicalQuestRequirement(
    /** Minimum steps per committed minute; null = movement is not measured. */
    val minStepsPerMinute: Int?,
    /** Maximum steps per committed minute (stillness); null = stillness is not measured. */
    val maxStepsPerMinute: Int?,
    /** Whether no entertainment app may be open during the attempt window. */
    val checkDistractions: Boolean,
    /** Packages that may stay open even while [checkDistractions] is true. */
    val distractionExemptPackages: Set<String> = emptySet()
) {
    val needsSteps: Boolean get() = minStepsPerMinute != null || maxStepsPerMinute != null
    val hasAnyCheck: Boolean get() = needsSteps || checkDistractions

    companion object {
        val NONE = PhysicalQuestRequirement(null, null, false)
    }
}
