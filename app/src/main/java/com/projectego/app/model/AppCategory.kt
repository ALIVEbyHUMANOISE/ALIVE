package com.projectego.app.model

enum class AppCategory {
    ESSENTIAL,

    /**
     * A tool for making things: office suites, note apps, video and design
     * editors. Seen and reported like [TRACKED], never charged, never locked.
     *
     * Curated in code and deliberately not user-editable, because this is the
     * one list whose contents decide whether the app has a cheat: a person can
     * be trusted with their own discipline, and the list itself must not be the
     * thing that invites the lie. Social apps, games and browsers can never
     * appear here.
     */
    WORK,

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
