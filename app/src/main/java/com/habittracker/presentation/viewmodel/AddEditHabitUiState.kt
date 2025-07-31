package com.habittracker.presentation.viewmodel

import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import kotlinx.datetime.LocalTime

data class AddEditHabitUiState(
    // Основные поля
    val title: String = "",
    val description: String = "",
    val icon: String = "🎯",
    val type: HabitType = HabitType.PERSONAL,
    
    // Настройки частоты
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    
    // Напоминание
    val reminderTime: LocalTime? = null,
    val isReminderEnabled: Boolean = false,
    
    // Целевые значения
    val targetValue: Int = 1,
    val unit: String = "раз",
    
    // Состояния
    val isHabitSaved: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
