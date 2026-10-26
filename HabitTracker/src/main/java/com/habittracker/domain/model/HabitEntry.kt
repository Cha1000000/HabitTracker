package com.habittracker.domain.model

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class HabitEntry(
    val habitId: String = "",
    val userId: String = "",
    val date: LocalDate = LocalDate(2024, 1, 1),
    val completed: Boolean = false,
    val completedAt: LocalDateTime? = null, // точное время выполнения
    val value: Int = 1, // сколько раз выполнил (для привычек с targetValue > 1)
    val note: String? = "",
    val isRestDay: Boolean = false,
    val streakBonusAwardedDays: Int? = null,
    val photoUrl: String? = null, // URL фото-подтверждения (локальный file:// или удалённый https://)
    val createdAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val updatedAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
)

// Модель для UI с дополнительной информацией
data class HabitEntryWithUser(
    val entry: HabitEntry,
    val user: User
)

