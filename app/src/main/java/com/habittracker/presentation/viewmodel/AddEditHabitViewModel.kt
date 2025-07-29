package com.habittracker.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject

data class AddEditHabitUiState(
    val title: String = "",
    val description: String = "",
    val isHabitSaved: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val addHabitUseCase: AddHabitUseCase,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditHabitUiState())
    val uiState = _uiState.asStateFlow()

    private var currentHabit: Habit? = null

    init {
        val habitId: String? = savedStateHandle.get("habitId")
        if (habitId != null) {
            loadHabit(habitId)
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onDescriptionChange(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    private fun loadHabit(habitId: String) {
        viewModelScope.launch {
            currentHabit = getHabitByIdUseCase(habitId)
            currentHabit?.let { habit ->
                _uiState.update {
                    it.copy(
                        title = habit.title,
                        description = habit.description,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun saveHabit() {
        viewModelScope.launch {
            val now = Clock.System.now()
            if (currentHabit == null) {
                // Create new habit
                val creatorId = "DUMMY_USER_ID" // Placeholder
                val newHabit = Habit(
                    id = UUID.randomUUID().toString(),
                    title = uiState.value.title,
                    description = uiState.value.description,
                    createdBy = creatorId,
                    icon = "ic_default_habit",
                    type = HabitType.PERSONAL,
                    frequency = HabitFrequency.DAILY,
                    participants = listOf(creatorId),
                    createdAt = now,
                    updatedAt = now
                )
                addHabitUseCase(newHabit)
            } else {
                // Update existing habit
                val updatedHabit = currentHabit!!.copy(
                    title = uiState.value.title,
                    description = uiState.value.description,
                    updatedAt = now
                )
                updateHabitUseCase(updatedHabit)
            }
            _uiState.update { it.copy(isHabitSaved = true) }
        }
    }
}
