package com.habittracker.domain.model

import kotlinx.datetime.LocalTime

/**
 * Глобальные настройки пользователя (помимо уведомлений)
 */
data class UserPreferences(
    val sortCompletedHabitsToBottom: Boolean = true,
    val dailyReminderEnabled: Boolean = true,
    val dailyReminderTime: LocalTime = LocalTime(19, 0)
)
