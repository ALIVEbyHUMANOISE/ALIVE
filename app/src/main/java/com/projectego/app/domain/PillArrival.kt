package com.projectego.app.domain

/**
 * Who owns the pill's arrival transform.
 *
 * The pill arrives by growing: the root view fades, scales and drops into place
 * over 240 ms with an overshoot, while the window underneath it is already the
 * real size. The morph never touches those three properties - it owns the window
 * size, the corner and the content - so the two cannot fight over a value. What
 * they can do is overlap: a tap in the first quarter second would start a morph
 * while the shell is still arriving, which reads as two motions at once.
 *
 * The rule: the arrival keeps its curve when nothing interrupts it, and a tap
 * shortens whatever is left of it instead of cancelling it. Cancelling would
 * freeze the root at a half-done scale and alpha; snapping to the end would jump.
 * Finishing from the live value on the same properties is the only option that
 * keeps both continuous, and it needs no second owner - just a shorter trip.
 */
class PillArrival {

    enum class OnTap {
        /** No arrival is running; a tap has nothing to settle. */
        LET_RUN,

        /** The arrival is still arriving: finish it quickly from where it is. */
        SHORTEN,

        /** It is already finishing; another tap adds nothing. */
        ALREADY_SETTLING
    }

    /** True while the root transform belongs to the arrival. */
    var isActive = false
        private set

    private var settling = false

    fun started() {
        isActive = true
        settling = false
    }

    fun finished() {
        isActive = false
        settling = false
    }

    fun tap(): OnTap {
        if (!isActive) return OnTap.LET_RUN
        if (settling) return OnTap.ALREADY_SETTLING
        settling = true
        return OnTap.SHORTEN
    }

    /** Reduced motion or teardown: the transform is handed back without a trip. */
    fun release() {
        isActive = false
        settling = false
    }
}
