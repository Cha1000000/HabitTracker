package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.HabitParticipantSettingsEntity
import com.habittracker.domain.model.HabitParticipantSettings
import kotlinx.datetime.DayOfWeek

fun HabitParticipantSettingsEntity.toDomain(): HabitParticipantSettings = HabitParticipantSettings(
    habitId = habitId,
    userId = userId,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    reminderDays = reminderDays.mapNotNull { d ->
        DayOfWeek.entries.firstOrNull { it.value == d }
    }.toSet(),
    notificationsMuted = notificationsMuted,
    timeZone = timeZone,
    updatedAt = updatedAt
)

fun HabitParticipantSettings.toEntity(): HabitParticipantSettingsEntity = HabitParticipantSettingsEntity(
    key = "${habitId}_${userId}",
    habitId = habitId,
    userId = userId,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    reminderDays = reminderDays.map { it.value },
    notificationsMuted = notificationsMuted,
    timeZone = timeZone,
    updatedAt = updatedAt
)
