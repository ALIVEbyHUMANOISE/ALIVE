package com.projectego.app.data

import android.content.Context

/**
 * Whether the floating pill is on, remembered.
 *
 * The pill began as something summoned once and forgotten by the next app
 * switch, which made it a tool only for people who already remembered to use
 * it - exactly the people who need it least. This is the switch instead: turned
 * on once, and the guard service brings the pill up whenever an entertainment
 * app comes to the front and takes it down when one leaves.
 *
 * It stays off until asked for, so nobody who never presses the tile ever sees
 * anything.
 */
object PillSettings {

    private const val PREFS_FILE = "project_ego_pill"
    private const val KEY_ENABLED = "key_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
