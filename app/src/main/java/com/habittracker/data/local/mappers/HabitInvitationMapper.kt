package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.HabitInvitationEntity
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus
import kotlinx.datetime.Instant

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
        createdAt = Instant.fromEpochSeconds(createdAt),
        respondedAt = respondedAt?.let { Instant.fromEpochSeconds(it) },
        expiresAt = expiresAt?.let { Instant.fromEpochSeconds(it) }
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
        createdAt = createdAt.epochSeconds,
        respondedAt = respondedAt?.epochSeconds,
        expiresAt = expiresAt?.epochSeconds
    )
}

fun List<HabitInvitationEntity>.toDomain(): List<HabitInvitation> {
    return map { it.toDomain() }
}

fun List<HabitInvitation>.toEntity(): List<HabitInvitationEntity> {
    return map { it.toEntity() }
}
