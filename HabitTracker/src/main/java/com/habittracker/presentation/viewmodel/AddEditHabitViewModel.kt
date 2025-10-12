package com.habittracker.presentation.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.graphics.toColorInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitValidation
import com.habittracker.domain.model.HabitValidation.HabitField
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.ApplyReminderToAllParticipantsUseCase
import com.habittracker.domain.usecase.CancelHabitReminderUseCase
import com.habittracker.domain.usecase.EnsureMySettingsForHabitUseCase
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.GetHabitCompletionStatusUseCase
import com.habittracker.domain.usecase.GetMyHabitReminderSettingsUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.UpdateMyHabitReminderSettingsUseCase
import com.habittracker.domain.usecase.habit.GenerateHabitInviteCodeUseCase
import com.habittracker.domain.usecase.habit.SendHabitInvitationUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.presentation.state.AddEditHabitUiState
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
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val application: Application,
    private val addHabitUseCase: AddHabitUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase,
    private val cancelHabitReminderUseCase: CancelHabitReminderUseCase,
    private val getMyHabitReminderSettingsUseCase: GetMyHabitReminderSettingsUseCase,
    private val updateMyHabitReminderSettingsUseCase: UpdateMyHabitReminderSettingsUseCase,
    private val sendHabitInvitationUseCase: SendHabitInvitationUseCase,
    private val generateHabitInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val applyReminderToAllParticipantsUseCase: ApplyReminderToAllParticipantsUseCase,
    private val ensureMySettingsForHabitUseCase: EnsureMySettingsForHabitUseCase,
    private val authRepository: AuthRepository,
    private val getHabitCompletionStatusUseCase: GetHabitCompletionStatusUseCase,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application), IAddEditHabitViewModel {

    private val _uiState = MutableStateFlow(AddEditHabitUiState())
    override val uiState: StateFlow<AddEditHabitUiState> = _uiState.asStateFlow()

    private var currentHabit: Habit? = null
    private var friendsJob: Job? = null

    init {
        val habitId: String? = savedStateHandle["habitId"]
        val startDateParam: String? = savedStateHandle["startDate"]
        
        if (habitId != null) {
            _uiState.update { it.copy(isLoading = true) }
            loadHabit(habitId)
        } else {
            // Парсим дату начала из параметров навигации
            val initialStartDate = startDateParam?.let { dateStr ->
                try {
                    LocalDate.parse(dateStr)
                } catch (_: Exception) {
                    Log.w("AddEditHabitViewModel", "Failed to parse startDate from navigation: $dateStr")
                    null
                }
            }
            
            _uiState.update { it.copy(
                isLoading = false,
                isInitialized = true,
                maxParticipantsInput = it.maxParticipants.toString(),
                startDate = initialStartDate // Устанавливаем дату начала из параметра
            ) }
            
            if (initialStartDate != null) {
                Log.d("AddEditHabitViewModel", "📅 Initialized with startDate from navigation: $initialStartDate")
            }
        }
    }

    // Применить текущие настройки напоминаний для всех участников (для автора)
    override fun applyReminderToAllParticipants() {
        viewModelScope.launch {
            try {
                val habit = currentHabit ?: return@launch
                val state = uiState.value
                _uiState.update { it.copy(isLoading = true) }
                val res = applyReminderToAllParticipantsUseCase(
                    habitId = habit.id,
                    reminderTime = state.reminderTime,
                    reminderDays = state.reminderDaysInput,
                    enabled = state.reminderEnabled
                )
                if (res.isSuccess) {
                    // Важно: обновим локальную currentHabit, чтобы последующее сохранение не откатило дефолтные напоминания
                    currentHabit = currentHabit?.copy(
                        reminderTime = if (state.reminderEnabled) state.reminderTime else null,
                        reminderEnabled = state.reminderEnabled,
                        reminderDays = state.reminderDaysInput
                    )
                    // Синхронизируем UI: включаем тумблер и проставляем дни согласно применённым значениям
                    _uiState.update { it.copy(
                        isLoading = false,
                        reminderEnabled = state.reminderEnabled,
                        reminderTime = state.reminderTime,
                        reminderDaysInput = state.reminderDaysInput,
                        successMessage = getApplication<Application>().getString(R.string.habit_updated)
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = res.exceptionOrNull()?.localizedMessage ?: "Ошибка применения настроек для всех"
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Ошибка применения настроек для всех"
                ) }
            }
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
                    val currentUserId = authRepository.getCurrentUserId() ?: ""
                    
                    // Проверяем, была ли привычка выполнена хотя бы раз
                    val hasBeenCompleted = try {
                        getHabitCompletionStatusUseCase(habitId, currentUserId)
                    } catch (_: Exception) {
                        false // При ошибке считаем, что не выполнена
                    }
                    
                    _uiState.update { state ->
                        state.copy(
                            habitId = habit.id,
                            title = habit.title,
                            description = habit.description,
                            icon = habit.icon,
                            color = habit.color.toColorInt(), // Безопасный парсинг hex-строки в Int
                            type = habit.type,
                            frequency = habit.frequency,
                            reminderEnabled = habit.reminderEnabled,
                            reminderTime = habit.reminderTime,
                            reminderDaysInput = habit.reminderDays,
                            targetValue = habit.targetValue,
                            targetValueInput = habit.targetValue.toString(),
                            unit = habit.unit,
                            maxParticipants = habit.maxParticipants,
                            maxParticipantsInput = habit.maxParticipants.toString(),
                            inviteCode = habit.inviteCode,
                            isPublic = habit.isPublic,
                            participants = emptyList(), // Пока оставляем пустым, позже загрузим User объекты
                            isLoading = false,
                            isHabitSaved = false,
                            canApplyReminderToAll = (habit.type == HabitType.GROUP && habit.createdBy == currentUserId),
                            startDate = habit.startDate,
                            endDate = habit.endDate,
                            // Дату начала можно редактировать только если привычка ещё не была выполнена
                            canEditStartDate = !hasBeenCompleted
                        )
                    }
                    // Переопределяем/гарантируем персональные настройки через use case
                    try {
                        ensureMySettingsForHabitUseCase(habit)
                        // Важно: берём свежие персональные настройки (remote-preferred), а не первый элемент локального observe
                        val personal = getMyHabitReminderSettingsUseCase.getOnce(habit.id)
                        if (personal != null) {
                            _uiState.update { s ->
                                s.copy(
                                    reminderEnabled = personal.reminderEnabled,
                                    reminderTime = personal.reminderTime,
                                    reminderDaysInput = personal.reminderDays
                                )
                            }
                        } else {
                            // Фолбэк на дефолты привычки
                            _uiState.update { s ->
                                s.copy(
                                    reminderEnabled = habit.reminderEnabled,
                                    reminderTime = habit.reminderTime,
                                    reminderDaysInput = habit.reminderDays
                                )
                            }
                        }
                        _uiState.update { it.copy(isInitialized = true) }
                    } catch (e: Exception) {
                        Log.d("AddEditHabitViewModel", "ensureMySettingsForHabitUseCase failed (non-fatal): ${e.message}")
                    }

                    // Финальная нормализация UI: если есть время, но флаг выключен — включим флаг (устранение артефактов старых данных)
                    val s = _uiState.value
                    if (s.reminderTime != null && !s.reminderEnabled) {
                        _uiState.update { it.copy(reminderEnabled = true) }
                    }

                    Log.d(
                        "AddEditHabitViewModel",
                        "[loadHabit] ui: enabled=${_uiState.value.reminderEnabled}, time=${_uiState.value.reminderTime}, days=${_uiState.value.reminderDaysInput}"
                    )
                } ?: run {
                    _uiState.update { it.copy(isLoading = false, maxParticipantsInput = it.maxParticipants.toString(), isInitialized = true) }
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
                
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
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
                    
                    // Сохраняем дату начала как есть (может быть в будущем)
                    // Если не указана - по умолчанию сегодня
                    val effectiveStartDate = currentState.startDate 
                        ?: Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                    
                    Log.d("AddEditHabitViewModel", "📅 [saveHabit] Creating habit with startDate=$effectiveStartDate (UI state startDate=${currentState.startDate})")
                    
                    val newHabit = Habit(
                        id = UUID.randomUUID().toString(),
                        title = currentState.title,
                        description = currentState.description,
                        icon = currentState.icon,
                        color = "#%06X".format(0xFFFFFF and currentState.color), // Конвертируем Int (ARGB) в hex-строку RGB
                        type = currentState.type,
                        frequency = currentState.frequency,
                        reminderTime = if (currentState.reminderEnabled) currentState.reminderTime else null,
                        reminderEnabled = currentState.reminderEnabled,
                        reminderDays = currentState.reminderDaysInput,
                        targetValue = currentState.targetValue,
                        unit = currentState.unit,
                        createdBy = creatorId,
                        participants = participantsIds, // Создатель всегда включен в участники
                        maxParticipants = currentState.maxParticipants,
                        inviteCode = currentState.inviteCode,
                        isPublic = currentState.isPublic,
                        isActive = true,
                        createdAt = today,
                        updatedAt = today,
                        startDate = effectiveStartDate, // Всегда сохраняем дату начала
                        endDate = currentState.endDate
                    )
                    Log.d("AddEditHabitViewModel", "💾 [saveHabit] Calling addHabitUseCase for habit: ${newHabit.id}")
                    Log.d("AddEditHabitViewModel", "💾 [saveHabit] Habit details: title='${newHabit.title}', participants=${newHabit.participants}")
                    Log.d("AddEditHabitViewModel", "📅 [saveHabit] Habit dates: startDate=${newHabit.startDate}, endDate=${newHabit.endDate}")
                    addHabitUseCase(newHabit)
                    Log.d("AddEditHabitViewModel", "✅ [saveHabit] Habit saved successfully: ${newHabit.id}")
                    // Важно: установить текущую привычку для дальнейших операций (например, отправки приглашений)
                    currentHabit = newHabit
                    // Обновляем habitId в uiState для корректной работы методов после сохранения
                    _uiState.update { it.copy(habitId = newHabit.id) }
                    
                    // Планируем напоминание для новой привычки
                    if (newHabit.reminderTime != null) {
                        // Инициализируем персональные настройки для создателя из дефолтов привычки
                        try {
                            updateMyHabitReminderSettingsUseCase(
                                HabitParticipantSettings(
                                    habitId = newHabit.id,
                                    userId = creatorId,
                                    reminderTime = newHabit.reminderTime,
                                    reminderEnabled = true,
                                    reminderDays = newHabit.reminderDays
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("AddEditHabitViewModel", "updateMyHabitReminderSettingsUseCase failed for new habit: ${e.message}", e)
                        }
                        scheduleHabitReminderUseCase(newHabit)
                        Log.d("AddEditHabitViewModel", "Напоминание запланировано для новой привычки")
                    }
                } else {
                    // Обновляем существующую привычку
                    val safeParticipants: List<String> = if (currentState.participants.isNotEmpty()) {
                        currentState.participants.map { it.id }
                    } else {
                        // Сохраняем текущий состав участников, если UI ещё не успел подгрузить User-объекты
                        currentHabit!!.participants
                    }
                    // Частота — глобальная для группы: менять может только автор
                    val currentUserIdForUpdate = authRepository.getCurrentUserId() ?: ""
                    val isAuthor = currentHabit!!.createdBy == currentUserIdForUpdate
                    val effectiveFrequency = if (currentHabit!!.type == HabitType.GROUP && !isAuthor) {
                        currentHabit!!.frequency
                    } else currentState.frequency
                    // Обрабатываем дату начала
                    val effectiveStartDate = when {
                        // Если можно редактировать и задана дата - используем её
                        currentState.canEditStartDate && currentState.startDate != null -> currentState.startDate
                        // Если нельзя редактировать, берём существующую дату или текущую, если её не было
                        !currentState.canEditStartDate -> currentHabit!!.startDate ?: Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                        // Если можно редактировать, но дата не задана - используем текущую
                        else -> Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                    }
                    
                    val updatedHabit = currentHabit!!.copy(
                        title = currentState.title,
                        description = currentState.description,
                        icon = currentState.icon,
                        color = "#%06X".format(0xFFFFFF and currentState.color), // Конвертируем Int (ARGB) в hex-строку RGB
                        type = currentState.type,
                        frequency = effectiveFrequency,
                        targetValue = currentState.targetValue,
                        unit = currentState.unit,
                        maxParticipants = currentState.maxParticipants,
                        inviteCode = currentState.inviteCode,
                        isPublic = currentState.isPublic,
                        participants = safeParticipants,
                        updatedAt = today,
                        startDate = effectiveStartDate, // Всегда сохраняем валидную дату начала
                        endDate = currentState.endDate
                    )
                    updateHabitUseCase(updatedHabit)
                    
                    // Обновляем персональные настройки напоминаний текущего пользователя и перепланируем
                    try {
                        val userId = authRepository.getCurrentUserId() ?: ""
                        updateMyHabitReminderSettingsUseCase(
                            HabitParticipantSettings(
                                habitId = updatedHabit.id,
                                userId = userId,
                                reminderTime = if (currentState.reminderEnabled) currentState.reminderTime else null,
                                reminderEnabled = currentState.reminderEnabled,
                                reminderDays = currentState.reminderDaysInput
                            )
                        )
                        scheduleHabitReminderUseCase(updatedHabit)
                        Log.d("AddEditHabitViewModel", "Персональные напоминания обновлены и перепланированы")
                    } catch (e: Exception) {
                        Log.w("AddEditHabitViewModel", "updateMyHabitReminderSettingsUseCase/scheduleHabitReminderUseCase failed: ${e.message}", e)
                    }
                    // Обновим ссылку на текущую привычку
                    currentHabit = updatedHabit
                    // Обновляем habitId в uiState для корректной работы методов после сохранения
                    _uiState.update { it.copy(habitId = updatedHabit.id) }
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
        _uiState.update { it.copy(type = type, hasUnsavedChanges = true) }
    }
    
    override fun onFrequencyChange(frequency: HabitFrequency) {
        // Если редактируем существующую групповую привычку и пользователь не автор — блокируем смену частоты
        currentHabit?.let { habit ->
            if (habit.type == HabitType.GROUP) {
                val userId = authRepository.getCurrentUserId()
                if (userId != null && habit.createdBy != userId) {
                    return
                }
            }
        }
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
            state.copy(frequency = frequency, reminderDaysInput = presetDays)
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
                targetValueInput = value.toString(),
                targetValueError = targetValueError,
                hasUnsavedChanges = true
            )
        }
        updateValidationState()
    }

    override fun onTargetValueInputChange(text: String) {
        // Разрешаем только цифры и ограничиваем длину до 3 символов (макс 100)
        val filtered = text.filter { it.isDigit() }.take(3)
        // Всегда сохраняем текст как есть (после фильтра) — важно для корректной работы курсора/удаления
        _uiState.update { it.copy(targetValueInput = filtered, hasUnsavedChanges = true) }

        val value = filtered.toIntOrNull()
        if (value != null) {
            onTargetValueChange(value)
        } else {
            // Пустая строка или некорректное число — просто обновим состояние валидации без изменения targetValue
            updateValidationState()
        }
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
    
    fun showMessage(message: String) {
        _uiState.update { it.copy(successMessage = message) }
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
                    // Обновляем персональные настройки: выключаем напоминания
                    val userId = authRepository.getCurrentUserId() ?: return@launch
                    updateMyHabitReminderSettingsUseCase(
                        HabitParticipantSettings(
                            habitId = habit.id,
                            userId = userId,
                            reminderTime = null,
                            reminderEnabled = false,
                            reminderDays = emptySet()
                        )
                    )
                    // Отменяем запланированные задачи
                    cancelHabitReminderUseCase(habit.id)
                    Log.d("AddEditHabitViewModel", "Персональные напоминания отключены и отменены")
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
                val habit = currentHabit ?: return@launch
                val userId = authRepository.getCurrentUserId() ?: return@launch
                // Обновляем перс. настройки
                updateMyHabitReminderSettingsUseCase(
                    HabitParticipantSettings(
                        habitId = habit.id,
                        userId = userId,
                        reminderTime = if (currentState.reminderEnabled) currentState.reminderTime else null,
                        reminderEnabled = currentState.reminderEnabled,
                        reminderDays = currentState.reminderDaysInput
                    )
                )
                // Перепланируем напоминание с учётом перс. настроек
                scheduleHabitReminderUseCase(habit)
                Log.d("AddEditHabitViewModel", "Персональные напоминания обновлены и перепланированы")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.error_updating_reminder, e.localizedMessage ?: "")) }
            }
        }
    }
    
    override fun onReminderTimeChange(time: LocalTime?) {
        _uiState.update { s ->
            s.copy(
                reminderTime = time,
                reminderEnabled = if (time != null) true else s.reminderEnabled,
                hasUnsavedChanges = true
            )
        }
    }

    override fun onReminderEnabledChange(enabled: Boolean) {
        _uiState.update { s -> s.copy(reminderEnabled = enabled, hasUnsavedChanges = true) }
    }

    override fun onReminderDayToggle(day: DayOfWeek) {
        _uiState.update { state ->
            val current = state.reminderDaysInput.toMutableSet()
            val toggled = if (current.contains(day)) {
                current.remove(day)
                current
            } else {
                // Добавляем с учетом ограничений по частоте
                when (state.frequency) {
                    HabitFrequency.WEEKLY -> setOf(day)
                    HabitFrequency.TWICE_A_WEEK -> {
                        if (current.size >= 2) {
                            // Не добавляем больше 2
                            current
                        } else {
                            current.add(day)
                            current
                        }
                    }
                    HabitFrequency.WEEKDAYS -> setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                    HabitFrequency.WEEKENDS -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                    HabitFrequency.CUSTOM, HabitFrequency.DAILY -> {
                        current.add(day)
                        current
                    }
                }
            }
            state.copy(reminderDaysInput = toggled)
        }
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
                
                // Используем habitId из uiState для корректной работы после отключения кода
                Log.d("AddEditHabitViewModel", "[generateInviteCode] currentState.habitId=${currentState.habitId}, currentHabit?.id=${currentHabit?.id}")
                val habitId = currentState.habitId 
                    ?: currentHabit?.id 
                    ?: throw Exception("Привычка не найдена")
                Log.d("AddEditHabitViewModel", "[generateInviteCode] Using habitId=$habitId")
                
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
                
                // Используем habitId из uiState для корректной работы при редактировании
                val habitId = _uiState.value.habitId 
                    ?: currentHabit?.id 
                    ?: throw Exception("Привычка не найдена")
                
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
        if (_uiState.value.habitId == null) return

        // Создаем Intent для отправки текста
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, application.getString(
                R.string.share_invite_code_text,
                _uiState.value.title,
                inviteCode
            ))
            putExtra(Intent.EXTRA_SUBJECT, application.getString(R.string.share_invite_code_subject))
        }

        // Создаем chooser для выбора приложения
        val chooserIntent = Intent.createChooser(
            shareIntent,
            application.getString(R.string.share_invite_code_title)
        )
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Запускаем системный диалог
        application.startActivity(chooserIntent)

        _uiState.update {
            it.copy(
                successMessage = application.getString(R.string.invite_code_shared)
            )
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
            } catch (_: CancellationException) {
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
        val persistedType = currentHabit?.type
        val typeChanged = persistedType != null && persistedType != state.type

        // Если тип изменился (например, PERSONAL -> GROUP), надо сначала сохранить
        if (typeChanged) {
            _uiState.update {
                it.copy(
                    sendInvitesAfterSave = true,
                    successMessage = getApplication<Application>().getString(R.string.save_to_send_invitations)
                )
            }
            return
        }

        // Можно отправлять только если сохранённая привычка уже GROUP
        val canSendNow = state.type == HabitType.GROUP &&
                currentHabit?.type == HabitType.GROUP &&
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
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            inviteCode = currentState.inviteCode,
            startDate = currentState.startDate,
            endDate = currentState.endDate
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
        Log.d("AddEditHabitViewModel", "📅 [onStartDateChange] New start date selected: $date")
        _uiState.update { currentState ->
            // Если дата окончания установлена и она раньше новой даты начала, сбрасываем её
            val newEndDate = if (date != null && currentState.endDate != null && currentState.endDate < date) {
                null
            } else {
                currentState.endDate
            }
            
            currentState.copy(
                startDate = date,
                endDate = newEndDate,
                hasUnsavedChanges = true
            )
        }
        Log.d("AddEditHabitViewModel", "📅 [onStartDateChange] UI State updated: startDate=${_uiState.value.startDate}")
    }
    
    override fun onEndDateChange(date: LocalDate?) {
        _uiState.update { currentState ->
            // Валидация: дата окончания не может быть раньше даты начала
            val validEndDate = if (date != null) {
                val effectiveStartDate = currentState.startDate 
                    ?: Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                if (date >= effectiveStartDate) date else null
            } else {
                null
            }
            
            currentState.copy(
                endDate = validEndDate,
                hasUnsavedChanges = true
            )
        }
    }

}
