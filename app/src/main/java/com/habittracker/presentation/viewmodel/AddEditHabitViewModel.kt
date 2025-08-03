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
import com.habittracker.domain.usecase.AddHabitUseCase
import com.habittracker.domain.usecase.CancelHabitReminderUseCase
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.UpdateHabitReminderUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
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

    override fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    override fun onDescriptionChange(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    private fun loadHabit(habitId: String) {
        viewModelScope.launch {
            try {
                currentHabit = getHabitByIdUseCase(habitId)
                currentHabit?.let { habit ->
                    _uiState.update { state ->
                        state.copy(
                            title = habit.title,
                            description = habit.description,
                            icon = habit.icon,
                            color = habit.color, // Загружаем цвет привычки
                            type = habit.type,
                            frequency = habit.frequency,
                            reminderTime = habit.reminderTime,
                            isReminderEnabled = habit.reminderEnabled,
                            targetValue = habit.targetValue,
                            unit = habit.unit,
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
                    val creatorId = getApplication<Application>().getString(R.string.dummy_user_id) // TODO: Заменить на реальный ID пользователя
                    val newHabit = Habit(
                        id = UUID.randomUUID().toString(),
                        title = currentState.title,
                        description = currentState.description,
                        icon = currentState.icon,
                        color = currentState.color, // Используем выбранный цвет
                        type = currentState.type,
                        frequency = currentState.frequency,
                        reminderTime = if (currentState.isReminderEnabled) currentState.reminderTime else null,
                        reminderEnabled = currentState.isReminderEnabled,
                        targetValue = currentState.targetValue,
                        unit = currentState.unit,
                        createdBy = creatorId,
                        participants = listOf(creatorId),
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    )
                    addHabitUseCase(newHabit)
                    
                    // Планируем напоминание для новой привычки
                    if (newHabit.reminderEnabled && newHabit.reminderTime != null) {
                        scheduleHabitReminderUseCase(newHabit)
                        Log.d("AddEditHabitViewModel", "Напоминание запланировано для новой привычки")
                    }
                } else {
                    // Обновляем существующую привычку
                    val updatedHabit = currentHabit!!.copy(
                        title = currentState.title,
                        description = currentState.description,
                        icon = currentState.icon,
                        color = currentState.color, // Используем выбранный цвет
                        type = currentState.type,
                        frequency = currentState.frequency,
                        reminderTime = if (currentState.isReminderEnabled) currentState.reminderTime else null,
                        reminderEnabled = currentState.isReminderEnabled,
                        targetValue = currentState.targetValue,
                        unit = currentState.unit,
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
    override fun onIconChange(newIcon: String) {
        _uiState.update { it.copy(icon = newIcon) }
    }
    
    override fun onColorChange(newColor: String) {
        _uiState.update { it.copy(color = newColor) }
    }
    
    override fun onTypeChange(newType: HabitType) {
        _uiState.update { it.copy(type = newType) }
    }
    
    override fun onFrequencyChange(newFrequency: HabitFrequency) {
        _uiState.update { it.copy(frequency = newFrequency) }
    }
    
    override fun onReminderTimeChange(newTime: LocalTime?) {
        _uiState.update { it.copy(reminderTime = newTime) }
    }
    
    override fun onReminderEnabledChange(isEnabled: Boolean) {
        _uiState.update { it.copy(isReminderEnabled = isEnabled) }
    }
    
    override fun onTargetValueChange(newValue: Int) {
        _uiState.update { it.copy(targetValue = newValue) }
    }
    
    override fun onUnitChange(newUnit: String) {
        _uiState.update { it.copy(unit = newUnit) }
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
                if (currentState.isReminderEnabled && currentState.reminderTime != null) {
                    // Создаем временную привычку для планирования напоминания
                    val tempHabit = currentHabit?.copy(
                        reminderTime = currentState.reminderTime,
                        reminderEnabled = currentState.isReminderEnabled
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
                    reminderTime = if (currentState.isReminderEnabled) currentState.reminderTime else null,
                    reminderEnabled = currentState.isReminderEnabled
                ) ?: return@launch
                
                updateHabitReminderUseCase(tempHabit)
                Log.d("AddEditHabitViewModel", "Напоминание обновлено")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.error_updating_reminder, e.localizedMessage ?: "")) }
            }
        }
    }
}
