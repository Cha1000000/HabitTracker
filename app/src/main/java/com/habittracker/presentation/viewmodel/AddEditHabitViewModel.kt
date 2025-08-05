package com.habittracker.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.CancelHabitReminderUseCase
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.UpdateHabitReminderUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.habit.GenerateHabitInviteCodeUseCase
import com.habittracker.domain.usecase.habit.SendHabitInvitationUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.presentation.utils.GroupHabitValidationResult
import com.habittracker.presentation.utils.ValidationResult
import com.habittracker.presentation.utils.ValidationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val addHabitUseCase: AddHabitUseCase,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase,
    private val cancelHabitReminderUseCase: CancelHabitReminderUseCase,
    private val updateHabitReminderUseCase: UpdateHabitReminderUseCase,
    private val sendHabitInvitationUseCase: SendHabitInvitationUseCase,
    private val generateHabitInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
    application: Application
) : AndroidViewModel(application), IAddEditHabitViewModel {

    private val _uiState = MutableStateFlow(AddEditHabitUiState())
    override val uiState: StateFlow<AddEditHabitUiState> = _uiState.asStateFlow()

    private var currentHabit: Habit? = null

    init {
        val habitId: String? = savedStateHandle["habitId"]
        if (habitId != null) {
            loadHabit(habitId)
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    override fun onTitleChange(title: String) {
        val validationResult = ValidationUtils.validateHabitTitle(title)
        _uiState.update { 
            it.copy(
                title = title,
                titleError = if (validationResult is ValidationResult.Error) validationResult.message else null,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun onDescriptionChange(description: String) {
        val validationResult = ValidationUtils.validateHabitDescription(description)
        _uiState.update { 
            it.copy(
                description = description,
                descriptionError = if (validationResult is ValidationResult.Error) validationResult.message else null,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun loadHabit(habitId: String) {
        viewModelScope.launch {
            try {
                currentHabit = getHabitByIdUseCase(habitId)
                currentHabit?.let { habit ->
                    _uiState.update { state ->
                        state.copy(
                            habitId = habit.id,
                            title = habit.title,
                            description = habit.description,
                            icon = habit.icon,
                            color = habit.color.toInt(), // Конвертируем String в Int
                            type = habit.type,
                            frequency = habit.frequency,
                            reminderTime = habit.reminderTime,
                            targetValue = habit.targetValue,
                            unit = habit.unit,
                            maxParticipants = habit.maxParticipants,
                            inviteCode = habit.inviteCode,
                            isPublic = habit.isPublic,
                            participants = emptyList(), // Пока оставляем пустым, позже загрузим User объекты
                            isLoading = false
                        )
                    }
                } ?: run {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = getApplication<Application>().getString(R.string.error_loading_habit, e.localizedMessage ?: "")
                )}
            }
        }
    }

    override fun saveHabit() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val now = Clock.System.now()
                val currentState = uiState.value
                
                if (currentHabit == null) {
                    // Создаем новую привычку
                    val creatorId = authRepository.getCurrentUserId() 
                        ?: throw IllegalStateException("User must be authenticated to create habits")
                    val newHabit = Habit(
                        id = UUID.randomUUID().toString(),
                        title = currentState.title,
                        description = currentState.description,
                        icon = currentState.icon,
                        color = currentState.color.toString(), // Конвертируем Int в String для модели
                        type = currentState.type,
                        frequency = currentState.frequency,
                        reminderTime = currentState.reminderTime,
                        targetValue = currentState.targetValue,
                        unit = currentState.unit,
                        createdBy = creatorId,
                        participants = currentState.participants.map { it.id }, // Конвертируем User в String ID
                        maxParticipants = currentState.maxParticipants,
                        inviteCode = currentState.inviteCode,
                        isPublic = currentState.isPublic,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    )
                    addHabitUseCase(newHabit)
                    
                    // Планируем напоминание для новой привычки
                    if (newHabit.reminderTime != null) {
                        scheduleHabitReminderUseCase(newHabit)
                        Log.d("AddEditHabitViewModel", "Напоминание запланировано для новой привычки")
                    }
                } else {
                    // Обновляем существующую привычку
                    val updatedHabit = currentHabit!!.copy(
                        title = currentState.title,
                        description = currentState.description,
                        icon = currentState.icon,
                        color = currentState.color.toString(), // Конвертируем Int в String для модели
                        type = currentState.type,
                        frequency = currentState.frequency,
                        targetValue = currentState.targetValue,
                        unit = currentState.unit,
                        maxParticipants = currentState.maxParticipants,
                        inviteCode = currentState.inviteCode,
                        isPublic = currentState.isPublic,
                        participants = currentState.participants.map { it.id }, // Конвертируем User в String ID
                        updatedAt = now
                    )
                    updateHabitUseCase(updatedHabit)
                    
                    // Обновляем напоминание для существующей привычки
                    updateHabitReminderUseCase(updatedHabit)
                    Log.d("AddEditHabitViewModel", "Напоминание обновлено для существующей привычки")
                }
                
                _uiState.update { it.copy(
                    isHabitSaved = true, 
                    isLoading = false,
                    successMessage = if (currentHabit == null) getApplication<Application>().getString(R.string.habit_created) else getApplication<Application>().getString(R.string.habit_updated)
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = getApplication<Application>().getString(R.string.error_saving_habit, e.localizedMessage ?: "")
                )}
            }
        }
    }
    
    // Методы для обновления состояния
    override fun onIconChange(icon: String) {
        _uiState.update { it.copy(icon = icon) }
    }
    
    override fun onColorChange(color: Int) {
        _uiState.value = _uiState.value.copy(color = color)
    }
    
    override fun onTypeChange(type: HabitType) {
        _uiState.update { it.copy(type = type) }
    }
    
    override fun onFrequencyChange(frequency: HabitFrequency) {
        _uiState.update { it.copy(frequency = frequency) }
    }
    
    override fun onTargetValueChange(value: Int) {
        _uiState.update { it.copy(targetValue = value) }
    }
    
    override fun onUnitChange(unit: String) {
        _uiState.update { it.copy(unit = unit) }
    }
    
    override fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onSuccessMessageShown() {
        _uiState.update { it.copy(successMessage = null) }
    }
    
    // Методы для работы с напоминаниями
    override fun scheduleReminder() {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                if (currentState.reminderTime != null) {
                    // Создаем временную привычку для планирования напоминания
                    val tempHabit = currentHabit?.copy(
                        reminderTime = currentState.reminderTime
                    ) ?: return@launch
                    
                    scheduleHabitReminderUseCase(tempHabit)
                    Log.d("AddEditHabitViewModel", "Напоминание запланировано")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.error_scheduling_reminder, e.localizedMessage ?: "")) }
            }
        }
    }
    
    override fun cancelReminder() {
        viewModelScope.launch {
            try {
                currentHabit?.let { habit ->
                    cancelHabitReminderUseCase(habit.id)
                    Log.d("AddEditHabitViewModel", "Напоминание отменено")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.error_canceling_reminder, e.localizedMessage ?: "")) }
            }
        }
    }
    
    override fun updateReminder() {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                val tempHabit = currentHabit?.copy(
                    reminderTime = currentState.reminderTime
                ) ?: return@launch
                
                updateHabitReminderUseCase(tempHabit)
                Log.d("AddEditHabitViewModel", "Напоминание обновлено")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.error_updating_reminder, e.localizedMessage ?: "")) }
            }
        }
    }
    
    override fun onReminderTimeChange(time: LocalTime?) {
        _uiState.update { it.copy(reminderTime = time) }
    }
    
    override fun onReminderEnabledChange(enabled: Boolean) {
        // Метод оставлен для совместимости, но не используется в новой архитектуре
    }
    
    override fun generateInviteCode() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                
                // Проверяем, можно ли генерировать код
                if (currentState.inviteCodeGenerationAttempts >= 3) {
                    _uiState.update { 
                        it.copy(
                            inviteCodeError = "Превышен лимит попыток генерации кода. Попробуйте позже.",
                            error = "Слишком много попыток генерации кода"
                        )
                    }
                    return@launch
                }
                
                _uiState.update { 
                    it.copy(
                        isGeneratingInviteCode = true,
                        inviteCodeError = null,
                        error = null
                    )
                }
                
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                val habitId = currentHabit?.id ?: throw Exception("Привычка не найдена")
                
                val result = generateHabitInviteCodeUseCase.invoke(habitId, currentUserId)
                result.onSuccess { inviteCode: String ->
                    _uiState.update { 
                        it.copy(
                            inviteCode = inviteCode,
                            isGeneratingInviteCode = false,
                            successMessage = "Код приглашения создан успешно",
                            inviteCodeGenerationAttempts = it.inviteCodeGenerationAttempts + 1,
                            hasUnsavedChanges = true
                        )
                    }
                }.onFailure { error: Throwable ->
                    _uiState.update { 
                        it.copy(
                            isGeneratingInviteCode = false,
                            inviteCodeError = "Не удалось создать код: ${error.message}",
                            error = "Ошибка генерации кода приглашения",
                            inviteCodeGenerationAttempts = it.inviteCodeGenerationAttempts + 1
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isGeneratingInviteCode = false,
                        inviteCodeError = "Ошибка создания кода: ${e.message}",
                        error = "Неожиданная ошибка при генерации кода",
                        inviteCodeGenerationAttempts = it.inviteCodeGenerationAttempts + 1
                    )
                }
            }
        }
    }
    
    override fun disableInviteCode() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                val habitId = currentHabit?.id ?: throw Exception("Привычка не найдена")
                
                val result = generateHabitInviteCodeUseCase.disableInviteCode(habitId, currentUserId)
                result.onSuccess {
                    _uiState.update { 
                        it.copy(
                            inviteCode = null,
                            isLoading = false,
                            successMessage = "Код приглашения отключен"
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Не удалось отключить код: ${error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Ошибка отключения кода: ${e.message}"
                    )
                }
            }
        }
    }
    
    override fun shareInviteCode(inviteCode: String) {
        // Этот метод будет вызываться из UI для шаринга кода
        // Логика шаринга будет в UI компоненте
        _uiState.update { 
            it.copy(successMessage = "Код скопирован в буфер обмена")
        }
    }

    override fun loadAvailableFriends() {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isLoadingFriends = true,
                        error = null
                    )
                }
                
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                getFriendsUseCase(currentUserId).collect { friendships ->
                    val friends = friendships.map { friendshipWithUsers ->
                        // Получаем друга (не текущего пользователя)
                        if (friendshipWithUsers.friendship.userId1 == currentUserId) {
                            friendshipWithUsers.user2
                        } else {
                            friendshipWithUsers.user1
                        }
                    }
                    val currentParticipants = _uiState.value.participants
                    
                    // Фильтруем друзей, которые уже участвуют в привычке
                    val availableFriends = friends.filter { friend ->
                        !currentParticipants.any { participant -> participant.id == friend.id }
                    }
                    
                    _uiState.update { 
                        it.copy(
                            availableFriends = availableFriends,
                            isLoadingFriends = false,
                            error = if (availableFriends.isEmpty() && friends.isNotEmpty()) {
                                "Все ваши друзья уже участвуют в этой привычке"
                            } else null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingFriends = false,
                        availableFriends = emptyList(),
                        error = "Не удалось загрузить список друзей: ${e.message}"
                    )
                }
            }
        }
    }
    
    override fun onIsPublicChange(isPublic: Boolean) {
        _uiState.update { it.copy(isPublic = isPublic) }
    }

    override fun onShowFriendSelector(show: Boolean) {
        _uiState.update { it.copy(showFriendSelector = show) }
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
            try {
                val currentState = _uiState.value
                
                // Валидация перед отправкой
                val friendsValidation = ValidationUtils.validateInvitedFriends(
                    currentState.selectedFriendsToInvite,
                    currentState.maxParticipants,
                    currentState.participants
                )
                
                if (friendsValidation is ValidationResult.Error) {
                    _uiState.update { 
                        it.copy(
                            participantsError = friendsValidation.message,
                            error = "Невозможно отправить приглашения"
                        )
                    }
                    return@launch
                }
                
                // Проверка на спам (не более 1 раза в минуту)
                val lastInvitation = currentState.lastInvitationSentAt
                val currentTime = System.currentTimeMillis()
                if (lastInvitation != null && (currentTime - lastInvitation) < 60_000) {
                    _uiState.update { 
                        it.copy(
                            error = "Подождите минуту перед отправкой новых приглашений"
                        )
                    }
                    return@launch
                }
                
                _uiState.update { 
                    it.copy(
                        isSendingInvitations = true,
                        participantsError = null,
                        error = null
                    )
                }
                
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                val habitId = currentHabit?.id ?: throw Exception("Привычка не найдена")
                
                // Отправляем приглашения по одному для лучшего контроля
                var successCount = 0
                var errorCount = 0
                val errors = mutableListOf<String>()
                
                for (friend in currentState.selectedFriendsToInvite) {
                    try {
                        val result = sendHabitInvitationUseCase(habitId, currentUserId, friend.id)
                        result.onSuccess {
                            successCount++
                        }.onFailure { error: Throwable ->
                            errorCount++
                            errors.add("${friend.name}: ${error.message}")
                        }
                    } catch (e: Exception) {
                        errorCount++
                        errors.add("${friend.name}: ${e.message}")
                    }
                }
                
                // Обновляем состояние в зависимости от результатов
                val message = when {
                    successCount > 0 && errorCount == 0 -> 
                        "Приглашения отправлены ($successCount)"
                    successCount > 0 && errorCount > 0 -> 
                        "Отправлено: $successCount, ошибок: $errorCount"
                    else -> "Не удалось отправить приглашения"
                }
                
                _uiState.update { 
                    it.copy(
                        isSendingInvitations = false,
                        successMessage = if (successCount > 0) message else null,
                        error = if (errorCount > 0) errors.joinToString("; ") else null,
                        selectedFriendsToInvite = if (successCount > 0) emptyList() else it.selectedFriendsToInvite,
                        lastInvitationSentAt = currentTime,
                        hasUnsavedChanges = successCount > 0
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSendingInvitations = false,
                        error = "Ошибка отправки приглашений: ${e.message}"
                    )
                }
            }
        }
    }
    
    // Групповые привычки
    override fun onMaxParticipantsChange(maxParticipants: Int) {
        val validationResult = ValidationUtils.validateMaxParticipants(maxParticipants, _uiState.value.type)
        _uiState.update { 
            it.copy(
                maxParticipants = maxParticipants,
                participantsError = if (validationResult is ValidationResult.Error) validationResult.message else null,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }
    
    /**
     * Комплексная валидация формы привычки
     */
    private fun validateHabitForm(): GroupHabitValidationResult {
        val currentState = _uiState.value
        
        return if (currentState.type == HabitType.GROUP) {
            // Получаем количество текущих групповых привычек пользователя
            val currentGroupHabitsCount = 0 // TODO: Получить реальное количество из репозитория
            
            ValidationUtils.validateGroupHabitForm(
                title = currentState.title,
                description = currentState.description,
                maxParticipants = currentState.maxParticipants,
                selectedFriends = currentState.selectedFriendsToInvite,
                targetValue = currentState.targetValue,
                unit = currentState.unit,
                currentGroupHabitsCount = currentGroupHabitsCount
            )
        } else {
            // Для личных привычек используем упрощенную валидацию
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            // Валидация названия
            when (val titleResult = ValidationUtils.validateHabitTitle(currentState.title)) {
                is ValidationResult.Error -> errors.add(titleResult.message)
                is ValidationResult.Warning -> warnings.add(titleResult.message)
                else -> {}
            }
            
            // Валидация описания
            when (val descResult = ValidationUtils.validateHabitDescription(currentState.description)) {
                is ValidationResult.Error -> errors.add(descResult.message)
                is ValidationResult.Warning -> warnings.add(descResult.message)
                else -> {}
            }
            
            // Валидация целевого значения
            when (val targetResult = ValidationUtils.validateTargetValue(currentState.targetValue, currentState.unit)) {
                is ValidationResult.Error -> errors.add(targetResult.message)
                is ValidationResult.Warning -> warnings.add(targetResult.message)
                else -> {}
            }
            
            // Валидация времени напоминания
            currentState.reminderTime?.let { time ->
                when (val timeResult = ValidationUtils.validateReminderTime(time.hour, time.minute)) {
                    is ValidationResult.Error -> errors.add(timeResult.message)
                    is ValidationResult.Warning -> warnings.add(timeResult.message)
                    else -> {}
                }
            }
            
            GroupHabitValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                canProceed = errors.isEmpty()
            )
        }
    }
    
    /**
     * Обновляет состояние валидации в реальном времени
     */
    private fun updateValidationState() {
        val validationResult = validateHabitForm()
        _uiState.update { state ->
            state.copy(
                isFormValid = validationResult.isValid,
                canSaveHabit = validationResult.canProceed && !state.isLoading,
                groupHabitValidationResult = validationResult,
                titleError = validationResult.errors.find { it.contains("название", ignoreCase = true) },
                descriptionError = validationResult.errors.find { it.contains("описание", ignoreCase = true) },
                targetValueError = validationResult.errors.find { 
                    it.contains("целевое значение", ignoreCase = true) || it.contains("единица", ignoreCase = true) 
                },
                participantsError = validationResult.errors.find { 
                    it.contains("участник", ignoreCase = true) || it.contains("лимит", ignoreCase = true) 
                }
            )
        }
    }
}
