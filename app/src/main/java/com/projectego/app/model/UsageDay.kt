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
    val perAppMinutes: Map<String, Long>,
    /**
     * Everything ALIVE saw in the foreground, including the apps it never
     * blocks. [totalMinutes] is only the part that spends the allowance, so on
     * a day spent mostly working or chatting the two differ by hours - and a
     * card that shows only the first is not a picture of the day, it is a
     * picture of the diet.
     *
     * Null means the day was recorded before this figure existed, not that
     * nothing else was used. A day cannot be claimed to have zero of something
     * that was never counted.
     */
    val screenMinutes: Long? = null
) {
    /** A day with no allowance at all: recovery, not achievement. */
    val isZeroEntertainmentDay: Boolean
        get() = limitMinutes <= 0L
}
