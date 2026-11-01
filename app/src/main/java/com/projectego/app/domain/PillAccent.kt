package com.projectego.app.domain

/**
 * The two palettes the island can wear.
 *
 * It is the same character either way - only the light behind it changes. The
 * owner asked for both, with the choice left to the person, so this is a
 * preference rather than a constant: blue is the reference look, pink is the
 * warmer sibling, and the status ring keeps its own semantic colours in both.
 */
enum class PillAccent(val color: Int) {
    BLUE(0xFF2F6BFF.toInt()),
    PINK(0xFFFF5FA8.toInt());

    companion object {
        /** Unknown or absent preferences fall back to the reference palette. */
        fun of(name: String?): PillAccent =
            entries.firstOrNull { it.name == name } ?: BLUE
    }
}
