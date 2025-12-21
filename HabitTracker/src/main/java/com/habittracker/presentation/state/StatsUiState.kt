package com.habittracker.presentation.state

import com.habittracker.domain.model.gamification.UserStats

data class StatsUiState(
    val isLoading: Boolean = false,
    val userStats: UserStats? = null,
    val leaderboard: List<com.habittracker.domain.model.User> = emptyList(),
    val error: String? = null
)