package com.projectego.app.domain

/**
 * The compact timer's text: the one string on the pill whose width decides the
 * window's own width.
 *
 * The pill hugs its reading (`WRAP_CONTENT` in the compact state), so a new
 * minute that needs another digit is a new measured width. Written while a morph
 * owns the window, that measurement would resize the shell on a second clock:
 * the lights would slide inside a width the animator is already driving, and a
 * collapse would keep aiming at a width its own text no longer has. So the newest
 * reading is kept here and written at the next boundary - the instant a morph
 * commits, or any tick taken while nothing is morphing.
 *
 * Latest wins, and there is no queue behind it: a reading nobody has seen is not
 * worth replaying later.
 */
class PillHeaderText {

    private var latest: String? = null
    private var shown: String? = null

    /** Records a reading. Nothing is written until [flush] allows it. */
    fun offer(text: String) {
        latest = text
    }

    /**
     * The text to write now, or null when there is nothing new or the window is
     * being measured by a morph ([morphing]). A value handed back here is
     * remembered as shown, so an unchanged reading never asks for a second write.
     */
    fun flush(morphing: Boolean): String? {
        if (morphing) return null
        val next = latest ?: return null
        if (next == shown) return null
        shown = next
        return next
    }

    /** Teardown: nothing may be left waiting for a window that is gone. */
    fun clear() {
        latest = null
        shown = null
    }
}
