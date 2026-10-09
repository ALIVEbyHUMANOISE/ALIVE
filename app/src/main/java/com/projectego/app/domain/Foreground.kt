package com.projectego.app.domain

/**
 * What was in front, as intervals instead of as a stream of events.
 *
 * Pure on purpose: the platform hands ALIVE a log of resumes and pauses, and
 * everything that matters afterwards - the daily total, the hourly chart, the
 * focus-session exemption - is interval arithmetic over that same log. One place
 * turns the log into intervals, and every consumer agrees with every other by
 * construction instead of by luck.
 */
object Foreground {

    /** One stretch a single package was in front. [start] inclusive, [end] exclusive. */
    data class Span(val id: String, val start: Long, val end: Long)

    /**
     * Turns an event stream into spans.
     *
     * [nowMillis] closes whatever is still open: an app in front when this is
     * called has been running until now, and dropping it would make the hour the
     * person is living in the only empty one on the chart.
     */
    fun spans(events: List<HourlyUsage.Event>, nowMillis: Long): List<Span> {
        val spans = ArrayList<Span>()
        val open = HashMap<String, Long>()

        fun close(id: String, endAt: Long) {
            val from = open.remove(id) ?: return
            if (endAt > from) spans.add(Span(id, from, endAt))
        }

        events.sortedBy { it.at }.forEach { event ->
            when (event.kind) {
                HourlyUsage.Kind.RESUMED -> {
                    // One app is in front at a time, so a resume ends whatever was
                    // open before it, whatever the stream claims about that app.
                    open.keys.toList().forEach { close(it, event.at) }
                    open[event.id] = event.at
                }

                HourlyUsage.Kind.PAUSED -> close(event.id, event.at)

                // The screen went dark: nothing is in front any more, even if the
                // app never got its pause.
                HourlyUsage.Kind.SCREEN_OFF -> open.keys.toList().forEach { close(it, event.at) }
            }
        }

        open.keys.toList().forEach { close(it, nowMillis) }
        return spans.sortedBy { it.start }
    }
}
