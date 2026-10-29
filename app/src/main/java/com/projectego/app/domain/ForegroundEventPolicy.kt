package com.projectego.app.domain

/**
 * What a window-state event is allowed to change.
 *
 * The guard used to treat every event as a switch to a new app. A pulled shade,
 * a keyboard, the screenshot preview - windows that open *over* the person's
 * app - then replaced the tracked package and ended the periodic tick, and
 * nothing restarted it when the overlay closed because no new window-state
 * event arrives for an app that never left. Enforcement was simply off while
 * the person stayed in the entertainment app.
 *
 * The pill already drew this distinction ([AppClassifier.isTransientWindow]);
 * enforcement now routes by the same rule. Self is separate because ALIVE's own
 * screen genuinely is a place the person went - the lock screen or the app.
 */
object ForegroundEventPolicy {

    enum class Target {
        /** ALIVE's own activity is in front: leave the guarded app behind. */
        SELF,

        /** A window over the app, not a switch away from it: change nothing. */
        TRANSIENT,

        /** A real app switch: tracking and evaluation start from this package. */
        PACKAGE
    }

    fun targetOf(packageName: String, selfPackageName: String): Target = when {
        packageName == selfPackageName -> Target.SELF
        AppClassifier.isTransientWindow(packageName) -> Target.TRANSIENT
        else -> Target.PACKAGE
    }
}
