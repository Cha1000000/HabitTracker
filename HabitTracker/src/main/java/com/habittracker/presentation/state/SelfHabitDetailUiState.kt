package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry

@Immutable
data class SelfHabitDetailUiState(
    val isLoading: Boolean = false,
    val habit: Habit? = null,
    val currentUserId: String = "",
    val habitEntries: List<HabitEntry> = emptyList(),
    val isCompletedToday: Boolean = false,
    val isMarkingCompletion: Boolean = false,
    val showPhotoProofPrompt: Boolean = false,
    val showPhotoSheet: Boolean = false,
    val currentUserPhotoUrl: String? = null,
    val currentUserNote: String? = null,
    val isUploadingPhoto: Boolean = false,
    val photoUploadSuccessMessage: String? = null,
    val currentStreak: Int = 0,
    val totalCompletedDays: Int = 0,
    val completionRatePercent: Int = 0,
    val error: String? = null,
    val successMessage: String? = null,
)

