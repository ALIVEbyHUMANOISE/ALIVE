package com.projectego.app.data

import android.content.Context

/**
 * The first-run flag.
 *
 * Once the welcome is finished (or deliberately skipped) it never appears
 * again. It lives in its own preferences file rather than beside lock state,
 * because it is not a fact the engine reads - it is the app's memory that it
 * has already said hello. It rides in Auto Backup like the rest of the app,
 * so a reinstalled ALIVE on the same backup account is not treated as a
 * stranger.
 */
object WelcomeGate {

    private const val PREFS_FILE = "project_ego_app"
    private const val KEY_WELCOME_DONE = "welcome_done_v1"

    fun isDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WELCOME_DONE, false)

    fun markDone(context: Context) {
        // commit(), not apply(): this is written the moment the screen is about
        // to be replaced, once per install - a blocking write with no cost, and
        // the one write that must not be lost to a process death mid-handoff.
        prefs(context).edit().putBoolean(KEY_WELCOME_DONE, true).commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
}
