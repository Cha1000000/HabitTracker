package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import com.habittracker.domain.usecase.reset.ResetProgressUseCase
import com.habittracker.presentation.state.GroupHabitResetItem
import com.habittracker.presentation.state.ResetProgressUiState
import com.habittracker.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResetProgressViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getHabitsUseCase: GetHabitsUseCase,
    private val resetProgressUseCase: ResetProgressUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetProgressUiState())
    val uiState: StateFlow<ResetProgressUiState> = _uiState.asStateFlow()

    private var habitsJob: Job? = null

    init {
        observeUserAndHabits()
    }

    private fun observeUserAndHabits() {
        viewModelScope.launch {
            getCurrentUserUseCase().collectLatest { user ->
                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userId = null,
                        error = "User is not authenticated"
                    )
                    habitsJob?.cancel()
                    return@collectLatest
                }

                val userId = user.id
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    userId = userId,
                    error = null
                )

                habitsJob?.cancel()
                habitsJob = viewModelScope.launch {
                    getHabitsUseCase(userId).collectLatest { habits ->
                        val personalHabitsCount = habits.count { it.type == HabitType.PERSONAL }
                        val groupHabits = habits
                            .filter { it.type == HabitType.GROUP }
                            .map { habit ->
                                GroupHabitResetItem(
                                    habitId = habit.id,
                                    title = habit.title,
                                    isOwner = habit.createdBy == userId,
                                    participantsCount = habit.participants.size
                                )
                            }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            personalHabitsCount = personalHabitsCount,
                            groupHabits = groupHabits
                        )
                    }
                }
            }
        }
    }

    fun resetPointsSeason() = launchReset(R.string.reset_success_points_season) {
        val userId = _uiState.value.userId ?: return@launchReset
        resetProgressUseCase.resetUserSeasonProgress(userId)
    }

    fun resetPointsFull() = launchReset(R.string.reset_success_points_full) {
        val userId = _uiState.value.userId ?: return@launchReset
        resetProgressUseCase.resetUserFullProgress(userId)
    }

    fun resetPersonalHabitsSeason() = launchReset(R.string.reset_success_personal_season) {
        val userId = _uiState.value.userId ?: return@launchReset
        resetProgressUseCase.resetPersonalHabitsSeason(userId)
    }

    fun resetPersonalHabitsFull() = launchReset(R.string.reset_success_personal_full) {
        val userId = _uiState.value.userId ?: return@launchReset
        resetProgressUseCase.resetPersonalHabitsFull(userId)
    }

    fun resetGroupHabitSeason(habitId: String) = launchReset(R.string.reset_success_group_season) {
        val userId = _uiState.value.userId ?: return@launchReset
        resetProgressUseCase.resetGroupHabitSeasonForUser(habitId, userId)
    }

    fun resetGroupHabitFull(habitId: String) = launchReset(R.string.reset_success_group_full) {
        val userId = _uiState.value.userId ?: return@launchReset
        resetProgressUseCase.resetGroupHabitFullForUser(habitId, userId)
    }

    fun resetGroupHabitFullForAll(habitId: String) = launchReset(R.string.reset_success_group_full_all) {
        val userId = _uiState.value.userId ?: return@launchReset
        resetProgressUseCase.resetGroupHabitFullForAll(habitId, userId)
    }

    private fun launchReset(successResId: Int, block: suspend () -> Unit) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            runCatching { block() }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        successResId = successResId,
                        error = null
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = e.message ?: "Reset failed"
                    )
                }
        }
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun onSuccessShown() {
        _uiState.value = _uiState.value.copy(successResId = null)
    }
}
