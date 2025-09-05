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
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.presentation.ui.habits.components.ParticipantProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val deleteHabitUseCase: DeleteHabitUseCase,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HabitsViewModel"
    }

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()
    
    // Prevent parallel collection of habits flow
    private var habitsJob: Job? = null
    
    // Pending deletion state for UNDO support
    private var pendingDeletion: PendingDeletion? = null
    private data class PendingDeletion(val habitWithCompletion: HabitWithCompletion, val index: Int)
    
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
        Log.d(TAG, "🔄 [loadHabits] Start for user=$userId")
        // Cancel previous collection if any
        if (habitsJob != null) {
            Log.d(TAG, "⛔ [loadHabits] Cancelling previous habits subscription")
            habitsJob?.cancel()
        }

        var emissionCount = 0
        val job = getHabitsUseCase(userId)
            // Normalize ordering to make equality stable
            .map { habits -> habits.sortedBy { it.id } }
            // Skip identical lists to avoid redundant UI updates and status reloads
            .distinctUntilChanged()
            .onEach { habits ->
                emissionCount += 1
                Log.d(TAG, "📊 [habits][flow] emission #$emissionCount, size=${habits.size} (user=$userId)")
                habits.forEach { habit ->
                    Log.d(TAG, "   📋 [habit] ${habit.title} (${habit.id}), participants=${habit.participants}")
                }

                // Pre-populate UI with loading flags for completion status
                val habitsWithCompletion = habits.map { habit ->
                    HabitWithCompletion(habit = habit, isLoading = true)
                }
                _uiState.value = HabitsUiState(habitsWithCompletion = habitsWithCompletion, isLoading = false)
                Log.d(TAG, "✅ [habits][ui] list updated (${habitsWithCompletion.size})")

                // Load per-habit completion in background
                loadCompletionStatuses(habits, userId)
            }
            .catch { e ->
                Log.e(TAG, "❌ [habits][error] ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Unknown error"
                )
            }
            .launchIn(viewModelScope)

        habitsJob = job
        habitsJob?.invokeOnCompletion { cause ->
            if (cause == null) {
                Log.d(TAG, "🛑 [loadHabits] Subscription completed normally")
            } else {
                Log.d(TAG, "🛑 [loadHabits] Subscription cancelled/failed: ${cause.message}")
            }
        }
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
     * Начинает удаление привычки с оптимистичным удалением из UI, но без фактического вызова use case.
     * Используется совместно со Snackbar (UNDO). Для завершения вызовите [confirmPendingDeletion] или [cancelPendingDeletion].
     */
    fun requestDeleteHabit(habitId: String) {
        if (pendingDeletion != null) return // already have one pending
        val currentList = _uiState.value.habitsWithCompletion
        val index = currentList.indexOfFirst { it.habit.id == habitId }
        if (index == -1) return
        val removedItem = currentList[index]
        pendingDeletion = PendingDeletion(removedItem, index)
        _uiState.value = _uiState.value.copy(
            habitsWithCompletion = currentList.filterNot { it.habit.id == habitId }
        )
    }

    /**
     * Подтверждает удаление ожидающего элемента и выполняет use case.
     * В случае ошибки восстанавливает элемент и показывает ошибку.
     */
    fun confirmPendingDeletion() {
        val deletion = pendingDeletion ?: return
        pendingDeletion = null
        viewModelScope.launch {
            try {
                deleteHabitUseCase(
                    deletion.habitWithCompletion.habit.id,
                    currentUserId
                )
            } catch (e: Exception) {
                val restored = _uiState.value.habitsWithCompletion.toMutableList()
                val restoreIndex = deletion.index.coerceIn(0, restored.size)
                restored.add(restoreIndex, deletion.habitWithCompletion)
                _uiState.value = _uiState.value.copy(
                    habitsWithCompletion = restored,
                    error = getApplication<Application>().getString(R.string.error_delete_habit, e.localizedMessage ?: "")
                )
            }
        }
    }

    /**
     * Отменяет ожидающее удаление и возвращает элемент в исходную позицию.
     */
    fun cancelPendingDeletion() {
        val deletion = pendingDeletion ?: return
        pendingDeletion = null
        val restored = _uiState.value.habitsWithCompletion.toMutableList()
        val restoreIndex = deletion.index.coerceIn(0, restored.size)
        restored.add(restoreIndex, deletion.habitWithCompletion)
        _uiState.value = _uiState.value.copy(habitsWithCompletion = restored)
    }
    
    /**
     * Удаляет привычку с оптимистичным обновлением UI. В случае ошибки откатываем изменения и показываем ошибку.
     */
    fun deleteHabit(habitId: String) {
        val currentList = _uiState.value.habitsWithCompletion
        val index = currentList.indexOfFirst { it.habit.id == habitId }
        if (index == -1) return
        val removedItem = currentList[index]

        // Оптимистично обновляем UI
        _uiState.value = _uiState.value.copy(
            habitsWithCompletion = currentList.filterNot { it.habit.id == habitId }
        )

        viewModelScope.launch {
            try {
                deleteHabitUseCase(habitId, currentUserId)
            } catch (e: Exception) {
                // Откатываем
                val restored = _uiState.value.habitsWithCompletion.toMutableList()
                if (index <= restored.size) {
                    restored.add(index, removedItem)
                } else {
                    restored.add(removedItem)
                }
                _uiState.value = _uiState.value.copy(
                    habitsWithCompletion = restored,
                    error = getApplication<Application>().getString(R.string.error_delete_habit, e.localizedMessage ?: "")
                )
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
