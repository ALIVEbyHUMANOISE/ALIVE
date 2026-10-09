package com.projectego.app.domain

import com.projectego.app.model.PhysicalQuestEvidence
import com.projectego.app.model.PhysicalQuestRequirement

/**
 * The signals gate for physical quests: evidence in, decision out.
 *
 * Pure and stateless, like [PhysicalQuestVerifier]. The reason strings are
 * engine-only - the UI never renders them; it maps them onto an honest
 * family (see AliveStates.classifyFailure).
 */
object PhysicalQuestSignalsVerifier {

    sealed class Result {
        data class Passed(val reason: String) : Result()
        data class Failed(val reason: String) : Result()

        /** The attempt's requirement has nothing to check, or the time gate already failed. */
        object NotApplicable : Result()
    }

    fun verify(
        requirement: PhysicalQuestRequirement,
        evidence: PhysicalQuestEvidence,
        committedMinutes: Long
    ): Result {
        val failures = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var stepsPerMinute: Long? = null

        if (requirement.needsSteps) {
            val started = evidence.stepsAtStart
            val declared = evidence.stepsAtDeclare
            when {
                started == null -> notes.add("Steps not measurable on this device")
                declared == null -> failures.add("Step counter could not be read when the mission ended")
                declared < started -> failures.add("Step counter reset during the mission (device restarted)")
                else -> {
                    // The divisor is the commitment, not the elapsed clock: the
                    // requirement is scaled to what the quest promised to take.
                    stepsPerMinute = (declared - started).toLong() / committedMinutes
                    val min = requirement.minStepsPerMinute
                    val max = requirement.maxStepsPerMinute
                    when {
                        min != null && stepsPerMinute < min -> failures.add(
                            "$stepsPerMinute steps per committed minute measured, $min required " +
                                "(${declared - started} steps over $committedMinutes committed minutes)"
                        )
                        max != null && stepsPerMinute > max -> failures.add(
                            "$stepsPerMinute steps per committed minute measured, $max allowed " +
                                "(${declared - started} steps over $committedMinutes committed minutes)"
                        )
                        else -> notes.add(
                            "${declared - started} steps measured over $committedMinutes committed minutes"
                        )
                    }
                }
            }
        }

        if (requirement.checkDistractions) {
            val packages = evidence.distractionPackages
            if (packages == null) {
                notes.add("App usage could not be checked for this window")
            } else {
                val offending = packages.filter { it !in requirement.distractionExemptPackages }
                if (offending.isNotEmpty()) {
                    failures.add("Entertainment app open during the mission: ${offending.joinToString(", ")}")
                }
            }
        }

        return if (failures.isNotEmpty()) {
            Result.Failed(failures.joinToString("; "))
        } else {
            Result.Passed(notes.joinToString("; "))
        }
    }
}
