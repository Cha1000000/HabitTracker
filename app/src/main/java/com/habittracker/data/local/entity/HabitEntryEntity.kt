package com.habittracker.data.local.entity

import androidx.room.Entity
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "habit_entries",
    primaryKeys = ["habitId", "userId", "date"]
)
data class HabitEntryEntity(
    val habitId: String,
    val userId: String,
    val date: LocalDate,
    val completed: Boolean,
    val completedAt: Long?,
    val value: Int,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long
)
