package com.projectego.app.domain

/**
 * The three lights on the pill, chosen from the same report the guard acts on.
 *
 * The lights are status, not decoration. Exactly one is active:
 *  - YELLOW: an ordinary day.
 *  - RED: the report's own critical zone (approaching), with a pulse; or a
 *    reached limit, steady. Both are red because both are time running out;
 *    motion is the difference, and the accessibility description names it.
 *  - GREEN: a recovery day, where entertainment is closed on purpose.
 *  - NONE: no trustworthy status (no report, no lock). All lights stay dim
 *    rather than claiming a normal day.
 *
 * Priority comes from [PillPanelPolicy], so recovery, limit and approaching are
 * decided in one place. A guard lock with no report is still a limit: the guard
 * is authoritative even when the report is missing.
 */
enum class TrafficLight { NONE, RED, YELLOW, GREEN }

data class TrafficLightState(val active: TrafficLight, val pulse: Boolean = false)

object PillTrafficPolicy {

    fun from(report: UsageReport?, locked: Boolean): TrafficLightState {
        if (report == null) {
            // The guard can know the day is spent while the report is still
            // unreadable; that is a limit, not an unknown.
            return if (locked) TrafficLightState(TrafficLight.RED) else
                TrafficLightState(TrafficLight.NONE)
        }
        return when (PillPanelPolicy.from(report, locked).mood) {
            PillMood.RECOVERY -> TrafficLightState(TrafficLight.GREEN)
            PillMood.LIMIT -> TrafficLightState(TrafficLight.RED)
            PillMood.APPROACHING -> TrafficLightState(TrafficLight.RED, pulse = true)
            PillMood.NORMAL -> TrafficLightState(TrafficLight.YELLOW)
        }
    }

    /**
     * The pulse is motion, so reduced-motion turns it off without changing the
     * status: the red light stays fully coloured.
     */
    fun shouldPulse(state: TrafficLightState, animationsAllowed: Boolean): Boolean =
        state.pulse && animationsAllowed

    /**
     * With motion off, approaching is the one state that would otherwise have
     * no visible cue at all: every light is fully coloured in every state, and
     * a steady red is also what a reached limit looks like. A thin paper ring
     * on the red light is the non-animated equivalent of the pulse. It is not
     * used while the pulse runs, and never dims a light.
     */
    fun shouldRing(state: TrafficLightState, animationsAllowed: Boolean): Boolean =
        state.active == TrafficLight.RED && state.pulse && !animationsAllowed
}
