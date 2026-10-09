package com.projectego.app.domain

/**
 * The day, cut into the hours its time actually landed in.
 *
 * The event stream that feeds this is the platform's, but the arithmetic that
 * turns resume/pause pairs into "twenty minutes after nine" is not - and this is
 * the arithmetic a bar chart's honesty rests on. An interval that straddles an
 * hour boundary is split, never rounded: 09:50 to 10:10 is ten minutes in each
 * hour, because the alternative is a chart that quietly moves time between hours
 * and cannot be argued with.
 */
object HourlyUsage {

    const val HOURS = 24

    /** What the platform reported. Only these three things matter here. */
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF }

    /**
     * One foreground event, already filtered to the apps that count, already
     * carrying the package it belongs to.
     */
    data class Event(val at: Long, val kind: Kind, val id: String)

    /**
     * Milliseconds per hour of the day that starts at [dayStartMillis].
     *
     * [nowMillis] closes whatever is still open - an app in front when this is
     * called has been running until now, and dropping it would make the hour the
     * person is living in the only empty one on the chart.
     */
    fun bucket(dayStartMillis: Long, events: List<Event>, nowMillis: Long): LongArray {
        val out = LongArray(HOURS)
        val open = HashMap<String, Long>()

        fun close(id: String, endAt: Long) {
            val from = open.remove(id) ?: return
            if (endAt > from) addSpan(out, dayStartMillis, from, endAt)
        }

        events.sortedBy { it.at }.forEach { event ->
            when (event.kind) {
                Kind.RESUMED -> {
                    // One app is in front at a time, so a resume ends whatever was
                    // open before it, whatever the stream claims about that app.
                    open.keys.toList().forEach { close(it, event.at) }
                    open[event.id] = event.at
                }

                Kind.PAUSED -> close(event.id, event.at)

                // The screen went dark: nothing is in front any more, even if the
                // app never got its pause.
                Kind.SCREEN_OFF -> open.keys.toList().forEach { close(it, event.at) }
            }
        }

        open.keys.toList().forEach { close(it, nowMillis) }
        return out
    }

    private const val HOUR_MILLIS = 3_600_000L

    private fun addSpan(out: LongArray, dayStart: Long, from: Long, to: Long) {
        var cursor = from
        while (cursor < to) {
            if (cursor < dayStart) {
                cursor = dayStart
                continue
            }
            val index = ((cursor - dayStart) / HOUR_MILLIS).toInt()
            if (index >= HOURS) return
            val sliceEnd = minOf(to, dayStart + (index + 1) * HOUR_MILLIS)
            out[index] += sliceEnd - cursor
            cursor = sliceEnd
        }
    }
}
