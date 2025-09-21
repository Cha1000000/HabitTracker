package com.habittracker.presentation.viewmodel

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class CreateChallengeUiState(
    val title: String = "",
    val description: String = "",
    val selectedHabit: Habit? = null,
    val availableHabits: List<Habit> = emptyList(),
    val selectedParticipants: List<User> = emptyList(),
    val availableFriends: List<User> = emptyList(),
    val startDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val endDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(7, DateTimeUnit.DAY),
    val challengeType: ChallengeType = ChallengeType.COMPLETION_RATE,
    val targetValue: String = "100",
    val allowMissedDays: String = "0",
    val requireConsecutive: Boolean = false,
    val reward: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showHabitSelector: Boolean = false,
    val showParticipantSelector: Boolean = false,
    val showDatePicker: Boolean = false,
    val datePickerType: DatePickerType = DatePickerType.START_DATE,
    val isCreating: Boolean = false
)

enum class DatePickerType {
    START_DATE,
    END_DATE
}

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
                        
                        // Загружаем привычки пользователя
                        getHabitsUseCase(currentUser.id).collect { habits ->
                            _uiState.value = _uiState.value.copy(
                                availableHabits = habits,
                                isLoading = false
                            )
                        }
                        
                        // Загружаем друзей
                        getFriendsUseCase(currentUser.id).collect { friendships ->
                            val friends = friendships.mapNotNull { it.getOtherUser(currentUser.id) }
                            _uiState.value = _uiState.value.copy(
                                availableFriends = friends
                            )
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
        val userId = currentUserId ?: return

        // Валидация
        if (state.title.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите название челленджа")
            return
        }

        if (state.selectedHabit == null) {
            _uiState.value = state.copy(errorMessage = "Выберите привычку")
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

        val targetValue = state.targetValue.toIntOrNull()
        if (targetValue == null || targetValue <= 0) {
            _uiState.value = state.copy(errorMessage = "Введите корректное целевое значение")
            return
        }

        val allowMissedDays = state.allowMissedDays.toIntOrNull()
        if (allowMissedDays == null || allowMissedDays < 0) {
            _uiState.value = state.copy(errorMessage = "Введите корректное количество пропущенных дней")
            return
        }

        _uiState.value = state.copy(isCreating = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val rules = ChallengeRules(
                    type = state.challengeType,
                    targetValue = targetValue,
                    allowMissedDays = allowMissedDays,
                    requireConsecutive = state.requireConsecutive
                )

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

                if (result.isSuccess) {
                    val challenge = result.getOrNull()!!
                    onSuccess(challenge)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Ошибка создания челленджа"
                    )
                }
            } catch (e: Exception) {
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
