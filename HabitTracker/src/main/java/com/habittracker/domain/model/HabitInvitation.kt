package com.habittracker.domain.model

import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Модель приглашения в групповую привычку
 */
data class HabitInvitation(
    val id: String,
    val habitId: String,
    val habitTitle: String, // Кэшированное название для UI
    val fromUserId: String,
    val fromUserName: String, // Кэшированное имя для UI
    val toUserId: String,
    val message: String? = null,
    val status: HabitInvitationStatus,
    val createdAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val respondedAt: LocalDateTime? = null,
    val expiresAt: LocalDateTime? = null
)

/**
 * Статусы приглашения в групповую привычку
 */
enum class HabitInvitationStatus {
    PENDING,    // ожидает ответа
    ACCEPTED,   // принято
    DECLINED,   // отклонено
    EXPIRED,    // истекло
    CANCELLED   // отменено
}

/**
 * Extension функции для работы с приглашениями
 */

fun HabitInvitation.isPending() = status == HabitInvitationStatus.PENDING

fun HabitInvitation.isAccepted() = status == HabitInvitationStatus.ACCEPTED

fun HabitInvitation.isDeclined() = status == HabitInvitationStatus.DECLINED

fun HabitInvitation.isExpired(): Boolean {
    val now = Clock.System.now()
    return status == HabitInvitationStatus.EXPIRED || (expiresAt != null && now > expiresAt.toInstant(TimeZone.currentSystemDefault()))
}

fun HabitInvitation.canRespond() = 
    isPending() && !isExpired()

fun HabitInvitation.canCancel(userId: String) = 
    isPending() && fromUserId == userId

// Получить текст времени создания приглашения
fun HabitInvitation.getCreatedTimeText(): String {
    val now = Clock.System.now()
    val createdInstant = createdAt.toInstant(TimeZone.currentSystemDefault())
    val duration = now - createdInstant
    val diffMinutes = duration.inWholeMinutes
    val diffHours = duration.inWholeHours
    val diffDays = duration.inWholeDays

    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "${diffMinutes}м назад"
        diffHours < 24 -> "${diffHours}ч назад"
        diffDays < 7 -> "${diffDays}д назад"
        else -> "давно"
    }
}

// Получить текст времени ответа на приглашение
fun HabitInvitation.getRespondedTimeText(): String {
    val responseTime = respondedAt ?: return "не отвечено"
    
    val now = Clock.System.now()
    val respondedInstant = responseTime.toInstant(TimeZone.currentSystemDefault())
    val duration = now - respondedInstant
    val diffMinutes = duration.inWholeMinutes
    val diffHours = duration.inWholeHours
    val diffDays = duration.inWholeDays

    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "${diffMinutes}м назад"
        diffHours < 24 -> "${diffHours}ч назад"
        diffDays < 7 -> "${diffDays}д назад"
        else -> "давно"
    }
}

// Получить статус приглашения в виде текста для UI
fun HabitInvitation.getStatusText() = when (status) {
    HabitInvitationStatus.PENDING -> if (isExpired()) "Истекло" else "Ожидает ответа"
    HabitInvitationStatus.ACCEPTED -> "Принято"
    HabitInvitationStatus.DECLINED -> "Отклонено"
    HabitInvitationStatus.EXPIRED -> "Истекло"
    HabitInvitationStatus.CANCELLED -> "Отменено"
}

// Получить время до истечения приглашения
fun HabitInvitation.getTimeUntilExpiry(): String? {
    val expiryTime = expiresAt ?: return null
    val now = Clock.System.now()
    val expiryInstant = expiryTime.toInstant(TimeZone.currentSystemDefault())
    
    if (now >= expiryInstant) return "Истекло"
    
    val duration = expiryInstant - now
    val diffHours = duration.inWholeHours
    val diffDays = duration.inWholeDays
    
    return when {
        diffDays > 0 -> "Истекает через ${diffDays}д"
        diffHours > 0 -> "Истекает через ${diffHours}ч"
        else -> "Истекает скоро"
    }
}
