package com.habittracker.presentation.state

import com.habittracker.domain.model.Challenge

data class ChallengesUiState(
    val currentUserId: String? = null,
    val pendingInvitations: List<Challenge> = emptyList(),
    val activeChallenges: List<Challenge> = emptyList(),
    val endingSoonChallenges: List<Challenge> = emptyList(),
    val completedChallenges: List<Challenge> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedTab: ChallengeTab = ChallengeTab.ACTIVE,
    val showJoinDialog: Boolean = false,
    val joinChallengeId: String? = null,
    val isAcceptingInvitation: Boolean = false
)

enum class ChallengeTab {
    INVITATIONS,
    ACTIVE,
    ENDING_SOON,
    COMPLETED
}