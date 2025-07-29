package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Habit
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.GetHabitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State для экрана со списком привычек.
 */
data class HabitsUiState(
    val habits: List<Habit> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val getHabitsUseCase: GetHabitsUseCase,
    private val addHabitUseCase: AddHabitUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()

    init {
        // "DUMMY_USER_ID" нужно будет заменить на реальный ID пользователя
        loadHabits("DUMMY_USER_ID")
    }

    /**
     * Загружает список привычек для пользователя.
     */
    private fun loadHabits(userId: String) {
        getHabitsUseCase(userId)
            .onEach { habits ->
                _uiState.value = HabitsUiState(habits = habits, isLoading = false)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Добавляет новую привычку.
     */
    fun addHabit(habit: Habit) {
        viewModelScope.launch {
            addHabitUseCase(habit)
            // После добавления список обновится автоматически благодаря Flow
        }
    }
}
