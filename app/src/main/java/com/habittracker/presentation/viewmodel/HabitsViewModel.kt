package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Habit
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Данные о привычке с информацией о выполнении
 */
data class HabitWithCompletion(
    val habit: Habit,
    val isCompletedToday: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * UI State для экрана со списком привычек.
 */
data class HabitsUiState(
    val habitsWithCompletion: List<HabitWithCompletion> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val getHabitsUseCase: GetHabitsUseCase,
    private val addHabitUseCase: AddHabitUseCase,
    private val markHabitCompletedUseCase: MarkHabitCompletedUseCase,
    private val markHabitIncompleteUseCase: MarkHabitIncompleteUseCase,
    private val getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()
    
    private val currentUserId = "DUMMY_USER_ID" // TODO: Заменить на реальный ID пользователя
    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    init {
        loadHabits(currentUserId)
    }

    /**
     * Загружает список привычек для пользователя.
     */
    private fun loadHabits(userId: String) {
        getHabitsUseCase(userId)
            .onEach { habits ->
                // Загружаем статус выполнения для каждой привычки
                val habitsWithCompletion = habits.map { habit ->
                    HabitWithCompletion(habit = habit, isLoading = true)
                }
                _uiState.value = HabitsUiState(habitsWithCompletion = habitsWithCompletion, isLoading = false)
                
                // Асинхронно загружаем статус выполнения для каждой привычки
                loadCompletionStatuses(habits, userId)
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Загружает статус выполнения для списка привычек
     */
    private fun loadCompletionStatuses(habits: List<Habit>, userId: String) {
        viewModelScope.launch {
            val updatedHabits = habits.map { habit ->
                try {
                    val isCompleted = getHabitCompletionStatusUseCase(habit.id, userId, today)
                    HabitWithCompletion(habit = habit, isCompletedToday = isCompleted, isLoading = false)
                } catch (e: Exception) {
                    HabitWithCompletion(habit = habit, isCompletedToday = false, isLoading = false)
                }
            }
            _uiState.value = _uiState.value.copy(habitsWithCompletion = updatedHabits)
        }
    }

    /**
     * Добавляет новую привычку.
     */
    fun addHabit(habit: Habit) {
        viewModelScope.launch {
            addHabitUseCase(habit)
            // После добавления список обновится автоматически благодаря Flow
        }
    }
    
    /**
     * Отмечает привычку как выполненную
     */
    fun markHabitCompleted(habitId: String, note: String? = null) {
        viewModelScope.launch {
            try {
                // Обновляем UI состояние сразу для отзывчивости
                updateHabitCompletionStatus(habitId, true, isLoading = true)
                
                // Выполняем операцию
                markHabitCompletedUseCase(habitId, currentUserId, today, note)
                
                // Обновляем финальное состояние
                updateHabitCompletionStatus(habitId, true, isLoading = false)
            } catch (e: Exception) {
                // В случае ошибки возвращаем предыдущее состояние
                updateHabitCompletionStatus(habitId, false, isLoading = false)
                _uiState.value = _uiState.value.copy(error = "Не удалось отметить привычку: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * Отмечает привычку как невыполненную
     */
    fun markHabitIncomplete(habitId: String) {
        viewModelScope.launch {
            try {
                // Обновляем UI состояние сразу для отзывчивости
                updateHabitCompletionStatus(habitId, false, isLoading = true)
                
                // Выполняем операцию
                markHabitIncompleteUseCase(habitId, currentUserId, today)
                
                // Обновляем финальное состояние
                updateHabitCompletionStatus(habitId, false, isLoading = false)
            } catch (e: Exception) {
                // В случае ошибки возвращаем предыдущее состояние
                updateHabitCompletionStatus(habitId, true, isLoading = false)
                _uiState.value = _uiState.value.copy(error = "Не удалось снять отметку: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * Переключает статус выполнения привычки
     */
    fun toggleHabitCompletion(habitId: String) {
        val currentHabit = _uiState.value.habitsWithCompletion.find { it.habit.id == habitId }
        if (currentHabit != null) {
            if (currentHabit.isCompletedToday) {
                markHabitIncomplete(habitId)
            } else {
                markHabitCompleted(habitId)
            }
        }
    }
    
    /**
     * Обновляет статус выполнения конкретной привычки в UI состоянии
     */
    private fun updateHabitCompletionStatus(habitId: String, isCompleted: Boolean, isLoading: Boolean) {
        val currentState = _uiState.value
        val updatedHabits = currentState.habitsWithCompletion.map { habitWithCompletion ->
            if (habitWithCompletion.habit.id == habitId) {
                habitWithCompletion.copy(isCompletedToday = isCompleted, isLoading = isLoading)
            } else {
                habitWithCompletion
            }
        }
        _uiState.value = currentState.copy(habitsWithCompletion = updatedHabits)
    }
    
    /**
     * Очищает сообщение об ошибке
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
