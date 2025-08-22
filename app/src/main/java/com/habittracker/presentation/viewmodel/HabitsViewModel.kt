package com.habittracker.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.presentation.ui.habits.components.ParticipantProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Данные о привычке с информацией о выполнении
 */
data class HabitWithCompletion(
    val habit: Habit,
    val isCompletedToday: Boolean = false,
    val isLoading: Boolean = false,
    val participantsProgress: List<ParticipantProgress> = emptyList() // Прогресс участников для групповых привычек
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
    private val getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase,
    private val authRepository: AuthRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HabitsViewModel"
    }

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()
    
    private val currentUserId: String
        get() = authRepository.getCurrentUserId() 
            ?: throw IllegalStateException("User must be authenticated to access habits")
    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    init {
        val userId = authRepository.getCurrentUserId()
        Log.d(TAG, "🚀 [init] Initializing HabitsViewModel for user: $userId")
        if (userId != null) {
            loadHabits(userId)
        } else {
            Log.w(TAG, "⚠️ [init] No user ID found, cannot load habits")
        }
    }

    /**
     * Загружает список привычек для пользователя.
     */
    fun loadHabits(userId: String) {
        Log.d(TAG, "🔄 [loadHabits] Starting to load habits for user: $userId")
        getHabitsUseCase(userId)
            .onEach { habits ->
                Log.d(TAG, "📊 [loadHabits] Received ${habits.size} habits from Flow for user: $userId")
                habits.forEach { habit ->
                    Log.d(TAG, "   📋 [loadHabits] Habit: ${habit.title} (${habit.id}), participants: ${habit.participants}")
                }
                
                // Загружаем статус выполнения для каждой привычки
                val habitsWithCompletion = habits.map { habit ->
                    HabitWithCompletion(habit = habit, isLoading = true)
                }
                _uiState.value = HabitsUiState(habitsWithCompletion = habitsWithCompletion, isLoading = false)
                Log.d(TAG, "✅ [loadHabits] Updated UI state with ${habitsWithCompletion.size} habits")
                
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
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.error_mark_habit, e.localizedMessage ?: ""))
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
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.error_unmark_habit, e.localizedMessage ?: ""))
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
