package com.habittracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.domain.usecase.challenge.CreateChallengeUseCase
import com.habittracker.presentation.state.CreateChallengeUiState
import com.habittracker.presentation.state.DatePickerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import javax.inject.Inject

@HiltViewModel
class CreateChallengeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getHabitsUseCase: GetHabitsUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val createChallengeUseCase: CreateChallengeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateChallengeUiState())
    val uiState: StateFlow<CreateChallengeUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

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
                                _uiState.value = _uiState.value.copy(
                                    availableHabits = habits
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
        _uiState.value = _uiState.value.copy(
            selectedHabit = habit,
            showHabitSelector = false
        )
    }

    fun onParticipantToggle(user: User) {
        val currentParticipants = _uiState.value.selectedParticipants
        val updatedParticipants = if (currentParticipants.contains(user)) {
            currentParticipants - user
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

    fun onRewardChange(reward: String) {
        _uiState.value = _uiState.value.copy(reward = reward)
    }

    fun showHabitSelector() {
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

    fun createChallenge(onSuccess: (Challenge) -> Unit) {
        val state = _uiState.value
        val userId = currentUserId
        
        Log.d("CreateChallengeVM", "createChallenge called, userId=$userId")
        
        if (userId == null) {
            Log.e("CreateChallengeVM", "userId is null, cannot create challenge")
            _uiState.value = state.copy(errorMessage = "Пользователь не авторизован")
            return
        }

        // Валидация
        if (state.title.isBlank()) {
            Log.d("CreateChallengeVM", "Validation failed: title is blank")
            _uiState.value = state.copy(errorMessage = "Введите название челленджа")
            return
        }

        if (state.selectedHabit == null) {
            Log.d("CreateChallengeVM", "Validation failed: no habit selected")
            _uiState.value = state.copy(errorMessage = "Выберите привычку")
            return
        }

        if (state.selectedParticipants.isEmpty()) {
            Log.d("CreateChallengeVM", "Validation failed: no participants selected")
            _uiState.value = state.copy(errorMessage = "Выберите хотя бы одного участника")
            return
        }

        if (state.startDate >= state.endDate) {
            Log.d("CreateChallengeVM", "Validation failed: startDate >= endDate")
            _uiState.value = state.copy(errorMessage = "Дата окончания должна быть позже даты начала")
            return
        }

        val targetValue = state.targetValue.toIntOrNull()
        if (targetValue == null || targetValue <= 0) {
            Log.d("CreateChallengeVM", "Validation failed: invalid targetValue=${state.targetValue}")
            _uiState.value = state.copy(errorMessage = "Введите корректное целевое значение")
            return
        }

        val allowMissedDays = state.allowMissedDays.toIntOrNull()
        if (allowMissedDays == null || allowMissedDays < 0) {
            Log.d("CreateChallengeVM", "Validation failed: invalid allowMissedDays=${state.allowMissedDays}")
            _uiState.value = state.copy(errorMessage = "Введите корректное количество пропущенных дней")
            return
        }

        Log.d("CreateChallengeVM", "Validation passed, creating challenge...")
        _uiState.value = state.copy(isCreating = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val rules = ChallengeRules(
                    type = state.challengeType,
                    targetValue = targetValue,
                    allowMissedDays = allowMissedDays,
                    requireConsecutive = state.requireConsecutive
                )

                Log.d("CreateChallengeVM", "Calling createChallengeUseCase with habitId=${state.selectedHabit.id}, participants=${state.selectedParticipants.map { it.id }}")
                
                val result = createChallengeUseCase(
                    habitId = state.selectedHabit.id,
                    title = state.title,
                    description = state.description,
                    createdBy = userId,
                    participantIds = state.selectedParticipants.map { it.id },
                    startDate = state.startDate,
                    endDate = state.endDate,
                    rules = rules,
                    reward = state.reward
                )

                Log.d("CreateChallengeVM", "createChallengeUseCase result: isSuccess=${result.isSuccess}")

                if (result.isSuccess) {
                    val challenge = result.getOrNull()!!
                    Log.d("CreateChallengeVM", "Challenge created successfully: id=${challenge.id}")
                    _uiState.value = _uiState.value.copy(isCreating = false)
                    onSuccess(challenge)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Ошибка создания челленджа"
                    Log.e("CreateChallengeVM", "Challenge creation failed: $error")
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                Log.e("CreateChallengeVM", "Exception during challenge creation", e)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    errorMessage = "Ошибка: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
