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
    val selectedDate: LocalDate? = null, // Выбранная дата (для которой загружены привычки)
    val currentWeekStartDate: LocalDate? = null, // Начало текущей видимой недели (для отображения диапазона дат)
    val weekCompletionData: Map<LocalDate, Float> = emptyMap(), // Прогресс выполнения для дней текущей недели (0f-1f)
    val oldestHabitDate: LocalDate? = null, // Дата создания самой старой привычки (минимальная граница)
    val maxFutureDate: LocalDate? = null, // Максимальная дата в будущем (сегодня + 1 год)
    val currentUserAvatarUrl: String? = null // URL аватарки текущего пользователя для TopAppBar
)