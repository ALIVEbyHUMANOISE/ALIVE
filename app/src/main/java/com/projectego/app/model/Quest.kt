package com.projectego.app.model

enum class QuestCategory {
    EXERCISE,
    STUDY,
    READING,
    WALKING,
    CLEANING,
    CREATIVE,
    OTHER
}

enum class QuestDifficulty {
    EASY,
    MEDIUM,
    HARD
}

enum class QuestStatus {
    AVAILABLE,
    ACTIVE,
    COMPLETED
}

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val category: QuestCategory,
    val difficulty: QuestDifficulty,
    val estimatedDurationMinutes: Int,
    val rewardMinutes: Long,
    val status: QuestStatus = QuestStatus.AVAILABLE
)
