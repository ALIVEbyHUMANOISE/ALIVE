package com.projectego.app.domain

/**
 * A mission the person wrote themselves.
 *
 * The catalog is thirteen goals somebody else chose, and the honest signal
 * behind this file is that even the person who wrote the catalog skips them. A
 * goal stated in your own words is a different object: it is the difference
 * between "Olahraga 20 Menit" and "lari sore keliling blok".
 *
 * It is still checked by a machine, and that is not a compromise: the mission is
 * written by a person and proved by a focus session on the apps they name, so
 * nothing here rests on anybody's word - not theirs, and not ALIVE's.
 *
 * One slot, and it pays once, exactly like a catalog quest. If it could be
 * rewritten and paid again, the bank would be a tap that never runs dry, and
 * the whole reason the Time Bank is a currency that ends would fall apart.
 */
object CustomQuest {

    /** The only durations, matching the ones the night contract offers. */
    val DURATIONS = listOf(10, 20, 30)

    /** The fixed id, so a rewrite is the same quest and cannot be paid twice. */
    const val ID = "custom.own"

    /**
     * What a self-written mission pays.
     *
     * "The same as the catalog" is not one number: the catalog pays by the act,
     * not by the clock - a twenty-minute run pays 25 and a thirty-minute read
     * pays 20. These are the values the catalog uses most often at each length,
     * which is the only reading of "same" that is not a guess.
     */
    fun rewardFor(durationMinutes: Int): Long = when (durationMinutes) {
        in 0..10 -> 10L
        in 11..20 -> 15L
        else -> 20L
    }

    /** A mission needs a name somebody actually wrote and a duration we offer. */
    fun isComplete(name: String?, durationMinutes: Int): Boolean =
        !name.isNullOrBlank() && durationMinutes in DURATIONS
}
