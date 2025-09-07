package com.habittracker.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.core.graphics.toColorInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitValidation
import com.habittracker.domain.model.HabitValidation.HabitField
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
import com.habittracker.presentation.utils.GroupHabitConstants
import com.habittracker.presentation.utils.GroupHabitValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val addHabitUseCase: AddHabitUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
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
    private var friendsJob: Job? = null

    init {
        val habitId: String? = savedStateHandle["habitId"]
        if (habitId != null) {
            loadHabit(habitId)
        } else {
            _uiState.update { it.copy(isLoading = false, maxParticipantsInput = it.maxParticipants.toString()) }
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
                            color = habit.color.toColorInt(), // Безопасный парсинг hex-строки в Int
                            type = habit.type,
                            frequency = habit.frequency,
                            reminderTime = habit.reminderTime,
                            targetValue = habit.targetValue,
                            unit = habit.unit,
                            maxParticipants = habit.maxParticipants,
                            maxParticipantsInput = habit.maxParticipants.toString(),
                            inviteCode = habit.inviteCode,
                            isPublic = habit.isPublic,
                            participants = emptyList(), // Пока оставляем пустым, позже загрузим User объекты
                            isLoading = false
                        )
                    }
                } ?: run {
                    _uiState.update { it.copy(isLoading = false, maxParticipantsInput = it.maxParticipants.toString()) }
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
                val isNew = currentHabit == null
                _uiState.update { it.copy(isLoading = true) }
                
                val now = System.currentTimeMillis()
                val currentState = uiState.value
                
                if (isNew) {
                    // Создаем новую привычку
                    val creatorId = authRepository.getCurrentUserId() 
                        ?: throw IllegalStateException("User must be authenticated to create habits")
                    
                    // Для личных привычек создатель автоматически становится участником
                    // Для групповых привычек добавляем создателя к списку участников если его там нет
                    val participantsIds = currentState.participants.map { it.id }.toMutableList()
                    if (!participantsIds.contains(creatorId)) {
                        participantsIds.add(0, creatorId) // Добавляем создателя первым в списке
                    }
                    
                    val newHabit = Habit(
                        id = UUID.randomUUID().toString(),
                        title = currentState.title,
                        description = currentState.description,
                        icon = currentState.icon,
                        color = "#%06X".format(0xFFFFFF and currentState.color), // Конвертируем Int (ARGB) в hex-строку RGB
                        type = currentState.type,
                        frequency = currentState.frequency,
                        reminderTime = currentState.reminderTime,
                        reminderEnabled = currentState.reminderTime != null,
                        targetValue = currentState.targetValue,
                        unit = currentState.unit,
                        createdBy = creatorId,
                        participants = participantsIds, // Создатель всегда включен в участники
                        maxParticipants = currentState.maxParticipants,
                        inviteCode = currentState.inviteCode,
                        isPublic = currentState.isPublic,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    )
                    Log.d("AddEditHabitViewModel", "💾 [saveHabit] Calling addHabitUseCase for habit: ${newHabit.id}")
                    Log.d("AddEditHabitViewModel", "💾 [saveHabit] Habit details: title='${newHabit.title}', participants=${newHabit.participants}")
                    addHabitUseCase(newHabit)
                    Log.d("AddEditHabitViewModel", "✅ [saveHabit] Habit saved successfully: ${newHabit.id}")
                    // Важно: установить текущую привычку для дальнейших операций (например, отправки приглашений)
                    currentHabit = newHabit
                    
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
                        color = "#%06X".format(0xFFFFFF and currentState.color), // Конвертируем Int (ARGB) в hex-строку RGB
                        type = currentState.type,
                        frequency = currentState.frequency,
                        reminderTime = currentState.reminderTime,
                        reminderEnabled = currentState.reminderTime != null,
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
                    // Обновим ссылку на текущую привычку
                    currentHabit = updatedHabit
                }
                
                _uiState.update { it.copy(
                    isHabitSaved = true, 
                    isLoading = false,
                    successMessage = if (isNew) getApplication<Application>().getString(R.string.habit_created) else getApplication<Application>().getString(R.string.habit_updated)
                )}

                // Если пользователь просил отправить приглашения после сохранения — делаем это сейчас
                val stateAfterSave = _uiState.value
                if (stateAfterSave.sendInvitesAfterSave &&
                    stateAfterSave.selectedFriendsToInvite.isNotEmpty() &&
                    stateAfterSave.type == HabitType.GROUP &&
                    currentHabit?.id != null
                ) {
                    Log.d(
                        "AddEditHabitViewModel",
                        "[saveHabit] Trigger sendInvitations() after save: habitId=${currentHabit?.id}, selected=${stateAfterSave.selectedFriendsToInvite.size}"
                    )
                    // Сбрасываем флаг перед отправкой, чтобы не зациклиться при последующих сохранениях
                    _uiState.update { it.copy(sendInvitesAfterSave = false) }
                    sendInvitations()
                }
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
    
    override fun onTargetValueChange(targetValue: Int) {
        // Создаем временную привычку для валидации
        val tempHabit = createTempHabit().copy(targetValue = targetValue)
        val validationResult = HabitValidation.validateHabit(tempHabit)
        val targetValueError = validationResult.getError(HabitField.TARGET_VALUE)?.message
        
        _uiState.update { 
            it.copy(
                targetValue = targetValue,
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
        // Обеспечиваем единственную активную подписку
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                // Нормальная отмена при закрытии диалога — не считаем ошибкой
                _uiState.update { it.copy(isLoadingFriends = false) }
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
        if (show) {
            loadAvailableFriends()
        } else {
            // Закрываем подписку при закрытии диалога
            friendsJob?.cancel()
            _uiState.update { it.copy(isLoadingFriends = false) }
        }
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

    override fun requestInvitations() {
        val state = _uiState.value
        // Если можно отправить прямо сейчас — отправляем
        val canSendNow = state.type == HabitType.GROUP &&
                currentHabit?.id != null &&
                state.selectedFriendsToInvite.isNotEmpty() &&
                (state.participants.size + state.selectedFriendsToInvite.size) <= state.maxParticipants

        if (canSendNow) {
            sendInvitations()
            return
        }

        // Иначе отмечаем намерение и просим сохранить привычку
        _uiState.update {
            it.copy(
                sendInvitesAfterSave = true,
                successMessage = getApplication<Application>().getString(R.string.save_to_send_invitations)
            )
        }
    }

    override fun sendInvitations() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSendingInvitations = true, error = null) }
                Log.d("AddEditHabitViewModel", "[sendInvitations] start")
                
                val currentState = _uiState.value
                
                // Простая валидация перед отправкой
                if (currentState.selectedFriendsToInvite.isEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isSendingInvitations = false,
                            error = getApplication<Application>().getString(R.string.error_no_friends_selected)
                        )
                    }
                    return@launch
                }
                
                // Проверяем лимит участников
                val totalParticipants = currentState.participants.size + currentState.selectedFriendsToInvite.size
                if (totalParticipants > currentState.maxParticipants) {
                    _uiState.update { 
                        it.copy(
                            isSendingInvitations = false,
                            error = getApplication<Application>().getString(R.string.error_too_many_participants)
                        )
                    }
                    return@launch
                }

                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                val habitId = currentHabit?.id ?: throw Exception("Привычка не найдена")
                Log.d(
                    "AddEditHabitViewModel",
                    "[sendInvitations] will send: habitId=$habitId, from=$currentUserId, friends=${currentState.selectedFriendsToInvite.map { it.id }}"
                )
                
                // Отправляем приглашения по одному для лучшего контроля
                var successCount = 0
                var errorCount = 0
                val errors = mutableListOf<String>()
                
                for (friend in currentState.selectedFriendsToInvite) {
                    try {
                        Log.d("AddEditHabitViewModel", "[sendInvitations] -> to=${friend.id} (${friend.name})")
                        val result = sendHabitInvitationUseCase(habitId, currentUserId, friend.id)
                        result.onSuccess {
                            successCount++
                            Log.d("AddEditHabitViewModel", "[sendInvitations] <- SUCCESS to=${friend.id}")
                        }.onFailure { error: Throwable ->
                            errorCount++
                            errors.add("${friend.name}: ${error.message}")
                            Log.w("AddEditHabitViewModel", "[sendInvitations] <- FAILURE to=${friend.id}: ${error.message}", error)
                        }
                    } catch (e: Exception) {
                        errorCount++
                        errors.add("${friend.name}: ${e.message}")
                        Log.w("AddEditHabitViewModel", "[sendInvitations] <- EXCEPTION to=${friend.id}: ${e.message}", e)
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
                        lastInvitationSentAt = System.currentTimeMillis(),
                        hasUnsavedChanges = successCount > 0
                    )
                }
                Log.d("AddEditHabitViewModel", "[sendInvitations] done: success=$successCount errors=$errorCount")
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSendingInvitations = false,
                        error = "Ошибка отправки приглашений: ${e.message}"
                    )
                }
                Log.w("AddEditHabitViewModel", "[sendInvitations] FAILED: ${e.message}", e)
            }
        }
    }
    
    // Групповые привычки
    override fun onMaxParticipantsInputChange(text: String) {
        // Всегда сохраняем текст как есть
        _uiState.update { it.copy(maxParticipantsInput = text, hasUnsavedChanges = true) }

        // Пробуем распарсить и валидируем допустимый диапазон
        val value = text.toIntOrNull()
        if (value != null && value in GroupHabitConstants.MIN_PARTICIPANTS..GroupHabitConstants.MAX_PARTICIPANTS) {
            onMaxParticipantsChange(value)
        } else {
            // Если невалидно — не трогаем maxParticipants, только обновили текст; можно обновить подсказки валидации всей формы
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
    
    /**
     * Создает временную привычку из текущего состояния UI для валидации
     */
    private fun createTempHabit(): Habit {
        val currentState = _uiState.value
        return Habit(
            id = currentState.habitId ?: "",
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
            createdBy = "temp_user",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
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
