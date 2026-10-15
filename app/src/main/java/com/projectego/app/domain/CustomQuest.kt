package com.projectego.app.domain

import com.projectego.app.model.Quest
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestDifficulty

/**
 * A mission the person wrote themselves.
 *
 * The catalog is thirteen goals somebody else chose, and the honest signal
 * behind this file is that even the person who wrote the catalog skips them. A
 * goal stated in your own words is a different object: it is the difference
 * between "Olahraga 20 Menit" and "lari sore keliling blok".
 *
 * What the person owns is the words, the length and the kind of doing. What they
 * do not own is the app list - that belongs to ALIVE, the same list every quest
 * of that kind runs on. The first draft of this file let the author name the apps
 * their session would allow, and that was a hole rather than a feature: naming
 * an app both unblocked it and stopped it being charged, so a mission called
 * "fokus" with TikTok on its list paid the person for scrolling. The app has
 * written the rule down elsewhere already - a list that can be edited is a list
 * that can be emptied, and this one would have been widened.
 *
 * It is still checked by a machine, and that is not a compromise: the mission is
 * written by a person and proved by a focus session of the kind they chose, on
 * the apps ALIVE already trusts for that kind - so nothing here rests on
 * anybody's word, not theirs and not ALIVE's.
 *
 * One slot, and it pays once, exactly like a catalog quest. If it could be
 * rewritten and paid again, the bank would be a tap that never runs dry, and
 * the whole reason the Time Bank is a currency that ends would fall apart.
 */
object CustomQuest {

    /** The only durations, matching the ones the night contract offers. */
    val DURATIONS = listOf(10, 20, 30)

    /**
     * The kinds a mission may be.
     *
     * The four the engine can watch, and no others: a kind outside this list
     * would be a mission whose session could never be verified, which is the one
     * kind of mission the whole design refuses. Work is not here because the
     * catalog has no work quest to be the same thing as - a mission is always
     * one of the four things ALIVE already knows how to sit with.
     */
    val KINDS = listOf(
        QuestCategory.STUDY,
        QuestCategory.CODING,
        QuestCategory.CREATIVE,
        QuestCategory.WRITING
    )

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

    /** A mission needs a name somebody actually wrote, a length we offer, and a kind. */
    fun isComplete(name: String?, durationMinutes: Int, kind: QuestCategory): Boolean =
        !name.isNullOrBlank() && durationMinutes in DURATIONS && kind in KINDS

    /**
     * The board entry a stored mission becomes.
     *
     * Its category IS its kind. That is what keeps the rest of the engine
     * ordinary: everything that already asks "what is this quest" - the guard
     * that decides the session, the verifier that judges it, the hue and mark on
     * its card - reads the same field it reads for the catalog, and no code
     * anywhere has to know that this quest was written by hand.
     */
    fun quest(title: String, description: String, durationMinutes: Int, kind: QuestCategory): Quest =
        Quest(
            id = ID,
            title = title,
            description = description,
            category = kind,
            difficulty = QuestDifficulty.MEDIUM,
            estimatedDurationMinutes = durationMinutes,
            rewardMinutes = rewardFor(durationMinutes)
        )
}