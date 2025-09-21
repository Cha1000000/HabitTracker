package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.UserEntity
import com.habittracker.domain.model.User
import kotlinx.datetime.TimeZone

// User Mappers
fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    avatarUrl = avatarUrl,
    avatarThumbnailUrl = avatarThumbnailUrl,
    level = level,
    points = points,
    streak = streak,
    longestStreak = longestStreak,
    friendsCount = friendsCount,
    fcmToken = fcmToken,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
    timezone = timezone
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    email = email,
    avatarUrl = avatarUrl,
    avatarThumbnailUrl = avatarThumbnailUrl,
    level = level,
    points = points,
    streak = streak,
    longestStreak = longestStreak,
    friendsCount = friendsCount,
    fcmToken = fcmToken,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
    timezone = timezone ?: TimeZone.currentSystemDefault().id
)
