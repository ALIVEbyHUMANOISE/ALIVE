package com.projectego.app.domain

import com.projectego.app.model.DayType
import com.projectego.app.model.TimeStatus

/**
 * When ALIVE is allowed to say "you are running low".
 *
 * Deliberately reads `remainingMinutes` and never `criticalZoneTier`, for two
 * reasons that are both visible in `UsageTracker`:
 *
 *  1. In dev-test mode the ladder collapses to `if (isExceeded) 5 else 1`
 *     (`UsageTracker` :100-101), so `tier >= 1` is true on the very first
 *     minute of a dev day. A tier-based trigger would interrupt immediately.
 *  2. The ladder is written in absolute minutes against an implicit 120
 *     (:103-110). `tier >= 1` means "30 minutes left" only while the allowance
 *     happens to be 120; the moment the allowance changes it means something
 *     else, silently.
 *
 * No `Context` and no clock, so this is testable on plain JVM.
 */
object CriticalAlertPolicy {

    /** The window, in minutes of the day's own allowance, worth a note about. */
    const val REMAINING_WINDOW_MINUTES = 30L

    fun shouldShow(report: UsageReport, focusActive: Boolean): Boolean = when {
        // A running Focus session is the user doing the thing instead; the
        // entertainment ladder is not even consulted while it is active.
        focusActive -> false

        // The lock already speaks for itself, louder.
        report.timeStatus == TimeStatus.LOCKED -> false

        // A recovery day has no allowance to run short of.
        report.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY -> false
        report.dayType == DayType.ZERO_ENTERTAINMENT_DAY -> false

        // Nothing to approach.
        report.limitMinutes <= 0L -> false

        // Zero and negative belong to the lock, not to a warning. Leaving the
        // window downward must not re-arm the alert at the last minute.
        report.remainingMinutes <= 0L -> false

        else -> report.remainingMinutes <= REMAINING_WINDOW_MINUTES
    }
}
