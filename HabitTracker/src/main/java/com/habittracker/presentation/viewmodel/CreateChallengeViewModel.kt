package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.domain.usecase.challenge.CreateChallengeUseCase
import com.habittracker.presentation.state.CreateChallengeUiState
import com.habittracker.presentation.state.DatePickerType
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class CreateChallengeViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getHabitsUseCase: GetHabitsUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val createChallengeUseCase: CreateChallengeUseCase,
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateChallengeUiState())
    val uiState: StateFlow<CreateChallengeUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private var pendingEditId: String? = null

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                getCurrentUserUseCase().collect { user ->
                    user?.let { currentUser ->
                        currentUserId = currentUser.id
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        
                        // Загружаем привычки пользователя в отдельной корутине
                        launch {
                            getHabitsUseCase(currentUser.id).collect { habits ->
                                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                                val filteredHabits = habits
                                    .asSequence()
                                    .filter { it.isActive }
                                    .filter { it.type == HabitType.GROUP }
                                    .filter { it.participants.size >= 2 }
                                    .filter { it.startDate == null || it.startDate <= today }
                                    .filter { it.endDate == null || it.endDate >= today }
                                    .toList()
                                _uiState.value = _uiState.value.copy(
                                    availableHabits = filteredHabits
                                )
                            }
                        }
                        
                        // Загружаем друзей в отдельной корутине
                        launch {
                            getFriendsUseCase(currentUser.id).collect { friendships ->
                                val friends = friendships.mapNotNull { it.getOtherUser(currentUser.id) }
                                _uiState.value = _uiState.value.copy(
                                    availableFriends = friends
                                )
                            }
                        }

                        val editId = pendingEditId
                        if (editId != null) {
                            pendingEditId = null
                            launch {
                                loadChallengeForEdit(editId, currentUser.id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Ошибка загрузки данных: ${e.message}"
                )
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun onDescriptionChange(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onHabitSelected(habit: Habit) {
        if (_uiState.value.editingChallengeId != null) return
        _uiState.value = _uiState.value.copy(
            selectedHabit = habit,
            showHabitSelector = false
        )
    }

    fun onParticipantToggle(user: User) {
        val currentParticipants = _uiState.value.selectedParticipants
        val updatedParticipants = if (currentParticipants.any { it.id == user.id }) {
            currentParticipants.filterNot { it.id == user.id }
        } else {
            currentParticipants + user
        }
        _uiState.value = _uiState.value.copy(selectedParticipants = updatedParticipants)
    }

    fun onStartDateSelected(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            startDate = date,
            showDatePicker = false
        )
    }

    fun onEndDateSelected(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            endDate = date,
            showDatePicker = false
        )
    }

    fun onChallengeTypeSelected(type: ChallengeType) {
        _uiState.value = _uiState.value.copy(challengeType = type)
    }

    fun onTargetValueChange(value: String) {
        _uiState.value = _uiState.value.copy(targetValue = value)
    }

    fun onAllowMissedDaysChange(value: String) {
        _uiState.value = _uiState.value.copy(allowMissedDays = value)
    }

    fun onRequireConsecutiveToggle(require: Boolean) {
        _uiState.value = _uiState.value.copy(requireConsecutive = require)
    }

    fun onAllowBackfillCompletionsChange(allow: Boolean) {
        _uiState.value = _uiState.value.copy(allowBackfillCompletions = allow)
    }

    fun onRewardChange(reward: String) {
        _uiState.value = _uiState.value.copy(reward = reward)
    }

    fun showHabitSelector() {
        if (_uiState.value.editingChallengeId != null) return
        _uiState.value = _uiState.value.copy(showHabitSelector = true)
    }

    fun hideHabitSelector() {
        _uiState.value = _uiState.value.copy(showHabitSelector = false)
    }

    fun showParticipantSelector() {
        _uiState.value = _uiState.value.copy(showParticipantSelector = true)
    }

    fun hideParticipantSelector() {
        _uiState.value = _uiState.value.copy(showParticipantSelector = false)
    }

    fun showDatePicker(type: DatePickerType) {
        _uiState.value = _uiState.value.copy(
            showDatePicker = true,
            datePickerType = type
        )
    }

    fun hideDatePicker() {
        _uiState.value = _uiState.value.copy(showDatePicker = false)
    }

    fun createChallenge() {
        val state = _uiState.value
        val userId = currentUserId
        
        Timber.tag("CreateChallengeVM").d("createChallenge called, userId=$userId")
        
        if (userId == null) {
            Timber.tag("CreateChallengeVM").e("userId is null, cannot create challenge")
            _uiState.value = state.copy(errorMessage = "Пользователь не авторизован")
            return
        }

        // Валидация
        if (state.title.isBlank()) {
            Timber.tag("CreateChallengeVM").d("Validation failed: title is blank")
            _uiState.value = state.copy(errorMessage = "Введите название челленджа")
            return
        }

        if (state.selectedHabit == null) {
            Timber.tag("CreateChallengeVM").d("Validation failed: no habit selected")
            _uiState.value = state.copy(errorMessage = "Выберите привычку")
            return
        }

        if (state.selectedParticipants.isEmpty()) {
            Timber.tag("CreateChallengeVM").d("Validation failed: no participants selected")
            _uiState.value = state.copy(errorMessage = "Выберите хотя бы одного участника")
            return
        }

        if (state.startDate >= state.endDate) {
            Timber.tag("CreateChallengeVM").d("Validation failed: startDate >= endDate")
            _uiState.value = state.copy(errorMessage = "Дата окончания должна быть позже даты начала")
            return
        }

        val targetValue = state.targetValue.toIntOrNull()
        if (targetValue == null || targetValue <= 0) {
            Timber.tag("CreateChallengeVM").d("Validation failed: invalid targetValue=${state.targetValue}")
            _uiState.value = state.copy(errorMessage = "Введите корректное целевое значение")
            return
        }

        val allowMissedDays = state.allowMissedDays.toIntOrNull()
        if (allowMissedDays == null || allowMissedDays < 0) {
            Timber.tag("CreateChallengeVM").d("Validation failed: invalid allowMissedDays=${state.allowMissedDays}")
            _uiState.value = state.copy(errorMessage = "Введите корректное количество пропущенных дней")
            return
        }

        val selectedHabit = state.selectedHabit
        val isGroupHabit = selectedHabit.type == HabitType.GROUP
        val isGroupFull = isGroupHabit && selectedHabit.participants.size >= selectedHabit.maxParticipants

        val skipped = if (isGroupFull) {
            state.selectedParticipants
                .filter { it.id !in selectedHabit.participants }
        } else {
            emptyList()
        }

        val filteredParticipants = if (isGroupFull) {
            state.selectedParticipants
                .filter { it.id in selectedHabit.participants }
        } else {
            state.selectedParticipants
        }

        if (filteredParticipants.isEmpty()) {
            _uiState.value = state.copy(
                errorMessage = "В привычке нет свободных мест для новых участников"
            )
            return
        }

        Timber.tag("CreateChallengeVM").d("Validation passed, creating challenge...")
        _uiState.value = state.copy(
            isCreating = true,
            errorMessage = null,
            createdChallengeId = null,
            skippedInviteeNames = skipped.map { it.name },
            skippedInviteMaxParticipants = if (skipped.isNotEmpty()) selectedHabit.maxParticipants else null
        )

        viewModelScope.launch {
            try {
                val rules = ChallengeRules(
                    type = state.challengeType,
                    targetValue = targetValue,
                    allowMissedDays = allowMissedDays,
                    requireConsecutive = state.requireConsecutive,
                    allowBackfillCompletions = state.allowBackfillCompletions
                )

                Timber.tag("CreateChallengeVM").d("Calling createChallengeUseCase with habitId=${state.selectedHabit.id}, participants=${state.selectedParticipants.map { it.id }}")
                
                val result = createChallengeUseCase(
                    habitId = state.selectedHabit.id,
                    title = state.title,
                    description = state.description,
                    createdBy = userId,
                    participantIds = filteredParticipants.map { it.id },
                    startDate = state.startDate,
                    endDate = state.endDate,
                    rules = rules,
                    reward = state.reward
                )

                Timber.tag("CreateChallengeVM").d("createChallengeUseCase result: isSuccess=${result.isSuccess}")

                if (result.isSuccess) {
                    val challenge = result.getOrNull()!!
                    Timber.tag("CreateChallengeVM").d("Challenge created successfully: id=${challenge.id}")
                    
                    // Track challenge created event
                    analyticsManager.logEvent(
                        AnalyticsEvents.CHALLENGE_CREATED,
                        mapOf(AnalyticsEvents.Params.CHALLENGE_ID to challenge.id)
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createdChallengeId = challenge.id
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Ошибка создания челленджа"
                    Timber.tag("CreateChallengeVM").e("Challenge creation failed: $error")
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                Timber.tag("CreateChallengeVM").e(e, "Exception during challenge creation")
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    errorMessage = "Ошибка: ${e.message}"
                )
            }
        }
    }

    fun consumeCreatedChallenge() {
        _uiState.value = _uiState.value.copy(
            createdChallengeId = null,
            skippedInviteeNames = emptyList(),
            skippedInviteMaxParticipants = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun startEdit(challengeId: String) {
        if (challengeId.isBlank()) return
        if (_uiState.value.editingChallengeId == challengeId) return

        _uiState.value = _uiState.value.copy(
            editingChallengeId = challengeId,
            showHabitSelector = false,
            showParticipantSelector = false,
            showDatePicker = false
        )

        val userId = currentUserId
        if (userId == null) {
            pendingEditId = challengeId
            return
        }

        viewModelScope.launch {
            loadChallengeForEdit(challengeId, userId)
        }
    }

    private suspend fun loadChallengeForEdit(challengeId: String, userId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        try {
            val challenge = challengeRepository.getChallengeById(challengeId)
            if (challenge == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Челлендж не найден")
                return
            }
            if (challenge.createdBy != userId) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Нет прав на редактирование")
                return
            }
            if (challenge.status != ChallengeStatus.PENDING && challenge.status != ChallengeStatus.ACTIVE) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Редактирование недоступно")
                return
            }

            val habit = habitRepository.getHabitById(challenge.habitId)
            val friendsById = _uiState.value.availableFriends.associateBy { it.id }

            val participantUsers = mutableListOf<User>()
            for (participantId in challenge.participants) {
                if (participantId == challenge.createdBy) continue
                val user = friendsById[participantId] ?: socialRepository.getUserById(participantId)
                if (user != null) {
                    participantUsers.add(user)
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                title = challenge.title,
                description = challenge.description,
                selectedHabit = habit,
                selectedParticipants = participantUsers,
                startDate = challenge.startDate,
                endDate = challenge.endDate,
                challengeType = challenge.rules.type,
                targetValue = challenge.rules.targetValue.toString(),
                allowMissedDays = challenge.rules.allowMissedDays.toString(),
                requireConsecutive = challenge.rules.requireConsecutive,
                allowBackfillCompletions = challenge.rules.allowBackfillCompletions,
                reward = challenge.reward
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Ошибка загрузки челленджа: ${e.message}"
            )
        }
    }

    fun updateChallenge(onSuccess: (Challenge) -> Unit) {
        val state = _uiState.value
        val challengeId = state.editingChallengeId ?: return
        val userId = currentUserId

        if (userId == null) {
            _uiState.value = state.copy(errorMessage = "Пользователь не авторизован")
            return
        }

        if (state.title.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите название челленджа")
            return
        }

        if (state.selectedParticipants.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Выберите хотя бы одного участника")
            return
        }

        if (state.startDate >= state.endDate) {
            _uiState.value = state.copy(errorMessage = "Дата окончания должна быть позже даты начала")
            return
        }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        if (state.endDate <= today) {
            _uiState.value = state.copy(errorMessage = "Дата окончания должна быть в будущем")
            return
        }

        _uiState.value = state.copy(isCreating = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val existing = challengeRepository.getChallengeById(challengeId)
                if (existing == null) {
                    _uiState.value = _uiState.value.copy(isCreating = false, errorMessage = "Челлендж не найден")
                    return@launch
                }
                if (existing.createdBy != userId) {
                    _uiState.value = _uiState.value.copy(isCreating = false, errorMessage = "Нет прав на редактирование")
                    return@launch
                }
                if (existing.status != ChallengeStatus.PENDING && existing.status != ChallengeStatus.ACTIVE) {
                    _uiState.value = _uiState.value.copy(isCreating = false, errorMessage = "Редактирование недоступно")
                    return@launch
                }

                val newParticipantIds = (state.selectedParticipants.map { it.id } + userId).distinct()
                val newAcceptedParticipants = existing.acceptedParticipants.filter { it in newParticipantIds }

                val updatedRules = existing.rules.copy(
                    allowBackfillCompletions = state.allowBackfillCompletions
                )

                val updatedChallenge = existing.copy(
                    title = state.title,
                    description = state.description,
                    participants = newParticipantIds,
                    acceptedParticipants = newAcceptedParticipants,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    rules = updatedRules,
                    status = if (state.startDate <= today) ChallengeStatus.ACTIVE else ChallengeStatus.PENDING,
                    updatedAt = today
                )

                challengeRepository.updateChallenge(updatedChallenge)

                // Уведомления новым участникам (best-effort)
                try {
                    val creatorUser = socialRepository.getUserById(userId)
                    val habitTitle = state.selectedHabit?.title
                        ?: habitRepository.getHabitById(updatedChallenge.habitId)?.title
                        ?: ""
                    val addedParticipants = newParticipantIds
                        .filter { it != userId }
                        .filterNot { it in existing.participants }
                    addedParticipants.forEach { participantId ->
                        socialRepository.sendChallengeInvitationNotification(
                            toUserId = participantId,
                            fromUserName = creatorUser?.name ?: "Пользователь",
                            challengeId = updatedChallenge.id,
                            challengeTitle = updatedChallenge.title,
                            habitTitle = habitTitle
                        )
                    }
                } catch (_: Exception) {
                    // ignore
                }

                _uiState.value = _uiState.value.copy(isCreating = false)
                onSuccess(updatedChallenge)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    errorMessage = "Ошибка сохранения: ${e.message}"
                )
            }
        }
    }
}
