package com.habittracker.domain.model

import androidx.annotation.StringRes

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
    @StringRes val levelTitleResId: Int? = null,
    val isMaxLevel: Boolean
)
