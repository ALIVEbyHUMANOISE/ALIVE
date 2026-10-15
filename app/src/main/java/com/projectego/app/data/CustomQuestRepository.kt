package com.projectego.app.data

import android.content.Context
import com.projectego.app.domain.CustomQuest
import com.projectego.app.model.QuestCategory

/**
 * Where the one self-written mission is kept.
 *
 * Plain storage, no clock and no side effects: the rules it obeys - which
 * durations exist, which kinds, what each pays, what counts as finished - live
 * in [CustomQuest] where they can be tested without a device.
 */
class CustomQuestRepository(context: Context) {

    data class Stored(
        val name: String,
        val durationMinutes: Int,
        /** The kind of doing the person chose; it is the mission's category. */
        val kind: QuestCategory
    )

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun stored(): Stored? {
        val name = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val duration = prefs.getInt(KEY_DURATION, 0)
        // A mission written before kinds existed ran as a study session, so that
        // is what it stays rather than being dropped or guessed at.
        val kind = prefs.getString(KEY_KIND, null)
            ?.let { raw -> runCatching { QuestCategory.valueOf(raw) }.getOrNull() }
            ?.takeIf { it in CustomQuest.KINDS }
            ?: QuestCategory.STUDY
        if (!CustomQuest.isComplete(name, duration, kind)) return null
        return Stored(name, duration, kind)
    }

    fun save(name: String, durationMinutes: Int, kind: QuestCategory) {
        prefs.edit()
            .putString(KEY_NAME, name.trim())
            .putInt(KEY_DURATION, durationMinutes)
            .putString(KEY_KIND, kind.name)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE = "project_ego_custom_quest"
        const val KEY_NAME = "key_name"
        const val KEY_DURATION = "key_duration"
        const val KEY_KIND = "key_kind"
    }
}