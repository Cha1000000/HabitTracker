package com.habittracker.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Персональные настройки участника для привычки (Reminder settings per user)
 */
data class HabitParticipantSettings(
    val habitId: String,
    val userId: String,
    val reminderTime: LocalTime? = null,
    val reminderEnabled: Boolean = false,
    val reminderDays: Set<DayOfWeek> = emptySet(),
    val notificationsMuted: Boolean = false,
    val timeZone: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
