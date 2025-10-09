package com.habittracker.presentation.state

import com.habittracker.presentation.viewmodel.HabitWithCompletion
import kotlinx.datetime.LocalDate

/**
 * UI State для экрана со списком привычек.
 */
data class HabitsUiState(
    val habitsWithCompletion: List<HabitWithCompletion> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val selectedDate: LocalDate? = null,
    val weekCompletionData: Map<LocalDate, Float> = emptyMap() // Прогресс выполнения для дней текущей недели (0f-1f)
)