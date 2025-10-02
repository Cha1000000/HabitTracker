package com.habittracker.presentation.state

import com.habittracker.presentation.viewmodel.HabitWithCompletion

/**
 * UI State для экрана со списком привычек.
 */
data class HabitsUiState(
    val habitsWithCompletion: List<HabitWithCompletion> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)