package com.habittracker.presentation.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.User
import com.habittracker.presentation.viewmodel.AddEditHabitUiState
import com.habittracker.presentation.viewmodel.IAddEditHabitViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject

/**
 * Тестовая реализация ViewModel для превью.
 * Реализует IAddEditHabitViewModel для работы в превью.
 */
class PreviewAddEditHabitViewModel @Inject constructor(
    private val testState: AddEditHabitUiState = AddEditHabitUiState()
) : ViewModel(), IAddEditHabitViewModel {
    private val _uiState = MutableStateFlow(testState)
    override val uiState: StateFlow<AddEditHabitUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isLoading = false) }
    }

    override fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    override fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    override fun onIconChange(icon: String) {
        _uiState.update { it.copy(icon = icon) }
    }

    override fun onColorChange(color: Int) {
        _uiState.update { it.copy(color = color) }
    }

    override fun onTypeChange(type: HabitType) {
        _uiState.update { it.copy(type = type) }
    }

    override fun onFrequencyChange(frequency: HabitFrequency) {
        _uiState.update { it.copy(frequency = frequency) }
    }

    override fun onReminderTimeChange(time: LocalTime?) {
        _uiState.update { it.copy(reminderTime = time) }
    }

    override fun onReminderEnabledChange(enabled: Boolean) {
        // Не используется в новом интерфейсе
    }

    override fun onTargetValueChange(value: Int) {
        _uiState.update { it.copy(targetValue = value) }
    }

    override fun onUnitChange(unit: String) {
        _uiState.update { it.copy(unit = unit) }
    }

    override fun onMaxParticipantsChange(maxParticipants: Int) {
        _uiState.update { it.copy(maxParticipants = maxParticipants) }
    }

    override fun onIsPublicChange(isPublic: Boolean) {
        _uiState.update { it.copy(isPublic = isPublic) }
    }

    override fun onShowFriendSelector(show: Boolean) {
        _uiState.update { it.copy(showFriendSelector = show) }
    }

    override fun onErrorShown() {}

    override fun onSuccessMessageShown() {}

    override fun saveHabit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(1000) // Имитация сохранения
            _uiState.update { it.copy(isLoading = false, isHabitSaved = true) }
        }
    }

    override fun loadHabit(habitId: String) {
        // Для превью не загружаем реальные данные
    }

    // Методы для напоминаний
    override fun scheduleReminder() {
        // Для превью не реализовано
    }

    override fun cancelReminder() {
        // Для превью не реализовано
    }

    override fun updateReminder() {
        // Для превью не реализовано
    }

    // Методы для групповых привычек
    override fun generateInviteCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(500) // Имитация генерации
            val code = "ABC${(100..999).random()}"
            _uiState.update { 
                it.copy(
                    inviteCode = code,
                    isLoading = false,
                    successMessage = "Код приглашения создан"
                )
            }
        }
    }
    
    override fun disableInviteCode() {
        _uiState.update { 
            it.copy(
                inviteCode = null,
                successMessage = "Код приглашения отключен"
            )
        }
    }
    
    override fun shareInviteCode(inviteCode: String) {
        _uiState.update { 
            it.copy(successMessage = "Код скопирован в буфер обмена")
        }
    }

    override fun loadAvailableFriends() {
        // Для превью создаем тестовых друзей
        val testFriends = listOf(
            User(
                id = "1",
                name = "Анна",
                email = "anna@test.com",
                createdAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds),
                lastActiveAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
            ),
            User(
                id = "2",
                name = "Борис",
                email = "boris@test.com",
                createdAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds),
                lastActiveAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
            ),
            User(
                id = "3",
                name = "Виктор",
                email = "viktor@test.com",
                createdAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds),
                lastActiveAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
            )
        )
        _uiState.update { it.copy(availableFriends = testFriends) }
    }

    override fun selectFriend(friend: User) {
        _uiState.update { state ->
            val currentSelected = state.selectedFriendsToInvite
            if (!currentSelected.contains(friend)) {
                state.copy(selectedFriendsToInvite = currentSelected + friend)
            } else {
                state
            }
        }
    }

    override fun deselectFriend(friend: User) {
        _uiState.update { state ->
            state.copy(selectedFriendsToInvite = state.selectedFriendsToInvite - friend)
        }
    }

    override fun sendInvitations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(500) // Имитация отправки
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    showFriendSelector = false,
                    selectedFriendsToInvite = emptyList()
                )
            }
        }
    }
}

/**
 * Создает тестовый экземпляр ViewModel с заполненными данными для превью.
 */
@Suppress("FunctionName")
fun PreviewAddEditHabitViewModel(
    title: String = "Бег по утрам",
    description: String = "Пробежка в парке",
    icon: String = "🏃",
    type: HabitType = HabitType.PERSONAL,
    frequency: HabitFrequency = HabitFrequency.DAILY,
    reminderTime: LocalTime? = LocalTime(8, 30),
    targetValue: Int = 5,
    unit: String = "км",
    isLoading: Boolean = false,
    isHabitSaved: Boolean = false
): PreviewAddEditHabitViewModel {
    val testState = AddEditHabitUiState(
        title = title,
        description = description,
        icon = icon,
        type = type,
        frequency = frequency,
        reminderTime = reminderTime,
        targetValue = targetValue,
        unit = unit,
        isLoading = isLoading,
        isHabitSaved = isHabitSaved
    )
    return PreviewAddEditHabitViewModel(testState)
}
