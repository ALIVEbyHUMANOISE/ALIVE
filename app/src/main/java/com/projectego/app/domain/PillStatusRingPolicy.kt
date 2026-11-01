package com.projectego.app.domain

/**
 * What the pill's single ring shows, given the state the three lights used to.
 *
 * This is a renderer's policy, not a second source of truth: [PillTrafficPolicy]
 * still decides *which* status the day is in, and every meaning survives the
 * move from three dots to one ring -
 *
 *  - green: an ordinary day, which is a safe day,
 *  - yellow, pulsing: approaching the limit,
 *  - red, steady: the limit is reached,
 *  - mint: a recovery day - safe, but not the same hue as an ordinary day,
 *  - grey: no trustworthy status.
 *
 * With motion off the pulse is replaced by a static inner ring, exactly as the
 * lights did: the approaching state must never become invisible just because
 * the device asked for less motion.
 */
data class RingSpec(
    val color: Int,
    /** 0..1 halo strength at rest; the pulse moves around this. */
    val glow: Float,
    /** True when the ring's glow should breathe. */
    val pulsing: Boolean,
    /** True when reduced motion is the reason there is no pulse. */
    val ringed: Boolean
)

object PillStatusRingPolicy {

    private const val NORMAL_GLOW = 0.30f
    private const val LIMIT_GLOW = 0.52f
    private const val APPROACHING_GLOW = 0.64f

    fun of(state: TrafficLightState, animationsAllowed: Boolean): RingSpec =
        when (state.active) {
            TrafficLight.NONE -> RingSpec(
                color = PillTheme.RING_UNKNOWN_COLOR,
                glow = 0f,
                pulsing = false,
                ringed = false
            )

            TrafficLight.YELLOW -> RingSpec(
                color = PillTheme.RING_NORMAL_COLOR,
                glow = NORMAL_GLOW,
                pulsing = false,
                ringed = false
            )

            TrafficLight.GREEN -> RingSpec(
                color = PillTheme.RING_RECOVERY_COLOR,
                glow = NORMAL_GLOW,
                pulsing = false,
                ringed = false
            )

            // Approaching is a warning, not yet a wall: the pulsing red is now
            // the pulsing yellow, so it reads ahead of the limit's steady red.
            // The guard can also know the day is spent while the report is
            // unreadable: that arrives here as RED without a pulse, and reads
            // as a limit.
            TrafficLight.RED -> if (state.pulse) {
                RingSpec(
                    color = PillTheme.RING_CAUTION_COLOR,
                    glow = APPROACHING_GLOW,
                    pulsing = animationsAllowed,
                    ringed = !animationsAllowed
                )
            } else {
                RingSpec(
                    color = PillTheme.RING_LIMIT_COLOR,
                    glow = LIMIT_GLOW,
                    pulsing = false,
                    ringed = false
                )
            }
        }
}
