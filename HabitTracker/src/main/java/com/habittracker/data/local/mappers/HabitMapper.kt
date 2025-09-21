package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.HabitEntity
import com.habittracker.domain.model.Habit
import kotlinx.datetime.DayOfWeek

/**
 * Мапперы для преобразования между Entity и Domain моделями привычек
 */

fun HabitEntity.toDomain(): Habit {
    return Habit(
        id = id,
        title = title,
        description = description,
        icon = icon,
        color = color,
        type = type,
        frequency = frequency,
        reminderTime = reminderTime,
        reminderEnabled = reminderEnabled,
        reminderDays = reminderDays.mapNotNull { d ->
            DayOfWeek.entries.firstOrNull { it.value == d }
        }.toSet(),
        targetValue = targetValue,
        unit = unit,
        createdBy = createdBy,
        participants = participants,
        maxParticipants = maxParticipants,
        inviteCode = inviteCode,
        isPublic = isPublic,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Habit.toEntity(): HabitEntity {
    return HabitEntity(
        id = id,
        title = title,
        description = description,
        icon = icon,
        color = color,
        type = type,
        frequency = frequency,
        reminderTime = reminderTime,
        reminderEnabled = reminderEnabled,
        reminderDays = reminderDays.map { it.value },
        targetValue = targetValue,
        unit = unit,
        createdBy = createdBy,
        participants = participants,
        maxParticipants = maxParticipants,
        inviteCode = inviteCode,
        isPublic = isPublic,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun List<HabitEntity>.toDomain(): List<Habit> {
    return map { it.toDomain() }
}

fun List<Habit>.toEntity(): List<HabitEntity> {
    return map { it.toEntity() }
}
