package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable
import com.habittracker.presentation.viewmodel.HabitWithCompletion
import kotlinx.datetime.LocalDate

/**
 * UI State для экрана со списком привычек.
 */
@Immutable
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
    val currentUserAvatarUrl: String? = null, // URL аватарки текущего пользователя для TopAppBar
    val showConfetti: Boolean = false, // Флаг для показа конфетти при выполнении привычки
    val habitIdForValueInput: String? = null, // ID привычки для которой открыт диалог ввода значения
    val completedHabitForPhotoProof: CompletedHabitInfo? = null, // Информация о только что выполненной привычке для предложения добавить фото
    val habitForPhotoSheet: HabitPhotoSheetInfo? = null, // Информация для показа BottomSheet добавления фото
    val isUploadingPhoto: Boolean = false // Флаг загрузки фото
)

/**
 * Информация о выполненной привычке для предложения добавить фото-подтверждение
 */
data class CompletedHabitInfo(
    val habitId: String,
    val habitTitle: String,
    val date: LocalDate
)

/**
 * Информация для показа BottomSheet добавления фото-подтверждения
 */
data class HabitPhotoSheetInfo(
    val habitId: String,
    val habitTitle: String,
    val habitUnit: String?,
    val date: LocalDate,
    val photoUrl: String? = null
)