package com.habittracker.presentation.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.presentation.viewmodel.AddEditHabitUiState
import com.habittracker.presentation.viewmodel.IAddEditHabitViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import javax.inject.Inject

/**
 * Тестовая реализация ViewModel для превью.
 * Реализует IAddEditHabitViewModel для работы в превью.
 */
class PreviewAddEditHabitViewModel @Inject constructor(
    private val testState: AddEditHabitUiState = AddEditHabitUiState()
) : ViewModel(), IAddEditHabitViewModel {
    private val _uiState = MutableStateFlow(testState)
    override val uiState: StateFlow<AddEditHabitUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isLoading = false) }
    }

    override fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    override fun onDescriptionChange(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

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

    override fun saveHabit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Имитация задержки сети
            delay(1000)
            _uiState.update { it.copy(
                isLoading = false,
                isHabitSaved = true,
                successMessage = "Привычка создана!"
            )}
        }
    }
    
    // Пустые реализации для use cases напоминаний (только для превью)
    override fun scheduleReminder() {
        // Пустая реализация для превью
    }
    
    override fun cancelReminder() {
        // Пустая реализация для превью
    }
    
    override fun updateReminder() {
        // Пустая реализация для превью
    }
}

/**
 * Создает тестовый экземпляр ViewModel с заполненными данными для превью.
 */
@Suppress("FunctionName")
fun PreviewAddEditHabitViewModel(
    title: String = "Бег по утрам",
    description: String = "Пробежка в парке",
    icon: String = "🏃",
    type: HabitType = HabitType.PERSONAL,
    frequency: HabitFrequency = HabitFrequency.DAILY,
    reminderTime: LocalTime? = LocalTime(8, 30),
    isReminderEnabled: Boolean = true,
    targetValue: Int = 5,
    unit: String = "км",
    isLoading: Boolean = false,
    isHabitSaved: Boolean = false
): PreviewAddEditHabitViewModel {
    return PreviewAddEditHabitViewModel(
        testState = AddEditHabitUiState(
            title = title,
            description = description,
            icon = icon,
            type = type,
            frequency = frequency,
            reminderTime = reminderTime,
            isReminderEnabled = isReminderEnabled,
            targetValue = targetValue,
            unit = unit,
            isLoading = isLoading,
            isHabitSaved = isHabitSaved
        )
    )
}
