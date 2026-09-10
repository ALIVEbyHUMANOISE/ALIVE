package com.projectego.app.model

/**
 * Data foundation prepared for Phase 2 (Time Bank & Quest Rewards).
 * Stores earned bonus entertainment minutes.
 */
data class TimeBank(
    val earnedMinutes: Long = 0L,
    val spentMinutes: Long = 0L
) {
    val availableMinutes: Long
        get() = (earnedMinutes - spentMinutes).coerceAtLeast(0L)
}
