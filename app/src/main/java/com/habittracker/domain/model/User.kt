package com.habittracker.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val level: Int = 1,
    val points: Int = 0,
    val streak: Int = 0, // текущая серия выполненных дней
    val longestStreak: Int = 0,
    val friendsCount: Int = 0, // количество друзей
    val fcmToken: String? = null, // токен для push-уведомлений
    val createdAt: Instant = kotlinx.datetime.Clock.System.now(),
    val lastActiveAt: Instant = kotlinx.datetime.Clock.System.now(),
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
    val now = kotlinx.datetime.Clock.System.now()
    val timeDiff = now - lastActiveAt
    return timeDiff.inWholeMinutes < 15 // онлайн если активность была меньше 15 минут назад
}

fun User.getLastActiveText(): String {
    val now = kotlinx.datetime.Clock.System.now()
    val timeDiff = now - lastActiveAt

    return when {
        timeDiff.inWholeMinutes < 1 -> "только что"
        timeDiff.inWholeMinutes < 60 -> "${timeDiff.inWholeMinutes} мин назад"
        timeDiff.inWholeHours < 24 -> "${timeDiff.inWholeHours} ч назад"
        timeDiff.inWholeDays < 7 -> "${timeDiff.inWholeDays} дн назад"
        else -> "давно"
    }
}
