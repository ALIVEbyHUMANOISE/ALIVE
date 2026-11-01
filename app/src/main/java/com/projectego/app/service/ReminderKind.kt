package com.projectego.app.service

/** The two things the island may rise for without a tap. */
enum class ReminderKind {
    DRINK,
    PROMISE;

    companion object {
        fun of(raw: String?): ReminderKind? =
            entries.firstOrNull { it.name == raw }
    }
}
