package com.projectego.app.domain

/**
 * The Living Interface's colours, in one place.
 *
 * The eyes are the one deliberately adjustable part of the character: white
 * ovals on a blue bloom, borrowed from the NUSA reference. The ring colours are
 * separate constants - the eyes are decoration, the ring is status, and
 * changing the character's look must never change what the day means.
 *
 * Values are plain ARGB ints so this stays a pure object: it is read by the
 * views, compared in tests, and has no dependency on `android.graphics.Color`.
 */
object PillTheme {

    // ---------- the character's eyes ----------

    /** The eyes themselves: white, like the reference island. */
    const val EYE_COLOR: Int = 0xFFFFFFFF.toInt()

    /** The bloom behind them. White alone reads as a sticker; blue makes it lit. */
    const val EYE_HALO_COLOR: Int = 0xFF2F6BFF.toInt()

    /**
     * How far the bloom bleeds past the eyes, 0..1. Restrained on purpose: a
     * black shell makes glow read twice as loud as it does on paper.
     */
    const val EYE_GLOW: Float = 0.42f

    // ---------- the timer ----------

    /** White until the day is spent; the amber belonged to the old lights. */
    const val TIMER_COLOR: Int = 0xFFFFFFFF.toInt()

    // ---------- the status ring ----------

    /** An ordinary day is a safe day: green. */
    const val RING_NORMAL_COLOR: Int = 0xFF28C840.toInt()

    /**
     * Approaching the limit, pulsing. This is the hue an ordinary day used to
     * carry, moved here so yellow means "running out" and nothing else.
     */
    const val RING_CAUTION_COLOR: Int = 0xFFFEBC2E.toInt()

    /** Approaching and reached share a hue: both are time running out. */
    const val RING_LIMIT_COLOR: Int = 0xFFFF5F57.toInt()

    /**
     * A recovery day is safe too, but it must not be mistaken for an ordinary
     * day, so it keeps green's family at a separate hue: mint.
     */
    const val RING_RECOVERY_COLOR: Int = 0xFF2ED1A6.toInt()

    /** No trustworthy status. Dim on purpose - it must not claim a normal day. */
    const val RING_UNKNOWN_COLOR: Int = 0xFF6B6B6B.toInt()

    // ---------- motion shared by both ----------

    /** How long a ring colour change takes. Short enough to read as a state flip. */
    const val RING_COLOR_FADE_MILLIS: Long = 240L
}
