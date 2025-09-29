package com.habittracker.presentation.state

import com.habittracker.domain.model.Challenge

data class ChallengesUiState(
    val activeChallenges: List<Challenge> = emptyList(),
    val endingSoonChallenges: List<Challenge> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedTab: ChallengeTab = ChallengeTab.ACTIVE,
    val showJoinDialog: Boolean = false,
    val joinChallengeId: String? = null
)

enum class ChallengeTab {
    ACTIVE,
    ENDING_SOON,
    COMPLETED
}