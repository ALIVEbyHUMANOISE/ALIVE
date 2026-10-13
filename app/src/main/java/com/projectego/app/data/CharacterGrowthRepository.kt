package com.projectego.app.data

import android.content.Context
import com.projectego.app.domain.CharacterGrowth

/**
 * Where the lifetime count lives.
 *
 * Three day keys and three counters, one pair per kind of day the character
 * grows on. The rule that decides whether a day owes a point is in
 * [CharacterGrowth]; this only keeps the books and is safe to call twice a day
 * by accident, because the stored day key is what makes a second call a no-op.
 */
class CharacterGrowthRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** A quest paid out on [dayKey]. Called from the one place a reward exists. */
    @Synchronized
    fun recordQuestDay(dayKey: String) =
        record(KEY_QUEST_DAY, KEY_QUEST_COUNT, dayKey)

    /** A focus session finished on [dayKey]. */
    @Synchronized
    fun recordFocusDay(dayKey: String) =
        record(KEY_FOCUS_DAY, KEY_FOCUS_COUNT, dayKey)

    /** A promise written the night before and kept, judged on [dayKey]. */
    @Synchronized
    fun recordPromiseDay(dayKey: String) =
        record(KEY_PROMISE_DAY, KEY_PROMISE_COUNT, dayKey)

    /** Lifetime points: one per qualifying day, per kind. */
    fun points(): Int =
        prefs.getInt(KEY_QUEST_COUNT, 0) +
            prefs.getInt(KEY_FOCUS_COUNT, 0) +
            prefs.getInt(KEY_PROMISE_COUNT, 0)

    fun stage(): CharacterGrowth.Stage = CharacterGrowth.stageFor(points())

    @Synchronized
    private fun record(dayKeyName: String, countKeyName: String, dayKey: String) {
        if (!CharacterGrowth.owesPoint(prefs.getString(dayKeyName, null), dayKey)) return
        prefs.edit()
            .putString(dayKeyName, dayKey)
            .putInt(countKeyName, prefs.getInt(countKeyName, 0) + 1)
            .apply()
    }

    private companion object {
        const val PREFS_FILE = "project_ego_character_growth"
        const val KEY_QUEST_DAY = "key_quest_day"
        const val KEY_QUEST_COUNT = "key_quest_count"
        const val KEY_FOCUS_DAY = "key_focus_day"
        const val KEY_FOCUS_COUNT = "key_focus_count"
        const val KEY_PROMISE_DAY = "key_promise_day"
        const val KEY_PROMISE_COUNT = "key_promise_count"
    }
}
