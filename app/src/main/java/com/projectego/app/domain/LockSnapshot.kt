package com.projectego.app.domain

/**
 * The two numbers the lock screen prints, exactly as the guard that decided the
 * lock computed them.
 *
 * The guard already asks UsageStats for the day in order to decide; asking again
 * inside the lock screen's `onCreate` cost 353-785 ms of its startup (measured on
 * the phone, three creations, 267-289 binder sends each) and duplicated the whole
 * classification of every app used that day. The screen now prints what the
 * decision was made from.
 *
 * Absent numbers are not zero. A focus lock never computes a report, and an intent
 * from elsewhere may carry nothing - in that case the screen says the sentence and
 * stops, because "0 of 120" would be a lie and "0 of 0" would read as a bug.
 */
class LockSnapshot private constructor(
    val usedMinutes: Long?,
    val limitMinutes: Long?
) {

    /** True only when both numbers arrived and both are real. */
    val hasNumbers: Boolean get() = usedMinutes != null && limitMinutes != null

    companion object {

        /** What the intent carries when the guard sent no number for this field. */
        const val MISSING = -1L

        /**
         * A snapshot from two intent values. Zero is a real number (a zero day,
         * a day with no usage yet); anything negative is the absent marker.
         */
        fun of(usedMinutes: Long, limitMinutes: Long): LockSnapshot = LockSnapshot(
            usedMinutes = usedMinutes.takeIf { it >= 0L },
            limitMinutes = limitMinutes.takeIf { it >= 0L }
        )
    }
}
