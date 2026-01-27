package com.habittracker.domain.model.gamification

import kotlinx.datetime.LocalDate

/**
 * Уровень активности для тепловой карты (Heatmap).
 */
enum class ActivityLevel {
    NONE,       // 0 привычек
    LOW,        // 1-2 привычки
    MEDIUM,     // 3-5 привычек
    HIGH,       // 6-9 привычек
    VERY_HIGH   // 10+ привычек
}

/**
 * Данные об активности за один день.
 */
data class DailyActivityLevel(
    val date: LocalDate,
    val count: Int,
    val level: ActivityLevel
)
