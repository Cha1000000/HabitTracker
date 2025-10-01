package com.habittracker.presentation.state

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.User

/**
 * UI состояние для экрана детальной информации о групповой привычке
 */
data class GroupHabitDetailUiState(
    val isLoading: Boolean = false,
    val habit: Habit? = null,
    val participants: List<User> = emptyList(),
    val habitEntries: List<HabitEntry> = emptyList(),
    val pendingInvitations: List<HabitInvitation> = emptyList(),
    val inviteCode: String? = null,
    val isOwner: Boolean = false,
    val isManageMode: Boolean = false,
    val isGeneratingCode: Boolean = false,
    val groupStreak: Int = 0,
    val isCompletedToday: Boolean = false,
    val shouldNavigateBack: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
