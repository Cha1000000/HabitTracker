package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.FriendshipEntity
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipStatus

// Friendship Mappers
fun FriendshipEntity.toDomain(): Friendship = Friendship(
    userId1 = userId1,
    userId2 = userId2,
    status = FriendshipStatus.valueOf(status),
    requestedBy = requestedBy,
    requestedAt = requestedAt,
    acceptedAt = acceptedAt,
    blockedBy = blockedBy,
    blockedAt = blockedAt,
    updatedAt = updatedAt
)

fun Friendship.toEntity(): FriendshipEntity = FriendshipEntity(
    userId1 = userId1,
    userId2 = userId2,
    status = status.name,
    requestedBy = requestedBy,
    requestedAt = requestedAt,
    acceptedAt = acceptedAt,
    blockedBy = blockedBy,
    blockedAt = blockedAt,
    updatedAt = updatedAt
)
