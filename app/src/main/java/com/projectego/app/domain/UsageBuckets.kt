package com.projectego.app.domain

/**
 * The three lenses on one day, and the arithmetic that keeps them honest.
 *
 * `Hiburan` is what the allowance is spent on, `Bebas` is what ALIVE never
 * blocks, and `Total` is the two added together - the whole day ALIVE can see.
 * The first two are disjoint by construction and the third is their sum, which
 * is exactly the trap: a package can appear in both lists (an entertainment app
 * whose time was partly swallowed by a focus session has its charged minutes in
 * one and its exempt minutes in the other), and a total that *replaced* rows
 * instead of adding them would report a smaller day than the person lived.
 *
 * No Context and no clock, so this is testable on plain JVM.
 */
object UsageBuckets {

    /**
     * The three lenses, in the order their buttons are drawn.
     *
     * Kept as an enum rather than three booleans because the chart panel holds it
     * as an index and both of its pickers (the range and this one) work the same
     * way: one number in the render state, one radio group on screen.
     */
    enum class Bucket { ENTERTAINMENT, UNBLOCKED, TOTAL }

    /** The rows one lens shows: the charged day, the free one, or both added. */
    fun rows(
        charged: List<Pair<String, Long>>,
        unblocked: List<Pair<String, Long>>,
        bucket: Bucket
    ): List<Pair<String, Long>> = when (bucket) {
        Bucket.ENTERTAINMENT -> charged
        Bucket.UNBLOCKED -> unblocked
        Bucket.TOTAL -> merge(charged, unblocked)
    }

    /** One row per package, minutes summed where a package is in both lists. */
    fun merge(
        charged: List<Pair<String, Long>>,
        unblocked: List<Pair<String, Long>>
    ): List<Pair<String, Long>> {
        val byPackage = LinkedHashMap<String, Long>()
        (charged + unblocked).forEach { (packageName, minutes) ->
            byPackage[packageName] = (byPackage[packageName] ?: 0L) + minutes
        }
        return byPackage.map { (packageName, minutes) -> packageName to minutes }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
    }

    /** Minutes a lens shows, which is what its donut centre states. */
    fun totalMinutes(rows: List<Pair<String, Long>>): Long = rows.sumOf { it.second }
}
