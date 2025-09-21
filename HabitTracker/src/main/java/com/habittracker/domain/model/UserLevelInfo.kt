package com.habittracker.domain.model

/**
 * Информация о текущем уровне пользователя
 */
data class UserLevelInfo(
    val currentLevel: Int,
    val currentPoints: Int,
    val pointsForNextLevel: Int,
    val pointsToNextLevel: Int,
    val progress: Float,
    val levelTitle: String,
    val isMaxLevel: Boolean
)
