package com.habittracker.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

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
    val createdAt: Instant,
    val respondedAt: Instant? = null,
    val expiresAt: Instant? = null
)

/**
 * Статусы приглашения в групповую привычку
 */
enum class HabitInvitationStatus {
    PENDING,    // Ожидает ответа
    ACCEPTED,   // Принято
    DECLINED,   // Отклонено
    EXPIRED,    // Истекло
    CANCELLED   // Отменено отправителем
}

/**
 * Extension функции для работы с приглашениями
 */

fun HabitInvitation.isPending(): Boolean = status == HabitInvitationStatus.PENDING

fun HabitInvitation.isAccepted(): Boolean = status == HabitInvitationStatus.ACCEPTED

fun HabitInvitation.isDeclined(): Boolean = status == HabitInvitationStatus.DECLINED

fun HabitInvitation.isExpired(): Boolean = 
    status == HabitInvitationStatus.EXPIRED || 
    (expiresAt != null && Clock.System.now() > expiresAt)

fun HabitInvitation.canRespond(): Boolean = 
    isPending() && !isExpired()

fun HabitInvitation.canCancel(userId: String): Boolean = 
    fromUserId == userId && isPending() && !isExpired()

/**
 * Получить текст времени создания приглашения
 */
fun HabitInvitation.getCreatedTimeText(): String {
    val now = Clock.System.now()
    val timeZone = TimeZone.currentSystemDefault()
    val createdDateTime = createdAt.toLocalDateTime(timeZone)
    val nowDateTime = now.toLocalDateTime(timeZone)
    
    val diffInDays = nowDateTime.date.toEpochDays() - createdDateTime.date.toEpochDays()
    
    return when {
        diffInDays == 0 -> {
            val diffInHours = (now.epochSeconds - createdAt.epochSeconds) / 3600
            when {
                diffInHours < 1 -> "только что"
                diffInHours < 2 -> "час назад"
                diffInHours < 5 -> "$diffInHours часа назад"
                else -> "$diffInHours часов назад"
            }
        }
        diffInDays == 1 -> "вчера"
        diffInDays < 7 -> "$diffInDays дн. назад"
        diffInDays < 30 -> "${diffInDays / 7} нед. назад"
        else -> "${diffInDays / 30} мес. назад"
    }
}

/**
 * Получить текст времени ответа на приглашение
 */
fun HabitInvitation.getRespondedTimeText(): String? {
    if (respondedAt == null) return null
    
    val now = Clock.System.now()
    val timeZone = TimeZone.currentSystemDefault()
    val respondedDateTime = respondedAt.toLocalDateTime(timeZone)
    val nowDateTime = now.toLocalDateTime(timeZone)
    
    val diffInDays = nowDateTime.date.toEpochDays() - respondedDateTime.date.toEpochDays()
    
    return when {
        diffInDays == 0 -> {
            val diffInHours = (now.epochSeconds - respondedAt.epochSeconds) / 3600
            when {
                diffInHours < 1 -> "только что"
                diffInHours < 2 -> "час назад"
                diffInHours < 5 -> "$diffInHours часа назад"
                else -> "$diffInHours часов назад"
            }
        }
        diffInDays == 1 -> "вчера"
        diffInDays < 7 -> "$diffInDays дн. назад"
        diffInDays < 30 -> "${diffInDays / 7} нед. назад"
        else -> "${diffInDays / 30} мес. назад"
    }
}

/**
 * Получить статус приглашения в виде текста для UI
 */
fun HabitInvitation.getStatusText(): String = when (status) {
    HabitInvitationStatus.PENDING -> "Ожидает ответа"
    HabitInvitationStatus.ACCEPTED -> "Принято"
    HabitInvitationStatus.DECLINED -> "Отклонено"
    HabitInvitationStatus.EXPIRED -> "Истекло"
    HabitInvitationStatus.CANCELLED -> "Отменено"
}

/**
 * Получить время до истечения приглашения
 */
fun HabitInvitation.getTimeUntilExpiry(): String? {
    if (expiresAt == null || isExpired()) return null
    
    val now = Clock.System.now()
    val diffInHours = (expiresAt.epochSeconds - now.epochSeconds) / 3600
    
    return when {
        diffInHours < 1 -> "менее часа"
        diffInHours < 24 -> "$diffInHours ч."
        else -> "${diffInHours / 24} дн."
    }
}
