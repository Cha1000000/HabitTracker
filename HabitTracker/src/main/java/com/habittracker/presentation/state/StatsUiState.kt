package com.habittracker.presentation.state

import com.habittracker.domain.model.gamification.UserStats

data class StatsUiState(
    val isLoading: Boolean = false,
    val userStats: UserStats? = null,
    val error: String? = null
)