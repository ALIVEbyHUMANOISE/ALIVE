package com.projectego.app.domain

import com.projectego.app.model.DayType
import com.projectego.app.model.TimeStatus

/**
 * What the expanded pill is allowed to say, decided from the same report the
 * guard and the allowance already use.
 *
 * The panel is a reading of today, not a second calculator: total, remaining
 * and per-app rows all come from [UsageReport], which is the one object the
 * blocking decision is made from. A missing report is represented as missing -
 * the panel says nothing rather than drawing zeroes.
 */
enum class PillMood {
    /** Ordinary day: the character is simply present. */
    NORMAL,

    /** Inside the critical zone, but not closed yet. */
    APPROACHING,

    /** The allowance is gone and the guard is closing entertainment. */
    LIMIT,

    /** A recovery day: entertainment is closed on purpose, not spent. */
    RECOVERY
}

data class PillAppRow(val packageName: String, val minutes: Long)

data class PillPanelModel(
    val mood: PillMood,
    /** Null when there is no readable report - never a fabricated 0. */
    val totalMinutes: Long?,
    val remainingMinutes: Long?,
    /**
     * Used share of today's own allowance, 0..1, or null when a bar would be a
     * guess: no report, no limit, or a recovery day where entertainment is
     * closed rather than exhausted. A reached limit is always 1.
     */
    val progress: Float?,
    /** The loudest apps today, largest first, capped. */
    val rows: List<PillAppRow>,
    /** Apps beyond the cap, so the panel can say the list is not the whole day. */
    val hiddenApps: Int
)

object PillPanelPolicy {

    /** Three rows fit above the action without turning the island into a page. */
    const val MAX_APPS = 3

    fun from(report: UsageReport?, locked: Boolean): PillPanelModel {
        if (report == null) {
            return PillPanelModel(
                mood = PillMood.NORMAL,
                totalMinutes = null,
                remainingMinutes = null,
                progress = null,
                rows = emptyList(),
                hiddenApps = 0
            )
        }

        val recovery = report.dayType == DayType.ZERO_ENTERTAINMENT_DAY ||
            report.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY
        val limit = report.isLimitExceeded ||
            locked ||
            report.timeStatus == TimeStatus.LOCKED
        val mood = when {
            recovery -> PillMood.RECOVERY
            limit -> PillMood.LIMIT
            report.criticalZoneTier >= 1 || report.timeStatus == TimeStatus.CRITICAL ->
                PillMood.APPROACHING

            else -> PillMood.NORMAL
        }

        val ranked = report.entertainmentAppDetails
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
        return PillPanelModel(
            mood = mood,
            totalMinutes = report.totalEntertainmentMinutes,
            remainingMinutes = report.remainingMinutes,
            progress = progressOf(report, recovery, limit),
            rows = ranked.take(MAX_APPS).map { PillAppRow(it.first, it.second) },
            hiddenApps = (ranked.size - MAX_APPS).coerceAtLeast(0)
        )
    }

    /**
     * The bar is drawn from the report's own total and limit - not a second
     * calculation and never a hardcoded allowance. It is hidden when either
     * side cannot be trusted: no limit, a recovery day (closed on purpose),
     * or an unreadable report.
     */
    private fun progressOf(report: UsageReport, recovery: Boolean, limit: Boolean): Float? {
        if (recovery || report.limitMinutes <= 0L) return null
        if (limit) return 1f
        return (report.totalEntertainmentMinutes.toFloat() / report.limitMinutes).coerceIn(0f, 1f)
    }
}
