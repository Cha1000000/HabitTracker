package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable
import com.habittracker.domain.model.AchievementCategory
import com.habittracker.domain.model.AchievementWithProgress

@Immutable
data class AchievementsUiState(
    val isLoading: Boolean = false,
    val achievements: List<AchievementWithProgress> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
    val selectedCategory: AchievementCategory? = null,
    val error: String? = null
)