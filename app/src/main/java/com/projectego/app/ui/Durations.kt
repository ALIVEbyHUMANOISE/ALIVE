package com.projectego.app.ui

import android.content.Context
import com.projectego.app.R

/**
 * Minutes, said in the unit a person would actually use.
 *
 * Past an hour the number stops being something anyone converts in their head:
 * "214 minutes" is a sum, "3 jam 34 menit" is a fact about their day. Under an
 * hour, minutes are already the natural unit, and "0 jam 45 menit" would be a
 * worse answer than the problem this solves.
 */
object Durations {

    /** Prose: "45 min", "1 jam 20 menit", "3 jam". */
    fun of(context: Context, minutes: Long): String = when {
        minutes < MINUTES_PER_HOUR -> context.getString(R.string.unit_minutes, minutes)
        minutes % MINUTES_PER_HOUR == 0L ->
            context.getString(R.string.duration_hours, minutes / MINUTES_PER_HOUR)
        else -> context.getString(
            R.string.duration_hours_minutes,
            minutes / MINUTES_PER_HOUR,
            minutes % MINUTES_PER_HOUR
        )
    }

    /**
     * The ring's own figure: "45", "1j20", "3j".
     *
     * No word and no space, because the caption under it already says what is
     * being counted, and the hole is only wide enough for about four characters.
     */
    fun compact(context: Context, minutes: Long): String = when {
        minutes < MINUTES_PER_HOUR -> minutes.toString()
        minutes % MINUTES_PER_HOUR == 0L ->
            context.getString(R.string.duration_hours_short, minutes / MINUTES_PER_HOUR)
        else -> context.getString(
            R.string.duration_hours_minutes_short,
            minutes / MINUTES_PER_HOUR,
            minutes % MINUTES_PER_HOUR
        )
    }

    private const val MINUTES_PER_HOUR = 60L
}
