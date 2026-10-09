package com.projectego.app.domain

/**
 * The time a focus session is allowed to swallow.
 *
 * The rule, whole: a minute of foreground time does not charge the allowance if
 * it falls inside a focus window AND the app it belongs to was on that
 * session's own approved list. Nothing else is exempt, and no other list exists
 * - so the loophole the whole design is defending against cannot exist: social
 * apps, games and browsers are never approved by any focus activity, which
 * means their minutes charge no matter what, service on or service off.
 *
 * Pure on purpose: it is interval arithmetic over [Foreground.Span]s, and the
 * daily total and the hourly chart both pass through it.
 */
object FocusExemption {

    /** One stretch of focus, with the packages that session said were allowed. */
    data class Window(val start: Long, val end: Long, val approved: Set<String>)

    /** Overlap of each span with a window that approves its app, per package. */
    fun exemptedMillis(spans: List<Foreground.Span>, windows: List<Window>): Map<String, Long> {
        val out = HashMap<String, Long>()
        spans.forEach { span ->
            windows.forEach { window ->
                if (!window.approved.contains(span.id)) return@forEach
                val from = maxOf(span.start, window.start)
                val to = minOf(span.end, window.end)
                if (to > from) out[span.id] = (out[span.id] ?: 0L) + (to - from)
            }
        }
        return out
    }

    /**
     * The spans with every approving window cut out of them.
     *
     * A span may be swallowed whole, trimmed at either end, or split in two when
     * the window sits inside it - and an unapproved app comes back untouched,
     * because there is no window in the world that approves it.
     */
    fun chargeableSpans(spans: List<Foreground.Span>, windows: List<Window>): List<Foreground.Span> {
        val out = ArrayList<Foreground.Span>()
        spans.forEach { span ->
            val swallows = windows
                .filter { it.approved.contains(span.id) }
                .map { maxOf(span.start, it.start) to minOf(span.end, it.end) }
                .filter { it.second > it.first }
                .sortedBy { it.first }
            var cursor = span.start
            swallows.forEach { (from, to) ->
                if (from > cursor) out.add(Foreground.Span(span.id, cursor, from))
                if (to > cursor) cursor = to
            }
            if (cursor < span.end) out.add(Foreground.Span(span.id, cursor, span.end))
        }
        return out
    }
}
