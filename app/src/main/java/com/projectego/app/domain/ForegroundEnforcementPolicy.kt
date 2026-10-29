package com.projectego.app.domain

/**
 * What the guard should do while a package stays in front.
 *
 * Enforcement used to fire only on a window-state change, so a person who opened
 * an entertainment app just under the limit and stayed there was never asked the
 * question again - the app that is supposed to close at the limit simply kept
 * running because no event arrived. This policy is the answer to "ask again", and
 * it is deliberately made of cheap questions first: a persisted lock, a zero day,
 * a focus session. The expensive authoritative report is only asked for at a
 * controlled cadence, because it is the one thing here that can block the thread
 * the person is typing into.
 *
 * Pure on purpose: the service reads its state, this decides, the service acts.
 */
object ForegroundEnforcementPolicy {

    /** How often the cheap questions are re-asked. */
    const val TICK_MILLIS = 5_000L

    /**
     * How often the authoritative day report may be computed for a package the
     * person is simply sitting in. One minute matches the pill's own freshness
     * window; the price is that crossing the limit inside the app can be noticed
     * up to this late, and each check is a main-thread query (measured 95-825 ms
     * on the phone). Once the lock is persisted the report is never asked for
     * again that day.
     */
    const val REPORT_INTERVAL_MILLIS = 60_000L

    /** Everything the decision is allowed to know, gathered cheaply by the caller. */
    data class State(
        val packageName: String?,
        val screenInteractive: Boolean,
        val isTransient: Boolean,
        val isEntertainment: Boolean,
        val focusActive: Boolean,
        val focusAllowsPackage: Boolean,
        val focusActivityName: String?,
        val alreadyLocked: Boolean,
        val zeroDay: Boolean
    )

    enum class Action {
        /** Nothing to do this tick. */
        IDLE,

        /** Launch the normal lock now, from the persisted lock or a zero day. */
        LOCK_NOW,

        /** Launch the focus lock now; the session no longer allows this package. */
        FOCUS_LOCK,

        /** Ask the authoritative report and lock only if it says the limit is spent. */
        EVALUATE_REPORT
    }

    /**
     * What this tick does, and whether the tick should continue when it does
     * nothing. The caller stops ticking when [Action] launched a lock (the lock
     * screen taking the foreground will also stop it) or when [keepTicking] is
     * false because the person is simply not in an app that can be enforced.
     */
    data class Tick(val action: Action, val keepTicking: Boolean)

    fun decide(state: State, nowMillis: Long, lastReportAtMillis: Long): Tick {
        if (state.packageName == null || state.isTransient) {
            return Tick(Action.IDLE, keepTicking = false)
        }
        if (!state.screenInteractive) {
            // Screen off: nothing to enforce over, and nothing to wake for.
            return Tick(Action.IDLE, keepTicking = false)
        }
        if (state.focusActive) {
            return if (state.focusAllowsPackage) {
                Tick(Action.IDLE, keepTicking = true)
            } else {
                Tick(Action.FOCUS_LOCK, keepTicking = true)
            }
        }
        if (!state.isEntertainment) {
            return Tick(Action.IDLE, keepTicking = false)
        }
        // The persisted lock is the same state the report itself treats as
        // exceeded (`isLimitExceeded = exceeded || isLocked`), and a zero day has
        // no allowance to argue about - neither needs a query.
        if (state.alreadyLocked || state.zeroDay) {
            return Tick(Action.LOCK_NOW, keepTicking = true)
        }
        val reportDue = nowMillis - lastReportAtMillis >= REPORT_INTERVAL_MILLIS
        return Tick(
            action = if (reportDue) Action.EVALUATE_REPORT else Action.IDLE,
            keepTicking = true
        )
    }
}
