package com.habittracker.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val avatarThumbnailUrl: String? = null,
    val level: Int = 1,
    val points: Int = 0,
    val streak: Int = 0, // текущая серия выполненных дней
    val longestStreak: Int = 0,
    val friendsCount: Int = 0, // количество друзей
    val fcmToken: String? = null, // токен для push-уведомлений
    val createdAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val lastActiveAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val timezone: String? = TimeZone.currentSystemDefault().id
)

// Extension functions для работы с пользователем
fun User.getDisplayName(): String = name.ifBlank { "Пользователь" }

fun User.getLevelProgress(): Float {
    val pointsForCurrentLevel = (level - 1) * 1000
    val pointsForNextLevel = level * 1000
    val currentLevelPoints = points - pointsForCurrentLevel
    return if (pointsForNextLevel > pointsForCurrentLevel) {
        currentLevelPoints.toFloat() / (pointsForNextLevel - pointsForCurrentLevel)
    } else 1f
}

fun User.getLevelTitle(): String = when (level) {
    in 1..5 -> "Новичок"
    in 6..15 -> "Энтузиаст"
    in 16..30 -> "Эксперт"
    in 31..50 -> "Мастер"
    else -> "Легенда"
}

fun User.isOnline(): Boolean {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    // Считаем онлайн, если последняя активность была сегодня
    return lastActiveAt == today
}

fun User.getLastActiveText(): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysDiff = today.toEpochDays() - lastActiveAt.toEpochDays()

    return when {
        daysDiff == 0 -> "сегодня"
        daysDiff == 1 -> "вчера"
        daysDiff < 7 -> "$daysDiff дн назад"
        else -> "давно"
    }
}
