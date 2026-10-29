package com.projectego.app.domain

/**
 * The pill's day-report reads: one at a time, newest wins, nothing after teardown.
 *
 * Reading a day of usage events took 218-499 ms on the phone - longer than a
 * whole morph - so it moved off the main thread. That move needs three rules that
 * are easy to get subtly wrong, and this is the smallest place that can hold all
 * three:
 *
 *  - one read at a time, so the periodic tick cannot queue work behind a slow
 *    query;
 *  - a result may only publish once, so a repeated callback cannot apply twice;
 *  - a result from an older read can never overwrite a newer one, and nothing at
 *    all may publish after the service is gone.
 *
 * Pure on purpose: tokens and booleans, no threads, no Android.
 */
class PillReportSchedule {

    private var nextToken = 0L
    private var inFlight: Long? = null
    private var newestApplied = 0L
    private var closed = false

    /** A token to read the report under, or null when a read is already out. */
    fun request(): Long? {
        if (closed) return null
        if (inFlight != null) return null
        val token = nextToken + 1
        nextToken = token
        inFlight = token
        return token
    }

    /**
     * A read finished: its slot is freed either way, and the answer says whether
     * this result may be published. False for a closed schedule, for a token
     * already applied, and for one older than the newest applied.
     */
    fun complete(token: Long): Boolean {
        if (closed || inFlight != token || token <= newestApplied) return false
        inFlight = null
        newestApplied = token
        return true
    }

    /** Teardown: refuse new reads and drop whatever is still out. */
    fun close() {
        closed = true
        inFlight = null
    }

    /** True while a read is out; the next tick leaves it alone. */
    val isBusy: Boolean get() = inFlight != null
}
