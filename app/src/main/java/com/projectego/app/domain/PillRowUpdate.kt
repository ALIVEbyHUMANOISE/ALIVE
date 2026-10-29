package com.projectego.app.domain

/**
 * What the app rows need after a new render: nothing, new numbers, or a rebuild.
 *
 * The list is ranked by minutes, so the numbers move constantly while the set of
 * apps usually does not. Rebuilding the row views and re-resolving every app's
 * label just because a number moved was measured on the phone at 45-94 ms of
 * main-thread time, on exactly the taps where a minute had turned; writing the
 * number into the row that already exists costs nothing worth tracing.
 */
object PillRowUpdate {

    enum class Action {
        /** Same apps, same numbers: nothing to do. */
        NOTHING,

        /** Same apps in the same order, later numbers: write the numbers only. */
        NUMBERS,

        /** Different apps, a different order, or a different watched app. */
        REBUILD
    }

    fun action(
        shownPackages: List<String>,
        nextPackages: List<String>,
        shownCurrent: String?,
        nextCurrent: String?,
        shownMinutes: List<Long>,
        nextMinutes: List<Long>
    ): Action = when {
        // The order is the ranking, so a reorder is a structure change even when
        // the set of packages is identical.
        shownPackages != nextPackages -> Action.REBUILD
        shownCurrent != nextCurrent -> Action.REBUILD
        shownMinutes != nextMinutes -> Action.NUMBERS
        else -> Action.NOTHING
    }
}
