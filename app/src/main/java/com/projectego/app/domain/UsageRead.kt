package com.projectego.app.domain


/**
 * The day's usage, as an answer that can also say "I could not tell you".
 *
 * `queryEvents` returns an empty list for a day with nothing in it, and it also
 * returns an empty list when Usage Access is missing or the platform refuses the
 * call - so an enforcement pass that only sees numbers cannot tell "nothing was
 * used" from "I know nothing". That difference is the whole of SEC-02: the first
 * is a reason not to lock, the second is not evidence of anything.
 */
sealed interface UsageRead {

    /** The query ran and the numbers are real - including a real zero. */
    data class Success(val report: UsageReport) : UsageRead

    /** Usage Access is not granted (or was revoked while the app was running). */
    data object PermissionUnavailable : UsageRead

    /** The platform was asked and did not answer: null manager, null stream, throw. */
    data object QueryFailed : UsageRead

    /** The query answered but the report could not be built from it. */
    data object ProcessingFailed : UsageRead

    val isAvailable: Boolean get() = this is Success
}

/**
 * What the rest of ALIVE may do with an [UsageRead] that is not [UsageRead.Success].
 *
 * Two rules, both deliberately about *not* inventing data:
 *
 *  - enforcement may only act on what it already knows without the usage log - a
 *    persisted lock and a zero-entertainment day; everything else waits for a
 *    later read rather than assuming an allowance;
 *  - a claim does not unlock on a failed read. The minutes were earned, but
 *    "earned" is not "verified under the limit", and clearing here would hand out
 *    entertainment access that enforcement could not check. Recovery is not lost:
 *    the next successful report clears a latched lock whenever the day is under
 *    its (now higher) limit, which is the only way a spent day can become an
 *    unspent one. That keeps the failure closed without stranding anyone.
 */
object UsageSourcePolicy {

    /** True when an unavailable report still leaves a reason to lock. */
    fun lockFromKnownStateOnly(alreadyLocked: Boolean, zeroDay: Boolean): Boolean =
        alreadyLocked || zeroDay

    /** Whether a finished claim clears the lock, given how the verification read went. */
    fun claimClearsLock(read: UsageRead): Boolean = when (read) {
        is UsageRead.Success ->
            read.report.totalEntertainmentMinutes < read.report.limitMinutes

        // No verification, no unlock. The claim's minutes are real and stay in
        // the limit; a later successful report clears the latch as soon as it can
        // see that the day is under the raised limit.
        else -> false
    }

    /** What a successful read may do to the persisted lock latch. */
    enum class Latch { LOCK, CLEAR, LEAVE }

    /**
     * Whether a report's totals may count against the limit at all.
     *
     * A read whose query window began before midnight describes yesterday: the
     * rollover has already judged the new day, and yesterday's minutes must not
     * be compared with today's allowance. The caller passes the window's day and
     * the day it is deciding for; only the same day may exceed.
     */
    fun countsAsExceeded(
        totalMinutes: Long,
        limitMinutes: Long,
        windowIsToday: Boolean
    ): Boolean = windowIsToday && totalMinutes >= limitMinutes

    /**
     * Usage only grows within a day, so the single way a day can go from spent to
     * not-spent is a raised limit - a Time Bank claim, or the dev test limit. An
     * under-limit read that still finds the latch set is exactly such a raise, and
     * verifying it here is what lets a claim survive a failed verification read.
     *
     * [mayDecide] is false when the numbers must not move the latch: a display
     * adapter filling in for an unreadable day, or a report for a window that is
     * no longer today. No evidence, no transition - not even a clear.
     */
    fun latchAction(exceeded: Boolean, locked: Boolean, mayDecide: Boolean = true): Latch = when {
        !mayDecide -> Latch.LEAVE
        exceeded -> Latch.LOCK
        locked -> Latch.CLEAR
        else -> Latch.LEAVE
    }
}

/**
 * The one place a usage read turns into an [UsageRead], so the mapping from
 * "what the platform did" to "what the app may conclude" exists once and can be
 * tested with a query that throws.
 *
 * The three failure kinds are kept apart on purpose: a refused grant is something
 * the person can fix, a platform that did not answer is transient, and a report
 * that could not be built is a bug with our own numbers - and none of the three is
 * a day with no usage.
 */
object UsageReadGuard {

    fun read(
        permissionGranted: Boolean,
        query: () -> List<Foreground.Span>?,
        build: (List<Foreground.Span>) -> UsageReport,
        log: (String, Throwable) -> Unit = { _, _ -> }
    ): UsageRead {
        if (!permissionGranted) return UsageRead.PermissionUnavailable

        val spans = try {
            query()
        } catch (refused: SecurityException) {
            log("usage access refused during read", refused)
            return UsageRead.PermissionUnavailable
        } catch (failed: RuntimeException) {
            log("usage query failed", failed)
            return UsageRead.QueryFailed
        } ?: return UsageRead.QueryFailed

        return try {
            UsageRead.Success(build(spans))
        } catch (failed: RuntimeException) {
            log("usage report processing failed", failed)
            UsageRead.ProcessingFailed
        }
    }
}
