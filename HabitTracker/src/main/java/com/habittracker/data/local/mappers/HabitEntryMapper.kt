package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.HabitEntryEntity
import com.habittracker.data.local.entity.tuples.HabitEntryWithUserTuple
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.data.local.mappers.toDomain as habitEntryToDomain
import com.habittracker.data.local.mappers.toDomain as userToDomain

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

// HabitEntryWithUser mapper
fun HabitEntryWithUserTuple.toDomain(): HabitEntryWithUser = HabitEntryWithUser(
    entry = this.entry.habitEntryToDomain(),
    user = this.user.userToDomain()
)