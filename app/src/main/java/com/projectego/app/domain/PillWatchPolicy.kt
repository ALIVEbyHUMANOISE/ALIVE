package com.projectego.app.domain

/**
 * What the pill should do with the app in front, as a rule rather than as code
 * buried in a Service.
 *
 * The pill used to learn about app changes only from the accessibility service,
 * which meant that with the guard off it never learned - a pill started for
 * YouTube stayed on screen for fifteen hours. Reading the usage log fixes that,
 * but reading it adds two questions this file answers:
 *
 *  - A log entry older than the pill itself is not news. The pill is raised for
 *    the app that is in front; an older resume belongs to whatever was there
 *    before it, and acting on that evidence would close the pill the moment it
 *    appeared.
 *  - The notification shade is not a place. A system window over the app is not
 *    the person leaving it, and a "different app" seen for a couple of seconds
 *    is usually a dialog, not a switch.
 *  - The same app is not a change, and a real switch is a decision: a
 *    non-entertainment app ends the reading, an entertainment app moves it.
 *
 * Pure: no Context, no clock read, so every rule here is testable on plain JVM.
 */
object PillWatchPolicy {

    /** How long a different app must stay in front before it counts as a switch. */
    const val SETTLE_MILLIS = 5_000L

    sealed interface Decision {
        /** Nothing in the log contradicts the app the pill is already reading. */
        object Keep : Decision

        /** The person is no longer in an entertainment app. */
        object Stop : Decision

        /** The person moved to another entertainment app; read that one now. */
        data class Follow(val packageName: String) : Decision
    }

    /**
     * @param foregroundPackage the last app to come to the front, or null when
     *        the log has nothing recent to say.
     * @param foregroundAt wall-clock time of that event; it is compared with the
     *        moment the pill was raised, because an older event is not evidence
     *        of anything that happened since.
     * @param isTransientWindow the shade, the volume panel or a system dialog -
     *        something drawn over the app, never a place the person went.
     */
    fun decide(
        watchedPackage: String,
        foregroundPackage: String?,
        foregroundAt: Long,
        startedAtWall: Long,
        nowWall: Long,
        isEntertainment: Boolean,
        isTransientWindow: Boolean
    ): Decision = when {
        foregroundPackage == null -> Decision.Keep
        foregroundPackage == watchedPackage -> Decision.Keep
        foregroundAt < startedAtWall -> Decision.Keep
        isTransientWindow -> Decision.Keep
        nowWall - foregroundAt < SETTLE_MILLIS -> Decision.Keep
        !isEntertainment -> Decision.Stop
        else -> Decision.Follow(foregroundPackage)
    }

    /**
     * A window-state change names a *window*, not a place.
     *
     * Keyboards, Game Booster, screenshot previews and system panels raise
     * windows over the app without resuming any activity. Acting on one of
     * those took the pill down while the person was still in TikTok or a game,
     * and the app's re-appearance started the session again at 0 - the
     * "resets by itself" report of 2026-10-23. The activity log is the
     * arbiter: only a package that also resumed is evidence of a switch.
     *
     * A null log is not evidence of leaving either; the service's own periodic
     * probe still takes the pill down if the app really changed.
     */
    fun isConfirmedSwitch(windowPackage: String, lastResumedPackage: String?): Boolean =
        lastResumedPackage != null && lastResumedPackage == windowPackage
}