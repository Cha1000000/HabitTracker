package com.habittracker.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.UserPreferencesRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.EnsureMySettingsForHabitUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import com.habittracker.domain.usecase.GetHabitsForDateUseCase
import com.habittracker.domain.usecase.GetWeekCompletionDataUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.UpdateHabitEntryValueUseCase
import com.habittracker.domain.usecase.habit.AttachPhotoToEntryUseCase
import com.habittracker.domain.usecase.habit.GetGroupHabitProgressUseCase
import com.habittracker.domain.usecase.habit.JoinHabitByCodeUseCase
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import com.habittracker.presentation.state.HabitsUiState
import com.habittracker.presentation.ui.habits.components.ParticipantProgress
import com.habittracker.presentation.widget.TodayHabitsWidgetScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
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
import timber.log.Timber
import javax.inject.Inject

/**
 * Данные о привычке с информацией о выполнении
 */
@Immutable
data class HabitWithCompletion(
    val habit: Habit,
    val isCompletedToday: Boolean = false,
    val isRestDayToday: Boolean = false,
    val isLoading: Boolean = false,
    val participantsProgress: List<ParticipantProgress> = emptyList(), // Прогресс участников для групповых привычек
    val currentValue: Int = 0, // Текущее значение выполнения (для измеримых привычек)
    val photoUrl: String? = null, // URL фото-подтверждения для текущего дня
    val note: String? = null // Заметка для текущего дня
)

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val getHabitsForDateUseCase: GetHabitsForDateUseCase,
    private val markHabitCompletedUseCase: MarkHabitCompletedUseCase,
    private val markHabitIncompleteUseCase: MarkHabitIncompleteUseCase,
    private val updateHabitEntryValueUseCase: UpdateHabitEntryValueUseCase,
    private val getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val deleteHabitUseCase: DeleteHabitUseCase,
    private val ensureMySettingsForHabitUseCase: EnsureMySettingsForHabitUseCase,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase,
    private val joinHabitByCodeUseCase: JoinHabitByCodeUseCase,
    private val getWeekCompletionDataUseCase: GetWeekCompletionDataUseCase,
    private val getGroupHabitProgressUseCase: GetGroupHabitProgressUseCase,
    private val setHabitRestDayUseCase: com.habittracker.domain.usecase.SetHabitRestDayUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val analyticsManager: AnalyticsManager,
    private val errorLogger: ErrorLogger,
    private val attachPhotoToEntryUseCase: AttachPhotoToEntryUseCase,
    private val habitEntryRepository: com.habittracker.domain.repository.HabitEntryRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HabitsViewModel"
    }

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()
    
    // Prevent parallel collection of habits flow
    private var habitsJob: Job? = null
    
    // Job для подписки на прогресс групповых привычек
    private var groupProgressJobs: MutableMap<String, Job> = mutableMapOf()
    
    // Pending deletion state for UNDO support
    private var pendingDeletion: PendingDeletion? = null
    private data class PendingDeletion(val habitWithCompletion: HabitWithCompletion, val index: Int)

    private fun dedupeByHabitId(list: List<HabitWithCompletion>): List<HabitWithCompletion> {
        if (list.size <= 1) return list
        val seen = HashSet<String>(list.size)
        return list.filter { seen.add(it.habit.id) }
    }

    private fun restoreDeduped(list: List<HabitWithCompletion>, item: HabitWithCompletion, index: Int): List<HabitWithCompletion> {
        val mutable = list.toMutableList()
        mutable.removeAll { it.habit.id == item.habit.id }
        val insertIndex = index.coerceIn(0, mutable.size)
        mutable.add(insertIndex, item)
        return dedupeByHabitId(mutable)
    }

    // Reactive rescheduling disabled to avoid loops; we schedule on demand where settings change
    private val lastScheduleKeyByHabit: MutableMap<String, String> = mutableMapOf()
    
    // Кеш данных о прогрессе выполнения для разных недель (ключ - начало недели)
    private val weekCompletionCache: MutableMap<LocalDate, Map<LocalDate, Float>> = mutableMapOf()
    
    // Кеш последних настроек пользователя для синхронного доступа при сортировке
    @Volatile
    private var cachedUserPreferences: com.habittracker.domain.model.UserPreferences? = null
    
    private val currentUserId: String
        get() = authRepository.getCurrentUserId() 
            ?: throw IllegalStateException("User must be authenticated to access habits")
    private val today: LocalDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    init {
        val userId = authRepository.getCurrentUserId()
        Timber.tag(TAG).d("🚀 [init] Initializing HabitsViewModel for user: $userId")
        if (userId != null) {
            // Инициализация с сегодняшней датой
            val weekStart = getStartOfWeek(today)
            _uiState.value = _uiState.value.copy(
                selectedDate = today,
                currentWeekStartDate = weekStart
            )
            loadHabitsForDate(userId, today)
            loadWeekCompletionData(userId, weekStart)
            loadCurrentUserAvatar(userId)
        } else {
            Timber.tag(TAG).w("⚠️ [init] No user ID found, cannot load habits")
        }
    }
    
    private fun loadCurrentUserAvatar(userId: String) {
        userRepository.observeUser(userId)
            .onEach { user ->
                _uiState.value = _uiState.value.copy(
                    currentUserAvatarUrl = user?.avatarThumbnailUrl ?: user?.avatarUrl
                )
            }
            .catch { e ->
                Timber.tag(TAG).w("Failed to load user avatar: ${e.message}")
            }
            .launchIn(viewModelScope)
    }

    /**
     * Обновляет данные (pull-to-refresh). Безопасно перезапускает подписку на список привычек
     * для текущего авторизованного пользователя.
     */
    fun refresh() {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Timber.tag(TAG).w("⚠️ [refresh] No user ID, ignoring refresh")
            return
        }
        val selectedDate = _uiState.value.selectedDate ?: today
        val weekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
        Timber.tag(TAG).d("🔁 [refresh] Reloading habits for user=$userId, date=$selectedDate")
        loadHabitsForDate(userId, selectedDate)
        loadWeekCompletionData(userId, weekStart, forceReload = true)
    }
    
    /**
     * Выбирает дату для просмотра
     */
    fun selectDate(date: LocalDate) {
        Timber.tag(TAG).d("📅 [selectDate] Selected date: $date")
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Timber.tag(TAG).w("⚠️ [selectDate] No user ID")
            return
        }
        val weekStart = getStartOfWeek(date)
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            currentWeekStartDate = weekStart
        )
        loadHabitsForDate(userId, date)
    }
    
    /**
     * Переходит на текущую дату
     */
    fun goToToday() {
        selectDate(today)
    }
    
    /**
     * Переходит на предыдущую неделю
     */
    fun goToPreviousWeek() {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Timber.tag(TAG).w("⚠️ [goToPreviousWeek] No user ID")
            return
        }
        
        val currentWeekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
        val previousWeekStart = currentWeekStart.minus(7, DateTimeUnit.DAY)
        
        // Проверяем минимальную границу
        val oldestDate = _uiState.value.oldestHabitDate
        if (oldestDate != null) {
            val startOfOldestWeek = getStartOfWeek(oldestDate)
            
            // Не позволяем уйти раньше недели с самой старой привычкой
            if (previousWeekStart < startOfOldestWeek) {
                Timber.tag(TAG).d("⛔ [goToPreviousWeek] Reached minimum date boundary: $oldestDate")
                return
            }
        }
        
        // Обновляем только currentWeekStartDate без изменения selectedDate
        _uiState.value = _uiState.value.copy(currentWeekStartDate = previousWeekStart)
        Timber.tag(TAG).d("📅 [goToPreviousWeek] Week changed to: $previousWeekStart (selectedDate unchanged)")
        
        // Загружаем данные о прогрессе для новой недели
        loadWeekCompletionData(userId, previousWeekStart)
    }
    
    /**
     * Переходит на следующую неделю
     */
    fun goToNextWeek() {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Timber.tag(TAG).w("⚠️ [goToNextWeek] No user ID")
            return
        }
        
        val currentWeekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
        val nextWeekStart = currentWeekStart.plus(7, DateTimeUnit.DAY)
        
        // Проверяем максимальную границу (сегодня + 1 год)
        val maxDate = _uiState.value.maxFutureDate ?: today.plus(1, DateTimeUnit.YEAR)
        if (nextWeekStart > maxDate) {
            Timber.tag(TAG).d("⛔ [goToNextWeek] Reached maximum date boundary: $maxDate")
            return
        }
        
        // Обновляем только currentWeekStartDate без изменения selectedDate
        _uiState.value = _uiState.value.copy(currentWeekStartDate = nextWeekStart)
        Timber.tag(TAG).d("📅 [goToNextWeek] Week changed to: $nextWeekStart (selectedDate unchanged)")
        
        // Загружаем данные о прогрессе для новой недели
        loadWeekCompletionData(userId, nextWeekStart)
    }
    
    /**
     * Проверяет, можно ли перейти на предыдущую неделю
     */
    fun canGoToPreviousWeek(): Boolean {
        val currentWeekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
        val previousWeekStart = currentWeekStart.minus(7, DateTimeUnit.DAY)
        val oldestDate = _uiState.value.oldestHabitDate ?: return true
        
        val startOfOldestWeek = getStartOfWeek(oldestDate)
        
        return previousWeekStart >= startOfOldestWeek
    }
    
    /**
     * Проверяет, можно ли перейти на следующую неделю
     */
    fun canGoToNextWeek(): Boolean {
        val currentWeekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
        val nextWeekStart = currentWeekStart.plus(7, DateTimeUnit.DAY)
        val maxDate = _uiState.value.maxFutureDate ?: today.plus(1, DateTimeUnit.YEAR)
        
        return nextWeekStart <= maxDate
    }
    
    /**
     * Получает список дат для текущей недели
     */
    fun getCurrentWeekDates(): List<LocalDate> {
        val weekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
        return (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }
    }
    
    /**
     * Проверяет, находится ли текущая видимая неделя на неделе с сегодняшним днём
     */
    fun isCurrentWeekDisplayed(): Boolean {
        val currentWeekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
        val todayWeekStart = getStartOfWeek(today)
        return currentWeekStart == todayWeekStart
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
        Timber.tag(TAG).d("🔄 [loadHabitsForDate] Start for user=$userId, date=$date")
        // Cancel previous collection if any
        if (habitsJob != null) {
            Timber.tag(TAG).d("⛔ [loadHabitsForDate] Cancelling previous habits subscription")
            habitsJob?.cancel()
        }

        var emissionCount = 0
        habitsJob = combine(
            getHabitsForDateUseCase(userId, date),
            userPreferencesRepository.userPreferences
        ) { habits, preferences ->
            Pair(habits, preferences)
        }
            // Normalize ordering to make equality stable
            .map { (habits, preferences) -> 
                Pair(habits.sortedBy { it.id }, preferences) 
            }
            // Skip identical lists to avoid redundant UI updates and status reloads
            // We use a custom comparator or just rely on distinctUntilChanged if data classes are correct
            // Here we rely on Pair equals
            .distinctUntilChanged()
            // For each emission, emit skeleton first, then the resolved statuses
            .flatMapLatest { (habits, preferences) ->
                // Кешируем настройки для синхронного доступа при сортировке
                cachedUserPreferences = preferences
                
                emissionCount += 1
                Timber.tag(TAG).d("📊 [habits][flow] emission #$emissionCount, size=${habits.size} (user=$userId)")
                habits.forEach { habit ->
                    Timber.tag(TAG).d("   📋 [habit] ${habit.title} (${habit.id}), participants=${habit.participants}")
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
                                try {
                                    ensureMySettingsForHabitUseCase(habit)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Timber.tag(TAG).d("ensureMySettingsForHabitUseCase failed (non-fatal) for habit=${habit.id}: ${e.message}")
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
                                    try {
                                        scheduleHabitReminderUseCase(habit)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Timber.tag(TAG).w(e, "scheduleHabitReminderUseCase failed for habit=${habit.id}: ${e.message}")
                                    }
                                }

                                val habitEntry = try {
                                    getHabitCompletionStatusUseCase.getEntry(habit.id, userId, date)
                                } catch (_: Exception) {
                                    null
                                }
                                val isCompleted = habitEntry?.completed ?: false
                                val isRestDay = habitEntry?.isRestDay ?: false
                                val currentValue = habitEntry?.value ?: 0
                                
                                // Загружаем прогресс участников для групповых привычек
                                val participantsProgress = if (habit.isGroupHabit()) {
                                    try {
                                        val groupProgress = getGroupHabitProgressUseCase(habit.id, date).firstOrNull()
                                        groupProgress?.participants?.map { p ->
                                            ParticipantProgress(
                                                userId = p.user.id,
                                                userName = p.user.name,
                                                isCompletedToday = p.completedToday,
                                                currentStreak = p.currentStreak,
                                                completionRate = p.completionRate,
                                                avatarUrl = p.user.avatarThumbnailUrl ?: p.user.avatarUrl
                                            )
                                        } ?: emptyList()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Timber.tag(TAG).d("getGroupHabitProgressUseCase failed for habit=${habit.id}: ${e.message}")
                                        emptyList()
                                    }
                                } else {
                                    emptyList()
                                }
                                
                                HabitWithCompletion(
                                    habit = habit,
                                    isCompletedToday = isCompleted,
                                    isRestDayToday = isRestDay,
                                    isLoading = false,
                                    participantsProgress = participantsProgress,
                                    currentValue = currentValue,
                                    photoUrl = habitEntry?.photoUrl,
                                    note = habitEntry?.note
                                )
                            }
                        }.awaitAll()
                    }
                    
                    // Apply sorting based on preferences
                    val sortedHabits = updatedHabits.sortedWith(
                        // Сначала сортируем по времени суток
                        compareBy<HabitWithCompletion> { it.habit.timeOfDay.ordinal }
                            // Затем по статусу выполнения (если включена настройка)
                            .thenBy { if (preferences.sortCompletedHabitsToBottom) it.isCompletedToday else false }
                            .thenBy { it.habit.id }
                    )
                    
                    // 3) Вычисляем границы дат
                    val oldestDate = habits.minOfOrNull { it.createdAt }
                    val maxFutureDate = today.plus(1, DateTimeUnit.YEAR)
                    
                    emit(_uiState.value.copy(
                        habitsWithCompletion = sortedHabits,
                        isLoading = false,
                        oldestHabitDate = oldestDate,
                        maxFutureDate = maxFutureDate
                    ))
                }
            }
            .onEach { state ->
                // Merge with existing state to preserve selectedDate and other fields
                _uiState.value = _uiState.value.copy(
                    habitsWithCompletion = dedupeByHabitId(state.habitsWithCompletion),
                    isLoading = state.isLoading,
                    oldestHabitDate = state.oldestHabitDate,
                    maxFutureDate = state.maxFutureDate
                )
                Timber.tag(TAG).d("✅ [habits][ui] list updated (${state.habitsWithCompletion.size})")
                
                // Подписываемся на реактивные обновления прогресса групповых привычек
                subscribeToGroupHabitsProgress(state.habitsWithCompletion)
            }
            .catch { e ->
                Timber.tag(TAG).e(e, "❌ [habits][error] ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Unknown error"
                )
            }
            .launchIn(viewModelScope)

        habitsJob?.invokeOnCompletion { cause ->
            if (cause == null) {
                Timber.tag(TAG).d("🛑 [loadHabitsForDate] Subscription completed normally")
            } else {
                Timber.tag(TAG).d("🛑 [loadHabitsForDate] Subscription cancelled/failed: ${cause.message}")
            }
        }
    }
    
    // Текущая дата для которой загружен прогресс групповых привычек
    private var lastGroupProgressDate: LocalDate? = null
    
    /**
     * Подписывается на реактивные обновления прогресса групповых привычек
     */
    private fun subscribeToGroupHabitsProgress(habits: List<HabitWithCompletion>) {
        val groupHabits = habits.filter { it.habit.isGroupHabit() }
        val currentHabitIds = groupHabits.map { it.habit.id }.toSet()
        val selectedDate = _uiState.value.selectedDate ?: today
        
        // Если дата изменилась, отменяем все подписки и создаём новые
        val dateChanged = lastGroupProgressDate != selectedDate
        if (dateChanged) {
            groupProgressJobs.values.forEach { it.cancel() }
            groupProgressJobs.clear()
            lastGroupProgressDate = selectedDate
        }
        
        // Отменяем подписки для привычек, которых больше нет в списке
        groupProgressJobs.keys.toList().forEach { habitId ->
            if (habitId !in currentHabitIds) {
                groupProgressJobs[habitId]?.cancel()
                groupProgressJobs.remove(habitId)
            }
        }
        
        // Создаём подписки для новых групповых привычек
        groupHabits.forEach { habitWithCompletion ->
            val habitId = habitWithCompletion.habit.id
            
            // Пропускаем, если уже есть активная подписка
            if (groupProgressJobs[habitId]?.isActive == true) return@forEach
            
            groupProgressJobs[habitId] = viewModelScope.launch {
                getGroupHabitProgressUseCase(habitId, selectedDate)
                    .catch { e ->
                        Timber.tag(TAG).d("Group progress subscription error for habit=$habitId: ${e.message}")
                    }
                    .collect { groupProgress ->
                        if (groupProgress != null) {
                            val participantsProgress = groupProgress.participants.map { p ->
                                ParticipantProgress(
                                    userId = p.user.id,
                                    userName = p.user.name,
                                    isCompletedToday = p.completedToday,
                                    currentStreak = p.currentStreak,
                                    completionRate = p.completionRate,
                                    avatarUrl = p.user.avatarThumbnailUrl ?: p.user.avatarUrl
                                )
                            }
                            
                            // Обновляем только participantsProgress для этой привычки
                            _uiState.value = _uiState.value.copy(
                                habitsWithCompletion = _uiState.value.habitsWithCompletion.map { hwc ->
                                    if (hwc.habit.id == habitId) {
                                        hwc.copy(participantsProgress = participantsProgress)
                                    } else {
                                        hwc
                                    }
                                }
                            )
                        }
                    }
            }
        }
    }
    
    /**
     * Загружает данные о выполнении привычек за неделю с кешированием
     */
    private fun loadWeekCompletionData(userId: String, weekStartDate: LocalDate, forceReload: Boolean = false) {
        viewModelScope.launch {
            try {
                // Проверяем кеш, если не требуется принудительная перезагрузка
                if (!forceReload && weekCompletionCache.containsKey(weekStartDate)) {
                    Timber.tag(TAG).d("📦 [loadWeekCompletionData] Using cached data for week: $weekStartDate")
                    _uiState.value = _uiState.value.copy(weekCompletionData = weekCompletionCache[weekStartDate]!!)
                    return@launch
                }
                
                Timber.tag(TAG).d("🔄 [loadWeekCompletionData] Loading data for week: $weekStartDate")
                // Загружаем данные для любой даты из этой недели (используем начало недели)
                val completionData = getWeekCompletionDataUseCase(userId, weekStartDate)
                
                // Сохраняем в кеш
                weekCompletionCache[weekStartDate] = completionData
                
                // Обновляем UI только если это текущая видимая неделя
                val currentWeekStart = _uiState.value.currentWeekStartDate ?: getStartOfWeek(today)
                if (weekStartDate == currentWeekStart) {
                    _uiState.value = _uiState.value.copy(weekCompletionData = completionData)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ [loadWeekCompletionData] Error loading week data: ${e.message}")
            }
        }
    }

    /**
     * Обновляет значение выполнения привычки
     */
    fun updateHabitValue(habitId: String, value: Int) {
        viewModelScope.launch {
            try {
                val selectedDate = _uiState.value.selectedDate ?: today
                
                // Получаем привычку для проверки targetValue
                val habit = _uiState.value.habitsWithCompletion.find { it.habit.id == habitId }?.habit
                val targetValue = habit?.targetValue ?: 1
                val isCompleted = value >= targetValue
                
                // Обновляем UI оптимистично с новым значением
                updateHabitCurrentValue(habitId, newValue = value, isCompleted = isCompleted, isLoading = true)
                
                updateHabitEntryValueUseCase(habitId, currentUserId, value, selectedDate)
                
                // Показываем конфетти при достижении цели
                if (isCompleted) {
                    _uiState.value = _uiState.value.copy(showConfetti = true)
                }
                
                // Скрываем диалог и снимаем флаг загрузки
                _uiState.value = _uiState.value.copy(habitIdForValueInput = null)
                updateHabitCurrentValue(habitId, newValue = value, isCompleted = isCompleted, isLoading = false)
                
                // Инвалидируем кеш недельной статистики
                val weekStart = getStartOfWeek(selectedDate)
                loadWeekCompletionData(currentUserId, weekStart, forceReload = true)
                
            } catch (e: Exception) {
                // В случае ошибки откатываем состояние
                val previousValue = _uiState.value.habitsWithCompletion
                    .find { it.habit.id == habitId }?.currentValue ?: 0
                _uiState.value = _uiState.value.copy(
                    error = getApplication<Application>().getString(R.string.error_update_habit, e.localizedMessage ?: ""),
                    habitIdForValueInput = null
                )
                updateHabitCurrentValue(habitId, newValue = previousValue, isCompleted = false, isLoading = false)
            }
        }
    }

    fun showValueInputDialog(habitId: String) {
        _uiState.value = _uiState.value.copy(habitIdForValueInput = habitId)
    }

    fun dismissValueInputDialog() {
        _uiState.value = _uiState.value.copy(habitIdForValueInput = null)
    }

    /**
     * Отмечает привычку как выполненную
     */
    fun markHabitCompleted(habitId: String, note: String? = null) {
        viewModelScope.launch {
            try {
                val selectedDate = _uiState.value.selectedDate ?: today
                
                // Track event
                analyticsManager.logEvent(
                    AnalyticsEvents.HABIT_COMPLETED,
                    mapOf(AnalyticsEvents.Params.HABIT_ID to habitId)
                )

                // Обновляем UI состояние сразу для отзывчивости
                updateHabitCompletionStatus(habitId, true, isLoading = true)
                
                // Выполняем операцию
                markHabitCompletedUseCase(habitId, currentUserId, selectedDate, note)
                
                // Обновляем финальное состояние
                updateHabitCompletionStatus(habitId, true, isLoading = false)
                
                // Получаем название привычки для SnackBar
                val habitTitle = _uiState.value.habitsWithCompletion
                    .find { it.habit.id == habitId }?.habit?.title ?: ""
                
                // Показываем конфетти и предложение добавить фото
                _uiState.value = _uiState.value.copy(
                    showConfetti = true,
                    completedHabitForPhotoProof = com.habittracker.presentation.state.CompletedHabitInfo(
                        habitId = habitId,
                        habitTitle = habitTitle,
                        date = selectedDate
                    )
                )
                
                // Инвалидируем кеш для недели с изменённой датой и перезагружаем данные
                val weekStart = getStartOfWeek(selectedDate)
                loadWeekCompletionData(currentUserId, weekStart, forceReload = true)
                TodayHabitsWidgetScheduler.enqueueRefresh(getApplication())
            } catch (e: Exception) {
                errorLogger.logError(e, "Failed to mark habit $habitId completed")
                // В случае ошибки возвращаем предыдущее состояние
                updateHabitCompletionStatus(habitId, false, isLoading = false)
                _uiState.value = _uiState.value.copy(
                    error = getApplication<Application>().getString(R.string.error_update_habit, e.localizedMessage ?: "")
                )
            }
        }
    }
    
    /**
     * Скрывает конфетти после завершения анимации
     */
    fun hideConfetti() {
        _uiState.value = _uiState.value.copy(showConfetti = false)
    }

    /**
     * Очищает информацию о выполненной привычке (после закрытия SnackBar)
     */
    fun clearCompletedHabitForPhotoProof() {
        _uiState.value = _uiState.value.copy(completedHabitForPhotoProof = null)
    }
    
    /**
     * Показывает BottomSheet для добавления фото-подтверждения
     */
    fun showPhotoSheet(habitId: String) {
        val habit = _uiState.value.habitsWithCompletion.find { it.habit.id == habitId } ?: return
        val selectedDate = _uiState.value.selectedDate ?: today
        
        _uiState.value = _uiState.value.copy(
            habitForPhotoSheet = com.habittracker.presentation.state.HabitPhotoSheetInfo(
                habitId = habitId,
                habitTitle = habit.habit.title,
                habitUnit = habit.habit.unit,
                date = selectedDate,
                photoUrl = habit.photoUrl,
                note = habit.note
            )
        )
    }
    
    /**
     * Скрывает BottomSheet для добавления фото-подтверждения
     */
    fun hidePhotoSheet() {
        _uiState.value = _uiState.value.copy(habitForPhotoSheet = null)
    }
    
    /**
     * Прикрепляет фото к записи привычки
     */
    fun attachPhoto(uri: Uri) {
        val photoSheetInfo = _uiState.value.habitForPhotoSheet ?: run {
            Timber.tag(TAG).w("attachPhoto: photoSheetInfo is null")
            return
        }
        
        Timber.tag(TAG).d("attachPhoto: Starting upload for uri=$uri, habitId=${photoSheetInfo.habitId}")
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
            try {
                Timber.tag(TAG).d("attachPhoto: Calling attachPhotoToEntryUseCase")
                val result = attachPhotoToEntryUseCase(
                    sourceUri = uri,
                    habitId = photoSheetInfo.habitId,
                    userId = currentUserId,
                    date = photoSheetInfo.date
                )
                
                Timber.tag(TAG).d("attachPhoto: UseCase result isSuccess=${result.isSuccess}, value=${result.getOrNull()}")
                
                if (result.isSuccess) {
                    val cloudUrl = result.getOrNull()
                    // Обновляем photoUrl в списке привычек
                    val updatedHabits = _uiState.value.habitsWithCompletion.map { hwc ->
                        if (hwc.habit.id == photoSheetInfo.habitId) {
                            hwc.copy(photoUrl = cloudUrl)
                        } else {
                            hwc
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        habitsWithCompletion = updatedHabits,
                        isUploadingPhoto = false,
                        habitForPhotoSheet = photoSheetInfo.copy(photoUrl = cloudUrl),
                        message = getApplication<Application>().getString(R.string.photo_proof_upload_success),
                        photoUploadSuccessMessage = getApplication<Application>().getString(R.string.photo_proof_upload_success)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                    error = getApplication<Application>().getString(R.string.photo_proof_upload_error)
                )
            }
        } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to attach photo")
                _uiState.value = _uiState.value.copy(
                    isUploadingPhoto = false,
                    error = getApplication<Application>().getString(R.string.photo_proof_upload_error)
                )
            }
        }
    }

    fun clearPhotoUploadSuccessMessage() {
        _uiState.value = _uiState.value.copy(photoUploadSuccessMessage = null)
    }

    /**
     * Обновляет заметку к записи привычки
     */
    fun updateHabitEntryNote(note: String) {
        val photoSheetInfo = _uiState.value.habitForPhotoSheet ?: return
        
        viewModelScope.launch {
            try {
                val entry = habitEntryRepository.getEntry(photoSheetInfo.habitId, currentUserId, photoSheetInfo.date)
                if (entry != null) {
                    habitEntryRepository.updateEntry(entry.copy(note = note))
                } else {
                    habitEntryRepository.insertEntry(
                        com.habittracker.domain.model.HabitEntry(
                            habitId = photoSheetInfo.habitId,
                            userId = currentUserId,
                            date = photoSheetInfo.date,
                            completed = false,
                            note = note
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(
                    photoUploadSuccessMessage = getApplication<Application>().getString(R.string.photo_proof_note_success)
                )
                // Refresh data to update the card in the list
                refresh()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to update note")
            }
        }
    }
    
    /**
     * Удаляет фото из записи привычки
     */
    fun deletePhoto() {
        val photoSheetInfo = _uiState.value.habitForPhotoSheet ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
            try {
                // Обновляем запись в БД (удаляем URL фото)
                habitEntryRepository.updatePhotoUrl(
                    habitId = photoSheetInfo.habitId,
                    userId = currentUserId,
                    date = photoSheetInfo.date,
                    photoUrl = null
                )
                
                // Обновляем photoUrl в списке привычек
                val updatedHabits = _uiState.value.habitsWithCompletion.map { hwc ->
                    if (hwc.habit.id == photoSheetInfo.habitId) {
                        hwc.copy(photoUrl = null)
                    } else {
                        hwc
                    }
                }
                _uiState.value = _uiState.value.copy(
                    habitsWithCompletion = updatedHabits,
                    isUploadingPhoto = false,
                    habitForPhotoSheet = photoSheetInfo.copy(photoUrl = null)
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to delete photo")
                _uiState.value = _uiState.value.copy(
                    isUploadingPhoto = false,
                    error = getApplication<Application>().getString(R.string.photo_proof_delete_error)
                )
            }
        }
    }
    
    /**
     * Очищает сообщение
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
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
                
                // Инвалидируем кеш для недели с изменённой датой и перезагружаем данные
                val weekStart = getStartOfWeek(selectedDate)
                loadWeekCompletionData(currentUserId, weekStart, forceReload = true)
                TodayHabitsWidgetScheduler.enqueueRefresh(getApplication())
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
        val selectedDate = _uiState.value.selectedDate ?: today
        
        // Блокируем отметку привычек в будущем
        if (selectedDate > today) {
            Timber.tag(TAG).d("⛔ [toggleHabitCompletion] Cannot mark habits in future date: $selectedDate")
            _uiState.value = _uiState.value.copy(
                error = getApplication<Application>().getString(R.string.error_cannot_mark_future_habit)
            )
            return
        }
        
        val currentHabit = _uiState.value.habitsWithCompletion.find { it.habit.id == habitId }
        if (currentHabit != null) {
            // Для измеримых привычек открываем диалог ввода значения
            if (currentHabit.habit.goalType == com.habittracker.domain.model.HabitGoalType.MEASURABLE) {
                showValueInputDialog(habitId)
                return
            }

            if (currentHabit.isCompletedToday) {
                markHabitIncomplete(habitId)
            } else {
                markHabitCompleted(habitId)
            }
            // Обновление данных за неделю происходит внутри markHabitCompleted/markHabitIncomplete
        }
    }
    
    /**
     * Переключает режим "выходной" для привычки
     */
    fun toggleRestDay(habitId: String) {
        viewModelScope.launch {
            try {
                val selectedDate = _uiState.value.selectedDate ?: today
                
                // Находим текущее состояние
                val currentState = _uiState.value
                val itemIndex = currentState.habitsWithCompletion.indexOfFirst { it.habit.id == habitId }
                
                if (itemIndex != -1) {
                    val item = currentState.habitsWithCompletion[itemIndex]
                    val newIsRestDay = !item.isRestDayToday
                    
                    // Оптимистичное обновление UI
                    val newCompleted = if (newIsRestDay) false else item.isCompletedToday
                    
                    val updatedItem = item.copy(
                        isRestDayToday = newIsRestDay,
                        isCompletedToday = newCompleted
                    )
                    
                    val updatedList = currentState.habitsWithCompletion.toMutableList()
                    updatedList[itemIndex] = updatedItem
                    
                    _uiState.value = currentState.copy(habitsWithCompletion = updatedList)
                    
                    // Вызов use case
                    setHabitRestDayUseCase(habitId, selectedDate, newIsRestDay)
                    
                    // Инвалидируем кеш статистики за неделю
                    val weekStart = getStartOfWeek(selectedDate)
                    loadWeekCompletionData(currentUserId, weekStart, forceReload = true)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error toggling rest day")
                // В идеале здесь нужен rollback UI, если запрос упал, но для простоты пока оставим лог
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.error_generic))
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
                
                // Track deletion event
                analyticsManager.logEvent(
                    AnalyticsEvents.HABIT_DELETED,
                    mapOf(
                        AnalyticsEvents.Params.HABIT_ID to deletion.habitWithCompletion.habit.id,
                        AnalyticsEvents.Params.HABIT_TYPE to deletion.habitWithCompletion.habit.type.name
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    habitsWithCompletion = restoreDeduped(
                        list = _uiState.value.habitsWithCompletion,
                        item = deletion.habitWithCompletion,
                        index = deletion.index
                    ),
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
        _uiState.value = _uiState.value.copy(
            habitsWithCompletion = restoreDeduped(
                list = _uiState.value.habitsWithCompletion,
                item = deletion.habitWithCompletion,
                index = deletion.index
            )
        )
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
                
                // Track deletion event
                analyticsManager.logEvent(
                    AnalyticsEvents.HABIT_DELETED,
                    mapOf(
                        AnalyticsEvents.Params.HABIT_ID to habitId,
                        AnalyticsEvents.Params.HABIT_TYPE to removedItem.habit.type.name
                    )
                )
            } catch (e: Exception) {
                // Откатываем
                _uiState.value = _uiState.value.copy(
                    habitsWithCompletion = restoreDeduped(
                        list = _uiState.value.habitsWithCompletion,
                        item = removedItem,
                        index = index
                    ),
                    error = getApplication<Application>().getString(R.string.error_delete_habit, e.localizedMessage ?: "")
                )
            }
        }
    }
    
    /**
     * Обновляет статус выполнения конкретной привычки в UI состоянии
     * и применяет сортировку согласно настройкам пользователя
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
        
        // Применяем сортировку только если не в состоянии загрузки (финальное обновление)
        val sortedHabits = if (!isLoading) {
            applySortingIfNeeded(updatedHabits)
        } else {
            updatedHabits
        }
        
        _uiState.value = currentState.copy(habitsWithCompletion = sortedHabits)
    }
    
    /**
     * Обновляет значение (currentValue) для измеримой привычки в UI состоянии
     */
    private fun updateHabitCurrentValue(habitId: String, newValue: Int, isCompleted: Boolean, isLoading: Boolean) {
        val currentState = _uiState.value
        val updatedHabits = currentState.habitsWithCompletion.map { habitWithCompletion ->
            if (habitWithCompletion.habit.id == habitId) {
                habitWithCompletion.copy(
                    currentValue = newValue,
                    isCompletedToday = isCompleted,
                    isLoading = isLoading
                )
            } else {
                habitWithCompletion
            }
        }
        
        // Применяем сортировку только если не в состоянии загрузки (финальное обновление)
        val sortedHabits = if (!isLoading) {
            applySortingIfNeeded(updatedHabits)
        } else {
            updatedHabits
        }
        
        _uiState.value = currentState.copy(habitsWithCompletion = sortedHabits)
    }
    
    /**
     * Применяет сортировку списка привычек согласно настройкам пользователя
     */
    private fun applySortingIfNeeded(habits: List<HabitWithCompletion>): List<HabitWithCompletion> {
        val preferences = cachedUserPreferences
        return if (preferences?.sortCompletedHabitsToBottom == true) {
            habits.sortedWith(
                compareBy<HabitWithCompletion> { it.isCompletedToday }
                    .thenBy { it.habit.id }
            )
        } else {
            habits
        }
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
                        // Track group habit joined event
                        analyticsManager.logEvent(
                            AnalyticsEvents.GROUP_HABIT_JOINED,
                            mapOf(AnalyticsEvents.Params.HABIT_ID to habit.id)
                        )
                        
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
