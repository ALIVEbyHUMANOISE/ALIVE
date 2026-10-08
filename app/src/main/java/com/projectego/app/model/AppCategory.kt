package com.projectego.app.model

enum class AppCategory {
    ESSENTIAL,

    /**
     * Counted like entertainment - in the day's tally, the limit and the chart -
     * but never locked. Messaging is the one category where a block would cost
     * the person a message they cannot see.
     */
    TRACKED,
    ENTERTAINMENT,
    SYSTEM_IGNORED
}
