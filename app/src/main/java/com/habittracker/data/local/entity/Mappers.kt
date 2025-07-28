package com.habittracker.data.local.entity

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.User
import kotlinx.datetime.TimeZone

// User Mappers
fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    avatarUrl = avatarUrl,
    level = level,
    points = points,
    streak = streak,
    longestStreak = longestStreak,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
    timezone = timezone
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    email = email,
    avatarUrl = avatarUrl,
    level = level,
    points = points,
    streak = streak,
    longestStreak = longestStreak,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
    timezone = timezone ?: TimeZone.currentSystemDefault().id
)

// Habit Mappers
fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    title = title,
    description = description,
    icon = icon,
    color = color,
    type = type,
    frequency = frequency,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    targetValue = targetValue,
    unit = unit,
    createdBy = createdBy,
    participants = participants,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    title = title,
    description = description,
    icon = icon,
    color = color,
    type = type,
    frequency = frequency,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    targetValue = targetValue,
    unit = unit,
    createdBy = createdBy,
    participants = participants,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// HabitEntry Mappers
fun HabitEntryEntity.toDomain(): HabitEntry = HabitEntry(
    habitId = habitId,
    userId = userId,
    date = date,
    completed = completed,
    completedAt = completedAt,
    value = value,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HabitEntry.toEntity(): HabitEntryEntity = HabitEntryEntity(
    habitId = habitId,
    userId = userId,
    date = date,
    completed = completed,
    completedAt = completedAt,
    value = value,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)
