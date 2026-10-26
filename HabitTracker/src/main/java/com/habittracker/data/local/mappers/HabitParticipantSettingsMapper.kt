package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.HabitParticipantSettingsEntity
import com.habittracker.domain.model.HabitParticipantSettings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

fun HabitParticipantSettingsEntity.toDomain(): HabitParticipantSettings = HabitParticipantSettings(
    habitId = habitId,
    userId = userId,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    reminderDays = reminderDays.mapNotNull { d ->
        DayOfWeek.entries.firstOrNull { it.isoDayNumber == d }
    }.toSet(),
    notificationsMuted = notificationsMuted,
    personalEndDate = personalEndDate,
    seasonStartDate = seasonStartDate,
    timeZone = timeZone,
    updatedAt = updatedAt
)

fun HabitParticipantSettings.toEntity(): HabitParticipantSettingsEntity = HabitParticipantSettingsEntity(
    key = "${habitId}_${userId}",
    habitId = habitId,
    userId = userId,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    reminderDays = reminderDays.map { it.isoDayNumber },
    notificationsMuted = notificationsMuted,
    personalEndDate = personalEndDate,
    seasonStartDate = seasonStartDate,
    timeZone = timeZone,
    updatedAt = updatedAt
)
