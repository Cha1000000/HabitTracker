package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "habit_entries",
    primaryKeys = ["habitId", "userId", "date"],
    indices = [
        Index(value = ["habitId"]),
        Index(value = ["userId"]),
        Index(value = ["date"]),
        Index(value = ["completed"]),
        Index(value = ["habitId", "date"]),
        Index(value = ["userId", "date"]),
        Index(value = ["habitId", "userId"])
    ]
)
data class HabitEntryEntity(
    val habitId: String,
    val userId: String,
    val date: LocalDate,
    val completed: Boolean,
    val completedAt: LocalDateTime?, // точное время выполнения
    val value: Int,
    val note: String?,
    val isRestDay: Boolean,
    val streakBonusAwardedDays: Int?,
    val photoUrl: String?, // URL фото-подтверждения
    val createdAt: LocalDate,
    val updatedAt: LocalDate,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deletedAtMillis: Long? = null
)
