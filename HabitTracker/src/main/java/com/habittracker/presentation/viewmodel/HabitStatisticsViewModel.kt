package com.habittracker.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.GroupHabitStatisticsData
import com.habittracker.domain.model.HabitStatisticsData
import com.habittracker.domain.model.StatisticsPeriod
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.habit.GetHabitStatisticsUseCase
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import javax.inject.Inject

/**
 * UI State для экрана статистики личной привычки
 */
data class SelfHabitStatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val statistics: HabitStatisticsData? = null,
    val selectedPeriod: StatisticsPeriod = StatisticsPeriod.WEEK,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null
)

/**
 * UI State для экрана статистики групповой привычки
 */
data class GroupHabitStatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val statistics: GroupHabitStatisticsData? = null,
    val selectedPeriod: StatisticsPeriod = StatisticsPeriod.WEEK,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val showParticipantsComparison: Boolean = false,
    val selectedParticipantIds: Set<String> = emptySet(), // Пустой = все участники
    val participantsInfo: List<User> = emptyList()
)

/**
 * ViewModel для экранов детальной статистики привычек.
 * Поддерживает как личные (SelfHabitStatScreen), так и групповые (GroupHabitStatScreen) привычки.
 */
@HiltViewModel
class HabitStatisticsViewModel @Inject constructor(
    private val getHabitStatisticsUseCase: GetHabitStatisticsUseCase,
    private val authRepository: AuthRepository,
    private val analyticsManager: AnalyticsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val habitId: String = savedStateHandle.get<String>("habitId") ?: ""
    private val isGroupHabit: Boolean = savedStateHandle.get<Boolean>("isGroup") ?: false

    private val today: LocalDate
        get() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // State для личной статистики
    private val _selfStatUiState = MutableStateFlow(SelfHabitStatUiState())
    val selfStatUiState: StateFlow<SelfHabitStatUiState> = _selfStatUiState.asStateFlow()

    // State для групповой статистики
    private val _groupStatUiState = MutableStateFlow(GroupHabitStatUiState())
    val groupStatUiState: StateFlow<GroupHabitStatUiState> = _groupStatUiState.asStateFlow()

    init {
        if (habitId.isNotEmpty()) {
            loadStatistics()
        }
    }

    /**
     * Загружает статистику в зависимости от типа привычки
     */
    fun loadStatistics() {
        if (isGroupHabit) {
            loadGroupStatistics()
        } else {
            loadSelfStatistics()
        }
    }

    /**
     * Загружает статистику личной привычки
     */
    private fun loadSelfStatistics() {
        viewModelScope.launch {
            _selfStatUiState.update { it.copy(isLoading = true, error = null) }

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _selfStatUiState.update { 
                    it.copy(isLoading = false, error = "Пользователь не авторизован") 
                }
                return@launch
            }

            val (startDate, endDate) = calculateDateRange(
                _selfStatUiState.value.selectedPeriod,
                _selfStatUiState.value.customStartDate,
                _selfStatUiState.value.customEndDate
            )

            try {
                val statistics = getHabitStatisticsUseCase.getPersonalStatistics(
                    habitId = habitId,
                    userId = userId,
                    startDate = startDate,
                    endDate = endDate
                )

                _selfStatUiState.update {
                    it.copy(
                        isLoading = false,
                        statistics = statistics,
                        error = if (statistics == null) "Не удалось загрузить статистику" else null
                    )
                }
            } catch (e: Exception) {
                _selfStatUiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    /**
     * Загружает статистику групповой привычки
     */
    private fun loadGroupStatistics() {
        viewModelScope.launch {
            _groupStatUiState.update { it.copy(isLoading = true, error = null) }

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _groupStatUiState.update { 
                    it.copy(isLoading = false, error = "Пользователь не авторизован") 
                }
                return@launch
            }

            val (startDate, endDate) = calculateDateRange(
                _groupStatUiState.value.selectedPeriod,
                _groupStatUiState.value.customStartDate,
                _groupStatUiState.value.customEndDate
            )

            try {
                val statistics = getHabitStatisticsUseCase.getGroupStatistics(
                    habitId = habitId,
                    currentUserId = userId,
                    startDate = startDate,
                    endDate = endDate
                )

                val participantsInfo = statistics?.participantsInfo?.filter { it.id != userId } ?: emptyList()
                
                _groupStatUiState.update {
                    it.copy(
                        isLoading = false,
                        statistics = statistics,
                        participantsInfo = participantsInfo,
                        error = if (statistics == null) "Не удалось загрузить статистику" else null
                    )
                }
            } catch (e: Exception) {
                _groupStatUiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    /**
     * Изменяет выбранный период для личной статистики
     */
    fun onSelfPeriodChanged(period: StatisticsPeriod) {
        _selfStatUiState.update { it.copy(selectedPeriod = period) }
        loadSelfStatistics()
    }

    /**
     * Устанавливает произвольный диапазон дат для личной статистики
     */
    fun onSelfCustomDateRangeChanged(startDate: LocalDate, endDate: LocalDate) {
        _selfStatUiState.update { 
            it.copy(
                selectedPeriod = StatisticsPeriod.CUSTOM,
                customStartDate = startDate,
                customEndDate = endDate
            ) 
        }
        loadSelfStatistics()
    }

    /**
     * Изменяет выбранный период для групповой статистики
     */
    fun onGroupPeriodChanged(period: StatisticsPeriod) {
        _groupStatUiState.update { it.copy(selectedPeriod = period) }
        loadGroupStatistics()
    }

    /**
     * Устанавливает произвольный диапазон дат для групповой статистики
     */
    fun onGroupCustomDateRangeChanged(startDate: LocalDate, endDate: LocalDate) {
        _groupStatUiState.update { 
            it.copy(
                selectedPeriod = StatisticsPeriod.CUSTOM,
                customStartDate = startDate,
                customEndDate = endDate
            ) 
        }
        loadGroupStatistics()
    }

    /**
     * Переключает режим сравнения с участниками
     */
    fun onToggleParticipantsComparison() {
        _groupStatUiState.update { 
            it.copy(showParticipantsComparison = !it.showParticipantsComparison) 
        }
    }

    /**
     * Переключает выбор участника для фильтрации
     */
    fun onToggleParticipantSelection(participantId: String) {
        _groupStatUiState.update { state ->
            val allIds = state.participantsInfo.map { it.id }.toSet()
            // Если сейчас пусто (выбраны все) - считаем, что выбраны все доступные ID
            val currentSelection = state.selectedParticipantIds.ifEmpty { allIds }

            val newSelection = if (currentSelection.contains(participantId)) {
                // Если участник уже выбран - убираем его
                currentSelection - participantId
            } else {
                // Если не выбран - добавляем
                currentSelection + participantId
            }

            // Если в итоге выбраны все участники - сбрасываем в пустой список (оптимизация и состояние "Все")
            // Если список стал пустым (пользователь отключил последнего) - тоже будет emptySet, что приведет к показу всех (авто-сброс)
            val finalSelection = if (newSelection.containsAll(allIds)) emptySet() else newSelection

            state.copy(selectedParticipantIds = finalSelection)
        }
    }

    /**
     * Сбрасывает фильтр участников (показать всех)
     */
    fun onClearParticipantFilter() {
        _groupStatUiState.update { it.copy(selectedParticipantIds = emptySet()) }
    }

    /**
     * Трекинг раскрытия календаря статистики участника
     */
    fun onParticipantCalendarExpanded(participantId: String) {
        analyticsManager.logEvent(
            AnalyticsEvents.PARTICIPANT_CALENDAR_EXPANDED,
            mapOf(
                AnalyticsEvents.Params.HABIT_ID to habitId,
                AnalyticsEvents.Params.PARTICIPANT_ID to participantId
            )
        )
    }

    private fun calculateDateRange(
        period: StatisticsPeriod,
        customStart: LocalDate?,
        customEnd: LocalDate?
    ): Pair<LocalDate, LocalDate> {
        return when (period) {
            StatisticsPeriod.WEEK -> {
                val endDate = today
                val startDate = today.minus(6, DateTimeUnit.DAY)
                startDate to endDate
            }
            StatisticsPeriod.MONTH -> {
                val endDate = today
                val startDate = today.minus(29, DateTimeUnit.DAY)
                startDate to endDate
            }
            StatisticsPeriod.CUSTOM -> {
                val startDate = customStart ?: today.minus(6, DateTimeUnit.DAY)
                val endDate = customEnd ?: today
                startDate to endDate
            }
        }
    }
}
