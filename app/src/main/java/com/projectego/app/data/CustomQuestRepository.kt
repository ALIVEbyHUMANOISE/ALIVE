package com.projectego.app.data

import android.content.Context
import com.projectego.app.domain.CustomQuest

/**
 * Where the one self-written mission is kept.
 *
 * Plain storage, no clock and no side effects: the rules it obeys - which
 * durations exist, what each pays, what counts as finished - live in
 * [CustomQuest] where they can be tested without a device.
 */
class CustomQuestRepository(context: Context) {

    data class Stored(
        val name: String,
        val durationMinutes: Int,
        /** The apps allowed to be open during its session; empty means none. */
        val approvedPackages: Set<String>
    )

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun stored(): Stored? {
        val name = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val duration = prefs.getInt(KEY_DURATION, 0)
        if (!CustomQuest.isComplete(name, duration)) return null
        return Stored(name, duration, prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet())
    }

    fun save(name: String, durationMinutes: Int, approvedPackages: Set<String>) {
        prefs.edit()
            .putString(KEY_NAME, name.trim())
            .putInt(KEY_DURATION, durationMinutes)
            .putStringSet(KEY_PACKAGES, approvedPackages)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE = "project_ego_custom_quest"
        const val KEY_NAME = "key_name"
        const val KEY_DURATION = "key_duration"
        const val KEY_PACKAGES = "key_packages"
    }
}
