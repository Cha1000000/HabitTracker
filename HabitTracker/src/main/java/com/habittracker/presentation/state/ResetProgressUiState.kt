package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable

@Immutable
data class GroupHabitResetItem(
    val habitId: String,
    val title: String,
    val isOwner: Boolean,
    val participantsCount: Int
)

@Immutable
data class ResetProgressUiState(
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val userId: String? = null,
    val personalHabitsCount: Int = 0,
    val groupHabits: List<GroupHabitResetItem> = emptyList(),
    val error: String? = null,
    val successResId: Int? = null
)
