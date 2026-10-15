package com.projectego.app.data

import com.projectego.app.model.FocusActivityType

/**
 * Reading and writing the day's focus windows.
 *
 * Split out of the manager so the format can be tested on plain JVM, because
 * the format is the one thing in this feature that carries old data: every
 * window recorded before self-written missions existed has three fields, and a
 * reader that demands four would drop them - and dropping a window silently
 * un-exempts the minutes it covered, which raises today's charged time and can
 * lock an app early.
 *
 * So the reader takes both shapes. Three fields is the record as it has always
 * been written; four means the session named its own apps, and the fourth field
 * is that list. Nothing here throws on bad input: a malformed record is dropped
 * on its own, exactly as the manager has always done.
 */
object FocusWindowCodec {

    private const val WINDOW_SEPARATOR = "\u001E"
    private const val FIELD_SEPARATOR = "\u001F"
    private const val PACKAGE_SEPARATOR = ","

    fun encode(windows: List<FocusWindow>): String =
        windows.joinToString(WINDOW_SEPARATOR) { window ->
            val base = "${window.start}$FIELD_SEPARATOR${window.end}" +
                "$FIELD_SEPARATOR${window.activityType.name}"
            window.approvedOverride?.let { approved ->
                base + FIELD_SEPARATOR + approved.joinToString(PACKAGE_SEPARATOR)
            } ?: base
        }

    fun decode(raw: String?): List<FocusWindow> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(WINDOW_SEPARATOR).mapNotNull { part ->
            val fields = part.split(FIELD_SEPARATOR)
            if (fields.size != 3 && fields.size != 4) return@mapNotNull null

            val start = fields[0].toLongOrNull() ?: return@mapNotNull null
            val end = fields[1].toLongOrNull() ?: return@mapNotNull null
            val type = try {
                FocusActivityType.valueOf(fields[2])
            } catch (_: Exception) {
                return@mapNotNull null
            }

            // An empty fourth field is a person who allowed no apps at all, not
            // a record from before the field existed - the two must not be
            // collapsed, because one means "the catalog's list" and the other
            // means "nothing".
            val approved = if (fields.size == 4) {
                fields[3].split(PACKAGE_SEPARATOR).filter { it.isNotBlank() }.toSet()
            } else {
                null
            }

            FocusWindow(start, end, type, approved)
        }
    }
}
