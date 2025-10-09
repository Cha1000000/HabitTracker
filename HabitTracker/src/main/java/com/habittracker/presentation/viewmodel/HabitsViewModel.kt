package com.habittracker.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.EnsureMySettingsForHabitUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.GetWeekCompletionDataUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.habit.JoinHabitByCodeUseCase
import com.habittracker.presentation.state.HabitsUiState
import com.habittracker.presentation.ui.habits.components.ParticipantProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val getHabitsUseCase: GetHabitsUseCase,
    private val markHabitCompletedUseCase: MarkHabitCompletedUseCase,
    private val markHabitIncompleteUseCase: MarkHabitIncompleteUseCase,
    private val getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase,
    private val authRepository: AuthRepository,
    private val deleteHabitUseCase: DeleteHabitUseCase,
    private val ensureMySettingsForHabitUseCase: EnsureMySettingsForHabitUseCase,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase,
    private val joinHabitByCodeUseCase: JoinHabitByCodeUseCase,
    private val getWeekCompletionDataUseCase: GetWeekCompletionDataUseCase,
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

    // Reactive rescheduling disabled to avoid loops; we schedule on demand where settings change
    private val lastScheduleKeyByHabit: MutableMap<String, String> = mutableMapOf()
    
    private val currentUserId: String
        get() = authRepository.getCurrentUserId() 
            ?: throw IllegalStateException("User must be authenticated to access habits")
    private val today: LocalDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    init {
        val userId = authRepository.getCurrentUserId()
        Log.d(TAG, "🚀 [init] Initializing HabitsViewModel for user: $userId")
        if (userId != null) {
            // Инициализация с сегодняшней датой
            _uiState.value = _uiState.value.copy(selectedDate = today)
            loadHabitsForDate(userId, today)
            loadWeekCompletionData(userId)
        } else {
            Log.w(TAG, "⚠️ [init] No user ID found, cannot load habits")
        }
    }

    /**
     * Обновляет данные (pull-to-refresh). Безопасно перезапускает подписку на список привычек
     * для текущего авторизованного пользователя.
     */
    fun refresh() {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Log.w(TAG, "⚠️ [refresh] No user ID, ignoring refresh")
            return
        }
        val selectedDate = _uiState.value.selectedDate ?: today
        Log.d(TAG, "🔁 [refresh] Reloading habits for user=$userId, date=$selectedDate")
        loadHabitsForDate(userId, selectedDate)
        loadWeekCompletionData(userId)
    }
    
    /**
     * Выбирает дату для просмотра
     */
    fun selectDate(date: LocalDate) {
        val userId = authRepository.getCurrentUserId() ?: return
        Log.d(TAG, "📅 [selectDate] Selecting date: $date")
        _uiState.value = _uiState.value.copy(selectedDate = date)
        Log.d(TAG, "📅 [selectDate] Updated selectedDate in state: ${_uiState.value.selectedDate}")
        loadHabitsForDate(userId, date)
    }
    
    /**
     * Переходит на текущую дату
     */
    fun goToToday() {
        selectDate(today)
    }
    
    /**
     * Получает список дат для текущей недели
     */
    fun getCurrentWeekDates(): List<LocalDate> {
        val selectedDate = _uiState.value.selectedDate ?: today
        val startOfWeek = getStartOfWeek(selectedDate)
        return (0..6).map { startOfWeek.plus(it, DateTimeUnit.DAY) }
    }
    
    /**
     * Получает начало недели для указанной даты (понедельник)
     */
    private fun getStartOfWeek(date: LocalDate): LocalDate {
        val daysFromMonday = when(date.dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        return date.minus(daysFromMonday, DateTimeUnit.DAY)
    }

    /**
     * Загружает список привычек для пользователя на определённую дату.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadHabitsForDate(userId: String, date: LocalDate) {
        Log.d(TAG, "🔄 [loadHabitsForDate] Start for user=$userId, date=$date")
        // Cancel previous collection if any
        if (habitsJob != null) {
            Log.d(TAG, "⛔ [loadHabitsForDate] Cancelling previous habits subscription")
            habitsJob?.cancel()
        }

        var emissionCount = 0
        habitsJob = getHabitsUseCase(userId)
            // Normalize ordering to make equality stable
            .map { habits -> habits.sortedBy { it.id } }
            // Skip identical lists to avoid redundant UI updates and status reloads
            .distinctUntilChanged()
            // For each emission, emit skeleton first, then the resolved statuses
            .flatMapLatest { habits ->
                emissionCount += 1
                Log.d(TAG, "📊 [habits][flow] emission #$emissionCount, size=${habits.size} (user=$userId)")
                habits.forEach { habit ->
                    Log.d(TAG, "   📋 [habit] ${habit.title} (${habit.id}), participants=${habit.participants}")
                }

                flow {
                    // 1) Skeleton with per-item loading
                    val skeleton = habits.map { habit ->
                        HabitWithCompletion(habit = habit, isLoading = true)
                    }
                    emit(_uiState.value.copy(habitsWithCompletion = skeleton, isLoading = false))

                    // 2) Parallel resolve completion statuses
                    val updatedHabits = coroutineScope {
                        habits.map { habit ->
                            async {
                                // Ленивая инициализация персональных настроек для текущего пользователя
                                try { ensureMySettingsForHabitUseCase(habit) } catch (e: Exception) {
                                    Log.d(TAG, "ensureMySettingsForHabitUseCase failed (non-fatal) for habit=${habit.id}: ${e.message}")
                                }

                                // Планируем уведомление только если есть изменения значимых полей
                                val scheduleKey = buildString {
                                    append(habit.reminderEnabled)
                                    append("|")
                                    append(habit.reminderTime?.toString() ?: "null")
                                    append("|")
                                    append(habit.reminderDays.sortedBy { it.value }.joinToString(","))
                                    append("|")
                                    append(habit.frequency.name)
                                    append("|")
                                    append(habit.isActive)
                                }
                                val lastKey = lastScheduleKeyByHabit[habit.id]
                                if (lastKey != scheduleKey) {
                                    lastScheduleKeyByHabit[habit.id] = scheduleKey
                                    try { scheduleHabitReminderUseCase(habit) } catch (e: Exception) {
                                        Log.w(TAG, "scheduleHabitReminderUseCase failed for habit=${habit.id}: ${e.message}", e)
                                    }
                                }

                                val isCompleted = try {
                                    getHabitCompletionStatusUseCase(habit.id, userId, date)
                                } catch (e: Exception) {
                                    Log.d(TAG, "getHabitCompletionStatusUseCase failed for habit=${habit.id}: ${e.message}")
                                    false
                                }
                                HabitWithCompletion(
                                    habit = habit,
                                    isCompletedToday = isCompleted,
                                    isLoading = false
                                )
                            }
                        }.awaitAll()
                    }
                    emit(_uiState.value.copy(habitsWithCompletion = updatedHabits, isLoading = false))
                }
            }
            .onEach { state ->
                // Merge with existing state to preserve selectedDate and other fields
                _uiState.value = _uiState.value.copy(
                    habitsWithCompletion = state.habitsWithCompletion,
                    isLoading = state.isLoading
                )
                Log.d(TAG, "✅ [habits][ui] list updated (${state.habitsWithCompletion.size})")
            }
            .catch { e ->
                Log.e(TAG, "❌ [habits][error] ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Unknown error"
                )
            }
            .launchIn(viewModelScope)

        habitsJob?.invokeOnCompletion { cause ->
            if (cause == null) {
                Log.d(TAG, "🛑 [loadHabitsForDate] Subscription completed normally")
            } else {
                Log.d(TAG, "🛑 [loadHabitsForDate] Subscription cancelled/failed: ${cause.message}")
            }
        }
    }
    
    /**
     * Загружает данные о выполнении привычек за неделю
     */
    private fun loadWeekCompletionData(userId: String) {
        viewModelScope.launch {
            try {
                val selectedDate = _uiState.value.selectedDate ?: today
                val completionData = getWeekCompletionDataUseCase(userId, selectedDate)
                _uiState.value = _uiState.value.copy(weekCompletionData = completionData)
            } catch (e: Exception) {
                Log.e(TAG, "❌ [loadWeekCompletionData] Error loading week data: ${e.message}", e)
            }
        }
    }

    /**
     * Отмечает привычку как выполненную
     */
    fun markHabitCompleted(habitId: String, note: String? = null) {
        viewModelScope.launch {
            try {
                val selectedDate = _uiState.value.selectedDate ?: today
                // Обновляем UI состояние сразу для отзывчивости
                updateHabitCompletionStatus(habitId, true, isLoading = true)
                
                // Выполняем операцию
                markHabitCompletedUseCase(habitId, currentUserId, selectedDate, note)
                
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
                val selectedDate = _uiState.value.selectedDate ?: today
                // Обновляем UI состояние сразу для отзывчивости
                updateHabitCompletionStatus(habitId, false, isLoading = true)
                
                // Выполняем операцию
                markHabitIncompleteUseCase(habitId, currentUserId, selectedDate)
                
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
            // Обновляем данные за неделю после изменения статуса
            viewModelScope.launch {
                loadWeekCompletionData(currentUserId)
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
     * Присоединяется к привычке по коду приглашения
     */
    fun joinByInviteCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(R.string.join_habit_auth_required)
                    )
                    return@launch
                }

                joinHabitByCodeUseCase(code, userId)
                    .onSuccess { habit ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null,
                            message = getApplication<Application>().getString(R.string.join_habit_success, habit.title)
                        )
                        refresh()
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: getApplication<Application>().getString(R.string.join_habit_error_generic)
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = getApplication<Application>().getString(R.string.join_habit_error, e.message ?: "")
                )
            }
        }
    }
    
    /**
     * Очищает сообщение об ошибке
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
