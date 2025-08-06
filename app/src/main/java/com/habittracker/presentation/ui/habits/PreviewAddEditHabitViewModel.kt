package com.habittracker.presentation.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitValidation
import com.habittracker.domain.model.HabitValidation.HabitField
import com.habittracker.domain.model.User
import com.habittracker.presentation.utils.GroupHabitValidationResult
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
 * Реализует IAddEditHabitViewModel для работы в превью с поддержкой валидации.
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
        _uiState.update { it.copy(frequency = frequency, hasUnsavedChanges = true) }
        updateValidationState()
    }

    override fun onReminderTimeChange(time: LocalTime?) {
        _uiState.update { it.copy(reminderTime = time, hasUnsavedChanges = true) }
        updateValidationState()
    }

    override fun onReminderEnabledChange(enabled: Boolean) {
        // Не используется в новом интерфейсе
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

    override fun onMaxParticipantsChange(maxParticipants: Int) {
        // Создаем временную привычку для валидации
        val tempHabit = createTempHabit().copy(participants = (1..maxParticipants).map { "user$it" })
        val validationResult = HabitValidation.validateHabit(tempHabit)
        val participantsError = validationResult.getError(HabitField.PARTICIPANTS)?.message
        
        _uiState.update { 
            it.copy(
                maxParticipants = maxParticipants,
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
        // Заглушка для превью
    }

    override fun loadAvailableFriends() {
        // Заглушка с тестовыми данными
        val currentTime = Clock.System.now()
        val testFriends = listOf(
            User(
                id = "1", 
                name = "Анна Петрова", 
                email = "anna@example.com",
                createdAt = currentTime,
                lastActiveAt = currentTime
            ),
            User(
                id = "2", 
                name = "Иван Сидоров", 
                email = "ivan@example.com",
                createdAt = currentTime,
                lastActiveAt = currentTime
            ),
            User(
                id = "3", 
                name = "Мария Козлова", 
                email = "maria@example.com",
                createdAt = currentTime,
                lastActiveAt = currentTime
            )
        )
        _uiState.update { 
            it.copy(
                availableFriends = testFriends,
                isLoadingFriends = false
            ) 
        }
    }

    override fun selectFriend(friend: User) {
        _uiState.update { state ->
            val updatedSelected = state.selectedFriendsToInvite + friend
            state.copy(selectedFriendsToInvite = updatedSelected)
        }
    }

    override fun deselectFriend(friend: User) {
        _uiState.update { state ->
            val updatedSelected = state.selectedFriendsToInvite.filter { it.id != friend.id }
            state.copy(selectedFriendsToInvite = updatedSelected)
        }
    }

    override fun sendInvitations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingInvitations = true) }
            delay(1000) // Имитация отправки
            _uiState.update { 
                it.copy(
                    isSendingInvitations = false,
                    selectedFriendsToInvite = emptyList(),
                    successMessage = "Приглашения отправлены (превью)"
                ) 
            }
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
            icon = currentState.icon.ifEmpty { "⭐" },
            color = String.format("#%06X", (0xFFFFFF and currentState.color)),
            type = currentState.type,
            frequency = currentState.frequency,
            reminderTime = currentState.reminderTime,
            targetValue = currentState.targetValue,
            unit = currentState.unit,
            participants = currentState.participants.map { it.id },
            createdBy = "preview_user",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            inviteCode = currentState.inviteCode
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
