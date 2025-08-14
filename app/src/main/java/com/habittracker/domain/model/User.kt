package com.habittracker.domain.model

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
    val createdAt: Long = System.currentTimeMillis(), // timestamp в миллисекундах
    val lastActiveAt: Long = System.currentTimeMillis(), // timestamp в миллисекундах
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
    val now = System.currentTimeMillis()
    val timeDiff = now - lastActiveAt
    return timeDiff / 1000 / 60 < 15 // онлайн если активность была меньше 15 минут назад
}

fun User.getLastActiveText(): String {
    val now = System.currentTimeMillis()
    val timeDiff = now - lastActiveAt

    return when {
        timeDiff / 1000 / 60 < 1 -> "только что"
        timeDiff / 1000 / 60 < 60 -> "${timeDiff / 1000 / 60} мин назад"
        timeDiff / 1000 / 60 / 60 < 24 -> "${timeDiff / 1000 / 60 / 60} ч назад"
        timeDiff / 1000 / 60 / 60 / 24 < 7 -> "${timeDiff / 1000 / 60 / 60 / 24} дн назад"
        else -> "давно"
    }
}
