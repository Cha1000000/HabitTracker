package com.habittracker.data.remote.model

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus

// Firestore DTO with no-arg constructor (all fields nullable with defaults)
// Не логируем чувствительные данные, валидация обязательна на маппере

data class HabitInvitationRemote(
    val id: String? = null, // Firestore doc may contain id field, but we prefer doc.id
    val habitId: String? = null,
    val habitTitle: String? = null,
    val fromUserId: String? = null,
    val fromUserName: String? = null,
    val toUserId: String? = null,
    val message: String? = null,
    val status: String? = null, // enum as String
    val createdAt: Long? = null,
    val respondedAt: Long? = null,
    val expiresAt: Long? = null,
    val updatedAt: Long? = null
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

        return HabitInvitation(
            id = id,
            habitId = hId,
            habitTitle = hTitle,
            fromUserId = fId,
            fromUserName = fName,
            toUserId = tId,
            message = message,
            status = s,
            createdAt = created,
            respondedAt = respondedAt,
            expiresAt = expiresAt
        )
    }
}
