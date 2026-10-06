package com.projectego.app.model

/**
 * One recorded day of entertainment usage.
 *
 * This is a record, not a decision: nothing in ALIVE's enforcement reads it.
 * It exists so the usage chart can answer "where did the time go" for days
 * that have already passed, which `UsageStatsManager` cannot do — it only ever
 * reports the current day from midnight.
 *
 * [limitMinutes] is stored alongside the total on purpose. A day with
 * `totalMinutes = 0` and `limitMinutes = 120` is a day the user did not spend;
 * the same total with `limitMinutes = 0` is a recovery day where there was
 * nothing to spend. Without the allowance of that day recorded, the two are
 * indistinguishable forever, and a chart would silently present one as the
 * other.
 */
data class UsageDay(
    val date: String,
    val totalMinutes: Long,
    val limitMinutes: Long,
    val perAppMinutes: Map<String, Long>
) {
    /** A day with no allowance at all: recovery, not achievement. */
    val isZeroEntertainmentDay: Boolean
        get() = limitMinutes <= 0L
}
