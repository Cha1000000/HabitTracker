package com.habittracker.presentation.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitValidation
import com.habittracker.domain.model.HabitValidation.HabitField
import com.habittracker.domain.model.User
import com.habittracker.presentation.state.AddEditHabitUiState
import com.habittracker.presentation.utils.GroupHabitConstants
import com.habittracker.presentation.utils.GroupHabitValidationResult
import com.habittracker.presentation.viewmodel.IAddEditHabitViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Тестовая реализация ViewModel для превью.
 * Реализует IAddEditHabitViewModel для работы в превью с поддержкой валидации.
 */
class PreviewAddEditHabitViewModel @Inject constructor(
    testState: AddEditHabitUiState = AddEditHabitUiState()
) : ViewModel(), IAddEditHabitViewModel {
    private val _uiState = MutableStateFlow(testState)
    override val uiState: StateFlow<AddEditHabitUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isLoading = false) }
    }

    override fun onTargetValueInputChange(text: String) {
        // Разрешаем только цифры, максимум 3 символа (<=100)
        val filtered = text.filter { it.isDigit() }.take(3)
        _uiState.update { it.copy(targetValueInput = filtered, hasUnsavedChanges = true) }

        val value = filtered.toIntOrNull()
        if (value != null) {
            onTargetValueChange(value)
        } else {
            updateValidationState()
        }
    }

    override fun onTitleChange(title: String) {
        // Создаем временную привычку для валидации
        val tempHabit = createTempHabit().copy(title = title)
        val validationResult = HabitValidation.validateHabit(tempHabit)
        val titleError = validationResult.getError(HabitField.TITLE)?.message
        
        _uiState.update { 
            it.copy(
                title = title,
                titleError = titleError,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun onDescriptionChange(description: String) {
        // Создаем временную привычку для валидации
        val tempHabit = createTempHabit().copy(description = description)
        val validationResult = HabitValidation.validateHabit(tempHabit)
        val descriptionError = validationResult.getError(HabitField.DESCRIPTION)?.message
        
        _uiState.update { 
            it.copy(
                description = description,
                descriptionError = descriptionError,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun onIconChange(icon: String) {
        _uiState.update { it.copy(icon = icon, hasUnsavedChanges = true) }
        updateValidationState()
    }

    override fun onColorChange(color: Int) {
        _uiState.update { it.copy(color = color, hasUnsavedChanges = true) }
        updateValidationState()
    }

    override fun onTypeChange(type: HabitType) {
        _uiState.update { it.copy(type = type, hasUnsavedChanges = true) }
        updateValidationState()
    }

    override fun onFrequencyChange(frequency: HabitFrequency) {
        _uiState.update { state ->
            val presetDays: Set<DayOfWeek> = when (frequency) {
                HabitFrequency.DAILY -> emptySet()
                HabitFrequency.WEEKLY -> state.reminderDaysInput.takeIf { it.size == 1 } ?: emptySet()
                HabitFrequency.TWICE_A_WEEK -> state.reminderDaysInput.takeIf { it.size <= 2 } ?: emptySet()
                HabitFrequency.WEEKDAYS -> setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
                )
                HabitFrequency.WEEKENDS -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                HabitFrequency.CUSTOM -> state.reminderDaysInput
            }
            state.copy(frequency = frequency, reminderDaysInput = presetDays, hasUnsavedChanges = true)
        }
        updateValidationState()
    }

    override fun onReminderTimeChange(time: LocalTime?) {
        _uiState.update { it.copy(reminderTime = time, hasUnsavedChanges = true) }
        updateValidationState()
    }

    override fun onReminderEnabledChange(enabled: Boolean) {
        // Не используется в новом интерфейсе
    }

    override fun applyReminderToAllParticipants() {
        // no-op in preview
    }

    override fun onReminderDayToggle(day: DayOfWeek) {
        _uiState.update { state ->
            val current = state.reminderDaysInput.toMutableSet()
            val toggled = if (current.contains(day)) {
                current.remove(day); current
            } else {
                when (state.frequency) {
                    HabitFrequency.WEEKLY -> setOf(day)
                    HabitFrequency.TWICE_A_WEEK -> if (current.size >= 2) current else current.apply { add(day) }
                    HabitFrequency.WEEKDAYS -> setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                    HabitFrequency.WEEKENDS -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                    HabitFrequency.CUSTOM, HabitFrequency.DAILY -> current.apply { add(day) }
                }
            }
            state.copy(reminderDaysInput = toggled, hasUnsavedChanges = true)
        }
    }

    override fun onTargetValueChange(value: Int) {
        // Создаем временную привычку для валидации
        val tempHabit = createTempHabit().copy(targetValue = value)
        val validationResult = HabitValidation.validateHabit(tempHabit)
        val targetValueError = validationResult.getError(HabitField.TARGET_VALUE)?.message
        
        _uiState.update { 
            it.copy(
                targetValue = value,
                targetValueError = targetValueError,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun onUnitChange(unit: String) {
        // Создаем временную привычку для валидации
        val tempHabit = createTempHabit().copy(unit = unit)
        val validationResult = HabitValidation.validateHabit(tempHabit)
        val unitError = validationResult.getError(HabitField.UNIT)?.message
        
        _uiState.update { 
            it.copy(
                unit = unit,
                targetValueError = unitError, // Ошибка единицы показывается в поле targetValue
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun onMaxParticipantsInputChange(text: String) {
        // Всегда сохраняем введенный текст
        _uiState.update { it.copy(maxParticipantsInput = text, hasUnsavedChanges = true) }

        val value = text.toIntOrNull()
        if (value != null && value in GroupHabitConstants.MIN_PARTICIPANTS..GroupHabitConstants.MAX_PARTICIPANTS) {
            onMaxParticipantsChange(value)
        } else {
            updateValidationState()
        }
    }

    override fun onMaxParticipantsChange(maxParticipants: Int) {
        // Создаем временную привычку для валидации
        val tempHabit = createTempHabit().copy(participants = (1..maxParticipants).map { "user$it" })
        val validationResult = HabitValidation.validateHabit(tempHabit)
        val participantsError = validationResult.getError(HabitField.PARTICIPANTS)?.message
        
        _uiState.update { 
            it.copy(
                maxParticipants = maxParticipants,
                maxParticipantsInput = maxParticipants.toString(),
                participantsError = participantsError,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun onIsPublicChange(isPublic: Boolean) {
        _uiState.update { it.copy(isPublic = isPublic, hasUnsavedChanges = true) }
        updateValidationState()
    }

    override fun onShowFriendSelector(show: Boolean) {
        _uiState.update { it.copy(showFriendSelector = show) }
    }

    override fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onSuccessMessageShown() {
        _uiState.update { it.copy(successMessage = null) }
    }

    override fun saveHabit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(1000) // Имитация сохранения
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    isHabitSaved = true,
                    successMessage = "Привычка сохранена (превью)",
                    hasUnsavedChanges = false
                ) 
            }

            // Имитация автосенд после сохранения при активном флаге
            val state = uiState.value
            if (state.sendInvitesAfterSave && state.selectedFriendsToInvite.isNotEmpty()) {
                _uiState.update { it.copy(sendInvitesAfterSave = false) }
                sendInvitations()
            }
        }
    }

    override fun loadHabit(habitId: String) {
        // Для превью не загружаем реальные данные
        _uiState.update { it.copy(isLoading = false) }
    }

    // Методы для напоминаний (заглушки для превью)
    override fun scheduleReminder() {}
    override fun cancelReminder() {}
    override fun updateReminder() {}

    // Методы для групповых привычек (заглушки для превью)
    override fun generateInviteCode() {
        _uiState.update { 
            it.copy(
                inviteCode = "PREVIEW123",
                successMessage = "Код приглашения создан (превью)"
            ) 
        }
    }

    override fun disableInviteCode() {
        _uiState.update { 
            it.copy(
                inviteCode = null,
                successMessage = "Код приглашения отключен (превью)"
            ) 
        }
    }

    override fun shareInviteCode(inviteCode: String) {
        // В превью просто показываем сообщение
        _uiState.update { it.copy(successMessage = "Код '$inviteCode' скопирован (превью)") }
    }

    override fun loadAvailableFriends() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val friends = listOf(
            User(id = "friend1", name = "Alice", email = "alice@example.com", avatarUrl = "", level = 1, points = 100, createdAt = today, lastActiveAt = today),
            User(id = "friend2", name = "Bob", email = "bob@example.com", avatarUrl = "", level = 2, points = 250, createdAt = today, lastActiveAt = today),
            User(id = "friend3", name = "Charlie", email = "charlie@example.com", avatarUrl = "", level = 3, points = 500, createdAt = today, lastActiveAt = today)
        )
        _uiState.update { it.copy(availableFriends = friends) }
    }

    override fun sendInvitations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(500)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    successMessage = "Приглашения отправлены (превью)",
                    selectedFriendsToInvite = emptyList(),
                    showFriendSelector = false
                ) 
            }
        }
    }

    override fun requestInvitations() {
        val state = _uiState.value
        val canSendNow = state.habitId != null && state.selectedFriendsToInvite.isNotEmpty()
        if (canSendNow) {
            sendInvitations()
        } else {
            _uiState.update { it.copy(sendInvitesAfterSave = true, successMessage = "Сохраните привычку, чтобы отправить приглашения (превью)") }
        }
    }

    override fun selectFriend(friend: User) {
        _uiState.update { 
            val updatedSelection = it.selectedFriendsToInvite + friend
            it.copy(selectedFriendsToInvite = updatedSelection)
        }
    }

    override fun deselectFriend(friend: User) {
        _uiState.update { 
            val updatedSelection = it.selectedFriendsToInvite - friend
            it.copy(selectedFriendsToInvite = updatedSelection)
        }
    }

    /**
     * Создает временную привычку из текущего состояния UI для валидации
     */
    private fun createTempHabit(): Habit {
        val currentState = _uiState.value
        return Habit(
            id = currentState.habitId ?: "preview_habit",
            title = currentState.title,
            description = currentState.description,
            icon = "⭐", // простое значение по умолчанию
            color = "#2196F3", // простое значение по умолчанию
            type = HabitType.PERSONAL, // простое значение по умолчанию
            frequency = currentState.frequency,
            reminderTime = currentState.reminderTime,
            reminderEnabled = false, // простое значение по умолчанию
            reminderDays = currentState.reminderDaysInput,
            targetValue = currentState.targetValue,
            unit = currentState.unit,
            createdBy = "preview_user",
            participants = emptyList(), // простое значение по умолчанию
            maxParticipants = GroupHabitConstants.DEFAULT_MAX_PARTICIPANTS,
            inviteCode = null, // простое значение по умолчанию
            isPublic = false, // простое значение по умолчанию
            isActive = true,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
    }

    /**
     * Централизованная валидация всей формы привычки
     */
    private fun validateHabitForm(): GroupHabitValidationResult {
        val tempHabit = createTempHabit()
        val validationResult = HabitValidation.validateHabit(tempHabit)
        
        // Преобразуем HabitValidationResult в GroupHabitValidationResult для совместимости с UI
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        validationResult.errors.forEach { error ->
            errors.add(error.message)
        }
        
        return GroupHabitValidationResult(
            isValid = validationResult.isValid,
            errors = errors,
            warnings = warnings,
            canProceed = validationResult.isValid
        )
    }

    /**
     * Обновляет состояние валидации в UI
     */
    private fun updateValidationState() {
        val validationResult = validateHabitForm()
        
        _uiState.update { currentState ->
            currentState.copy(
                groupHabitValidationResult = validationResult,
                isFormValid = validationResult.isValid,
                canSaveHabit = validationResult.isValid,
                showValidationErrors = !validationResult.isValid
            )
        }
    }
    
    // Методы для работы с датами привычек
    override fun onStartDateChange(date: LocalDate?) {
        _uiState.update { it.copy(
            startDate = date,
            hasUnsavedChanges = true
        )}
    }
    
    override fun onEndDateChange(date: LocalDate?) {
        _uiState.update { it.copy(
            endDate = date,
            hasUnsavedChanges = true
        )}
    }
}