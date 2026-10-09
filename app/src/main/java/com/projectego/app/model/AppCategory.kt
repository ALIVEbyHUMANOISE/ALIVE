package com.projectego.app.model

enum class AppCategory {
    ESSENTIAL,

    /**
     * Seen and reported, but never charged and never locked. Messaging is the
     * one category where a block would cost the person a message they cannot
     * see - and charging it was worse than that: two hours of WhatsApp spent the
     * entertainment allowance and locked the apps that were only ever
     * entertainment.
     */
    TRACKED,
    ENTERTAINMENT,
    SYSTEM_IGNORED
}
