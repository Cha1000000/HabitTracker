package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.HabitInvitationEntity
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus

/**
 * Мапперы для преобразования между Entity и Domain моделями приглашений в привычки
 */

fun HabitInvitationEntity.toDomain(): HabitInvitation {
    return HabitInvitation(
        id = id,
        habitId = habitId,
        habitTitle = habitTitle,
        fromUserId = fromUserId,
        fromUserName = fromUserName,
        toUserId = toUserId,
        message = message,
        status = HabitInvitationStatus.valueOf(status),
        createdAt = createdAt,
        respondedAt = respondedAt,
        expiresAt = expiresAt
    )
}

fun HabitInvitation.toEntity(): HabitInvitationEntity {
    return HabitInvitationEntity(
        id = id,
        habitId = habitId,
        habitTitle = habitTitle,
        fromUserId = fromUserId,
        fromUserName = fromUserName,
        toUserId = toUserId,
        message = message,
        status = status.name,
        createdAt = createdAt,
        respondedAt = respondedAt,
        expiresAt = expiresAt
    )
}

fun List<HabitInvitationEntity>.toDomain(): List<HabitInvitation> {
    return map { it.toDomain() }
}

fun List<HabitInvitation>.toEntity(): List<HabitInvitationEntity> {
    return map { it.toEntity() }
}
