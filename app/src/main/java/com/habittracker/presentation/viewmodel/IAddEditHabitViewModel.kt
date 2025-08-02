package com.habittracker.presentation.viewmodel

import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.HabitFrequency
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalTime

interface IAddEditHabitViewModel {
    val uiState: StateFlow<AddEditHabitUiState>
    
    fun onTitleChange(newTitle: String)
    fun onDescriptionChange(newDescription: String)
    fun onIconChange(newIcon: String)
    fun onColorChange(newColor: String)
    fun onTypeChange(newType: HabitType)
    fun onFrequencyChange(newFrequency: HabitFrequency)
    fun onReminderTimeChange(newTime: LocalTime?)
    fun onReminderEnabledChange(isEnabled: Boolean)
    fun onTargetValueChange(newValue: Int)
    fun onUnitChange(newUnit: String)
    fun onErrorShown()
    fun onSuccessMessageShown()
    fun saveHabit()
}
