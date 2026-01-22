package com.habittracker.data.remote.model

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber

// Firestore DTO with no-arg constructor (all fields nullable with defaults)
// Не логируем чувствительные данные, валидация обязательна на маппере
// Даты хранятся как Any? для обратной совместимости (могут быть String или Long/HashMap)

data class HabitInvitationRemote(
    val id: String? = null, // Firestore doc may contain id field, but we prefer doc.id
    val habitId: String? = null,
    val habitTitle: String? = null,
    val fromUserId: String? = null,
    val fromUserName: String? = null,
    val toUserId: String? = null,
    val message: String? = null,
    val status: String? = null, // enum as String
    val createdAt: Any? = null,      // String (ISO 8601) или Long (timestamp) для обратной совместимости
    val respondedAt: Any? = null,    // String (ISO 8601) или Long (timestamp)
    val expiresAt: Any? = null,      // String (ISO 8601) или Long (timestamp)
    val updatedAt: Any? = null       // String (ISO 8601) или Long (timestamp)
) {
    fun toDomain(id: String): HabitInvitation? {
        val s = when (status) {
            HabitInvitationStatus.PENDING.name -> HabitInvitationStatus.PENDING
            HabitInvitationStatus.ACCEPTED.name -> HabitInvitationStatus.ACCEPTED
            HabitInvitationStatus.DECLINED.name -> HabitInvitationStatus.DECLINED
            HabitInvitationStatus.EXPIRED.name -> HabitInvitationStatus.EXPIRED
            HabitInvitationStatus.CANCELLED.name -> HabitInvitationStatus.CANCELLED
            else -> null
        }
        val hId = habitId
        val hTitle = habitTitle
        val fId = fromUserId
        val fName = fromUserName
        val tId = toUserId
        val created = createdAt

        if (id.isBlank() || hId.isNullOrBlank() || hTitle.isNullOrBlank() || fId.isNullOrBlank() || fName.isNullOrBlank() || tId.isNullOrBlank() || s == null || created == null) {
            return null
        }

        // Парсим даты с обратной совместимостью (String ISO 8601 или Long timestamp)
        val createdDateTime = parseDateTime(created, "createdAt") ?: return null
        val respondedDateTime = respondedAt?.let { parseDateTime(it, "respondedAt") }
        val expiresDateTime = expiresAt?.let { parseDateTime(it, "expiresAt") }

        return HabitInvitation(
            id = id,
            habitId = hId,
            habitTitle = hTitle,
            fromUserId = fId,
            fromUserName = fName,
            toUserId = tId,
            message = message,
            status = s,
            createdAt = createdDateTime,
            respondedAt = respondedDateTime,
            expiresAt = expiresDateTime
        )
    }
    
    private fun parseDateTime(value: Any?, fieldName: String): LocalDateTime? {
        return when (value) {
            is String -> {
                // Новый формат: ISO 8601 строка
                runCatching { LocalDateTime.parse(value) }.getOrNull()
            }
            is Long -> {
                // Старый формат: timestamp в миллисекундах
                runCatching { Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
            }
            is Number -> {
                // На всякий случай: другие числовые типы
                runCatching { Instant.fromEpochMilliseconds(value.toLong()).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
            }
            else -> {
                Timber.tag("HabitInvitationRemote").w("Unexpected type for $fieldName: ${value?.javaClass?.simpleName}")
                null
            }
        }
    }
}
