package com.projectego.app.domain

/**
 * When Guardian is allowed to interrupt.
 *
 * While the accessibility service is off there is no event to listen to, so the
 * only signal left is the usage log - which says what was in front a moment ago
 * rather than what is in front now. The watch therefore remembers the last app
 * that came forward and treats it as the one still in front, which has two
 * consequences this policy exists to bound:
 *
 *  - Polling is cheap but not free, so Guardian looks every few seconds and not
 *    more often.
 *  - An app that is still open is still a decision: the knock repeats while the
 *    person stays in it, spaced by the two cooldowns below. It used to fire only
 *    when an app came forward, so one sitting got exactly one reminder however
 *    long the sitting lasted - which is what made it feel like it had stopped
 *    nagging.
 *
 * The general spacing matters when a person hops between apps, and the per-app
 * spacing matters when they do not. No Context, no clock read, so the whole rule
 * is testable on plain JVM.
 */
object GuardianWatchPolicy {

    /** How often the watch looks at the usage log. */
    const val POLL_MILLIS = 5_000L

    /** How far back the first look sees, so it knows what is already open. */
    const val STARTUP_LOOKBACK_MILLIS = 60_000L

    /** The smallest gap between any two popups. */
    const val GLOBAL_COOLDOWN_MILLIS = 45_000L

    /** How long one app is left alone after it has already been interrupted. */
    const val SAME_APP_COOLDOWN_MILLIS = 90_000L

    data class Decision(val interrupt: Boolean, val packageName: String?)

    /**
     * [foregroundPackage] is the app currently in front - the last one that came
     * forward and has not been replaced. The two timestamps are wall-clock, taken
     * from the same source the caller uses for `now`.
     */
    fun decide(
        now: Long,
        foregroundPackage: String?,
        isEntertainment: Boolean,
        lastInterruptAt: Long,
        lastInterruptedPackage: String?
    ): Decision {
        if (foregroundPackage == null || !isEntertainment) return Decision(false, null)

        val sinceLast = now - lastInterruptAt
        if (sinceLast < GLOBAL_COOLDOWN_MILLIS) return Decision(false, null)

        // One app is re-knocked, but no faster than its own spacing: a sitting is
        // still one decision, it is just a decision that gets repeated while the
        // person keeps choosing the same answer.
        if (foregroundPackage == lastInterruptedPackage &&
            sinceLast < SAME_APP_COOLDOWN_MILLIS
        ) {
            return Decision(false, null)
        }

        return Decision(true, foregroundPackage)
    }
}
