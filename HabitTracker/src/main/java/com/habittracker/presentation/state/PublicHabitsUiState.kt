package com.habittracker.presentation.state

import com.habittracker.domain.model.Habit
import com.habittracker.presentation.ui.discover.HabitCategory

/**
 * UI состояние экрана публичных привычек
 */
data class PublicHabitsUiState(
    val isLoading: Boolean = false,
    val publicHabits: List<Habit> = emptyList(),
    val filteredHabits: List<Habit> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: HabitCategory = HabitCategory.ALL,
    val sortByPopularity: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null
)