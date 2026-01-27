package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable
import com.habittracker.domain.model.gamification.UserStats
import com.habittracker.domain.usecase.gamification.LeaderboardEntry
import com.habittracker.domain.usecase.gamification.LeaderboardType
import com.habittracker.presentation.ui.habits.components.HeatmapItem

@Immutable
data class StatsUiState(
    val isLoading: Boolean = false,
    val userStats: UserStats? = null,
    val leaderboard: List<com.habittracker.domain.model.User> = emptyList(),
    val leaderboardEntries: List<LeaderboardEntry> = emptyList(),
    val leaderboardType: LeaderboardType = LeaderboardType.ALL_TIME,
    val yearlyActivity: List<HeatmapItem> = emptyList(),
    val error: String? = null
)