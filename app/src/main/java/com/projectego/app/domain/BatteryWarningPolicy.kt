package com.projectego.app.domain

/**
 * When the pill should say something about the battery.
 *
 * Deliberately narrow: one warning at fifteen percent, only while the phone is
 * unplugged, and only once per drop. A warning that repeats is a nag, and a
 * nag about something the person cannot act on in the moment is just noise.
 * Charging resets the latch, so the next unplugged drop can warn again.
 */
object BatteryWarningPolicy {

    const val LOW_PERCENT = 15

    fun shouldWarn(levelPercent: Int, charging: Boolean, alreadyWarned: Boolean): Boolean =
        !charging && !alreadyWarned && levelPercent in 1..LOW_PERCENT
}
