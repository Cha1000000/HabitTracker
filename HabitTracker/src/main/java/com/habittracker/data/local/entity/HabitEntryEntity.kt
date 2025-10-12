package com.habittracker.data.local.entity

import androidx.room.Entity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "habit_entries",
    primaryKeys = ["habitId", "userId", "date"]
)
data class HabitEntryEntity(
    val habitId: String,
    val userId: String,
    val date: LocalDate,
    val completed: Boolean,
    val completedAt: LocalDateTime?, // точное время выполнения
    val value: Int,
    val note: String?,
    val createdAt: LocalDate,
    val updatedAt: LocalDate
)
