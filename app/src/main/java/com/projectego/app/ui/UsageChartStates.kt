package com.projectego.app.ui

import androidx.annotation.StringRes
import com.projectego.app.R
import com.projectego.app.model.UsageDay
import java.time.LocalDate

/** Which stretch of days the chart is showing. */
enum class ChartRange { TODAY, LAST_7_DAYS, THIS_MONTH }

/**
 * How a single day is represented. These must differ in shape and wording,
 * never in hue alone.
 */
enum class DayBarKind {
    /** ALIVE never recorded this day. Not the same as a zero. */
    MISSING,

    /** Recorded, allowance existed, nothing was spent. */
    ZERO,

    /** Some of the allowance used. */
    SPENT,

    /** The allowance was reached or passed. */
    FULL,

    /** A recovery day: there was no allowance to use. */
    RECOVERY
}

data class DayBar(
    val date: String,
    val minutes: Long,
    /** That day's own allowance, kept with the bar so the two can be stated together. */
    val limitMinutes: Long,
    val kind: DayBarKind,
    /** Share of that day's own allowance, 0f..1f. */
    val fraction: Float
)

/**
 * The maths behind the usage chart.
 *
 * No clock, no context, no engine reads: today's date is passed in, so every
 * rule here is testable on plain JVM.
 */
object UsageChartStates {

    /**
     * How much of a day's own allowance was used, as a share of it.
     *
     * Scaled per day rather than against one shared axis on purpose: that is
     * what keeps the promise "a full bar means you used your allowance" true
     * for every day shown, whatever the allowance was that day. A shared axis
     * cannot keep it, because one day that went over would shrink every other
     * day's bar. Going over clamps at full; the real minutes are still counted
     * in the totals, so clamping is disclosure and not a quiet truncation.
     */
    fun fractionOf(minutes: Long, limitMinutes: Long): Float = when {
        limitMinutes <= 0L || minutes <= 0L -> 0f
        else -> (minutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
    }

    fun kindOf(day: UsageDay?): DayBarKind = when {
        day == null -> DayBarKind.MISSING
        day.isZeroEntertainmentDay -> DayBarKind.RECOVERY
        day.totalMinutes <= 0L -> DayBarKind.ZERO
        day.totalMinutes >= day.limitMinutes -> DayBarKind.FULL
        else -> DayBarKind.SPENT
    }

    fun barFor(date: String, day: UsageDay?): DayBar = DayBar(
        date = date,
        minutes = day?.totalMinutes ?: 0L,
        limitMinutes = day?.limitMinutes ?: 0L,
        kind = kindOf(day),
        fraction = day?.let { fractionOf(it.totalMinutes, it.limitMinutes) } ?: 0f
    )

    /**
     * One block of hours on the today chart.
     *
     * The day is drawn in [HOURS_PER_BAR]-hour blocks rather than as twenty-four
     * bars: twenty-four of them do not fit one row on a phone, and a chart that
     * wraps onto a second row reads as two different days rather than as one
     * sequence. Three hours is also the finest division that still says
     * something worth knowing - "nothing between nine and noon" is a fact about
     * a morning, "nothing at 09:40" is not.
     *
     * [fraction] is deliberately measured against the *daily* allowance and not
     * against the busiest hour, so a full bar keeps meaning what it means on
     * every other chart in the app: you used your day up. Scaling to the busiest
     * hour would make every quiet day look identical to a heavy one.
     */
    data class HourBar(
        val startHour: Int,
        val minutes: Long,
        val fraction: Float,
        val kind: DayBarKind
    )

    fun hourBars(
        millisPerHour: LongArray,
        limitMinutes: Long,
        currentHour: Int
    ): List<HourBar> = (0 until HOURS_PER_DAY step HOURS_PER_BAR).map { start ->
        val minutes = (start until start + HOURS_PER_BAR)
            .sumOf { millisPerHour.getOrElse(it) { 0L } } / 60_000L
        val kind = when {
            // The whole block is still ahead. Not a zero: a zero is a fact about
            // the past, and three hours from now are not a fact about anything.
            start > currentHour -> DayBarKind.MISSING
            minutes <= 0L -> DayBarKind.ZERO
            limitMinutes > 0L && minutes >= limitMinutes -> DayBarKind.FULL
            else -> DayBarKind.SPENT
        }
        HourBar(start, minutes, fractionOf(minutes, limitMinutes), kind)
    }

    /** "00–03": the label under a block, short enough for its own slot. */
    fun hourBlockLabel(startHour: Int): String =
        "%s–%02d".format(hourLabel(startHour), startHour + HOURS_PER_BAR)

    /** "00.00–03.00": the same block, spelled out where there is room for it. */
    fun hourBlockDetail(startHour: Int): String =
        "%s.00–%02d.00".format(hourLabel(startHour), startHour + HOURS_PER_BAR)

    /** Whole percent, for a screen that states shares rather than minutes. */
    fun percentOf(minutes: Long, limitMinutes: Long): Int = when {
        limitMinutes <= 0L -> 0
        else -> Math.round(minutes * 100.0 / limitMinutes).toInt()
    }

    /** "09" - the clock, zero padded. */
    fun hourLabel(hour: Int): String = "%02d".format(hour)

    const val HOURS_PER_DAY = 24
    const val HOURS_PER_BAR = 3

    /**
     * One entry per requested date, in order. Dates ALIVE never saw come back
     * as [DayBarKind.MISSING] rather than being dropped, so the chart cannot
     * quietly present a short history as a complete one.
     */
    fun bars(dateKeys: List<String>, recorded: Map<String, UsageDay>): List<DayBar> =
        dateKeys.map { key -> barFor(key, recorded[key]) }

    /**
     * Summed minutes plus how many of those days were actually recorded.
     * Missing days are excluded from the sum and disclosed by the count - they
     * are never imputed and never divided away.
     */
    fun totals(bars: List<DayBar>): Pair<Long, Int> {
        val recorded = bars.filter { it.kind != DayBarKind.MISSING }
        return recorded.sumOf { it.minutes } to recorded.size
    }

    /**
     * The day keys a range covers, oldest first.
     *
     * "Last 7 days" rather than "this week": the app has no week-start concept
     * anywhere, and a calendar week would show five MISSING marks on a Tuesday.
     */
    fun dateKeys(range: ChartRange, today: LocalDate): List<String> = when (range) {
        ChartRange.TODAY -> listOf(today.toString())
        ChartRange.LAST_7_DAYS -> (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
        ChartRange.THIS_MONTH -> {
            val firstDay = today.withDayOfMonth(1)
            (0..java.time.temporal.ChronoUnit.DAYS.between(firstDay, today)).map {
                firstDay.plusDays(it).toString()
            }
        }
    }

    fun expectedDayCount(range: ChartRange, today: LocalDate): Int =
        dateKeys(range, today).size

    /**
     * Copy that states how much of the picture is real. A chart with one bar in
     * it must not look like a month of history.
     */
    @StringRes
    fun coverageCopy(recorded: Int, expected: Int): Int = when {
        recorded <= 0 -> R.string.chart_none
        recorded == 1 -> R.string.chart_just_started
        recorded < expected -> R.string.chart_partial
        else -> R.string.chart_complete
    }

    @StringRes
    fun rangeLabel(range: ChartRange): Int = when (range) {
        ChartRange.TODAY -> R.string.chart_range_today
        ChartRange.LAST_7_DAYS -> R.string.chart_range_week
        ChartRange.THIS_MONTH -> R.string.chart_range_month
    }
}
