package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.FriendRequestEntity
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestStatus

// FriendRequest Mappers
fun FriendRequestEntity.toDomain(): FriendRequest = FriendRequest(
    id = id,
    fromUserId = fromUserId,
    toUserId = toUserId,
    status = FriendRequestStatus.valueOf(status),
    message = message,
    createdAt = createdAt,
    respondedAt = respondedAt,
    updatedAt = updatedAt
)

fun FriendRequest.toEntity(): FriendRequestEntity = FriendRequestEntity(
    id = id,
    fromUserId = fromUserId,
    toUserId = toUserId,
    status = status.name,
    message = message,
    createdAt = createdAt,
    respondedAt = respondedAt,
    updatedAt = updatedAt
)
