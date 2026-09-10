package com.projectego.app.model

enum class FocusActivityType(val displayName: String) {
    STUDY("Study"),
    WORK("Work"),
    CODING("Coding"),
    CREATIVE("Creative"),
    WRITING("Writing")
}

enum class FocusState {
    NO_FOCUS,
    ACTIVE,
    EXPIRED
}

data class FocusSession(
    val activityType: FocusActivityType,
    val goal: String,
    val durationMinutes: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val isActive: Boolean = true
) {
    fun getRemainingMillis(): Long {
        val now = System.currentTimeMillis()
        return (endTimestamp - now).coerceAtLeast(0L)
    }

    fun getRemainingMinutes(): Long {
        return getRemainingMillis() / 1000 / 60
    }

    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= endTimestamp
    }
}
