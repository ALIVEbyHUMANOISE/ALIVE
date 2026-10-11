package com.projectego.app.domain

/**
 * When Guardian is allowed to interrupt.
 *
 * While the accessibility service is off there is no event to listen to, so the
 * only signal left is the usage log - which says what was in front a moment ago
 * rather than what is in front now. That has two consequences this policy exists
 * to bound:
 *
 *  - Polling is cheap but not free, so Guardian looks every few seconds and not
 *    more often.
 *  - A reminder that fires on every glance at the log becomes a flicker, so one
 *    app gets one interruption, and interruptions in general are spaced out.
 *
 * It also never fires for the same app twice in a row inside the cooldown, which
 * is what stops "opened and closed twice" turning into two popups.
 *
 * No Context, no clock read, so the whole rule is testable on plain JVM.
 */
object GuardianWatchPolicy {

    /** How often the watch looks at the usage log. */
    const val POLL_MILLIS = 5_000L

    /** How far back one look sees. Slightly longer than the poll, so nothing slips between. */
    const val LOOKBACK_MILLIS = POLL_MILLIS + 1_000L

    /** The smallest gap between any two popups. */
    const val GLOBAL_COOLDOWN_MILLIS = 90_000L

    /** How long one app is left alone after it has already been interrupted. */
    const val SAME_APP_COOLDOWN_MILLIS = 3 * 60 * 1_000L

    data class Decision(val interrupt: Boolean, val packageName: String?)

    /**
     * [foregroundPackage] is the app that came to the front in the last look, and
     * null when nothing did. The two timestamps are wall-clock, taken from the
     * same source the caller uses for `now`.
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

        // An app that was already interrupted is left alone for longer than the
        // general spacing: closing and reopening the same app is one sitting, not
        // two decisions.
        if (foregroundPackage == lastInterruptedPackage &&
            sinceLast < SAME_APP_COOLDOWN_MILLIS
        ) {
            return Decision(false, null)
        }

        return Decision(true, foregroundPackage)
    }
}
