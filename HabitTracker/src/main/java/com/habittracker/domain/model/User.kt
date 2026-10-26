package com.habittracker.domain.model

import com.habittracker.domain.model.gamification.GamificationConstants
import kotlin.time.Clock
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
    val lastActiveAt: Long = Clock.System.now().toEpochMilliseconds(), // timestamp для точного онлайн-статуса
    val timezone: String? = TimeZone.currentSystemDefault().id
)

// Extension functions для работы с пользователем
fun User.getDisplayName(): String = name.ifBlank { "Пользователь" }

fun User.getLevelProgress(): Float {
    // Recalculate level from points to ensure consistency
    val calculatedLevel = GamificationConstants.getLevelFromPoints(points)
    
    val pointsForCurrentLevel = GamificationConstants.pointsForLevel(calculatedLevel)
    val pointsForNextLevel = GamificationConstants.pointsForLevel(calculatedLevel + 1)
    
    val currentLevelPoints = points - pointsForCurrentLevel
    val pointsNeeded = pointsForNextLevel - pointsForCurrentLevel
    
    return if (pointsNeeded > 0) {
        (currentLevelPoints.toFloat() / pointsNeeded.toFloat()).coerceIn(0f, 1f)
    } else 1f
}

fun User.getLevelTitle(): String = when (GamificationConstants.getLevelFromPoints(points)) {
    in 1..5 -> "Новичок"
    in 6..15 -> "Энтузиаст"
    in 16..30 -> "Эксперт"
    in 31..50 -> "Мастер"
    else -> "Легенда"
}

fun User.getLevelTitleResId(): Int = when (GamificationConstants.getLevelFromPoints(points)) {
    in 1..5 -> com.habittracker.R.string.level_title_novice
    in 6..15 -> com.habittracker.R.string.level_title_enthusiast
    in 16..30 -> com.habittracker.R.string.level_title_expert
    in 31..50 -> com.habittracker.R.string.level_title_master
    else -> com.habittracker.R.string.level_title_legend
}

fun User.isOnline(): Boolean {
    val now = Clock.System.now().toEpochMilliseconds()
    val diffMinutes = (now - lastActiveAt) / 60_000
    // Считаем онлайн, если последняя активность была менее 5 минут назад
    return diffMinutes < 5
}

fun User.getLastActiveText(): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diffMinutes = (now - lastActiveAt) / 60_000
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 5 -> "в сети"
        diffMinutes < 60 -> "был(а) $diffMinutes мин назад"
        diffHours < 24 -> "был(а) $diffHours ч назад"
        diffDays == 1L -> "вчера"
        diffDays < 7 -> "$diffDays дн назад"
        else -> "давно"
    }
}
