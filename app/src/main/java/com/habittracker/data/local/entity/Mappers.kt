package com.habittracker.data.local.entity

import com.habittracker.data.local.entity.tuples.HabitEntryWithUserTuple
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestStatus
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipStatus
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.model.User
import kotlinx.datetime.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// User Mappers
fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    avatarUrl = avatarUrl,
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

// Habit Mappers
fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    title = title,
    description = description,
    icon = icon,
    color = color,
    type = type,
    frequency = frequency,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    targetValue = targetValue,
    unit = unit,
    createdBy = createdBy,
    participants = participants,
    maxParticipants = maxParticipants,
    inviteCode = inviteCode,
    isPublic = isPublic,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    title = title,
    description = description,
    icon = icon,
    color = color,
    type = type,
    frequency = frequency,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled,
    targetValue = targetValue,
    unit = unit,
    createdBy = createdBy,
    participants = participants,
    maxParticipants = maxParticipants,
    inviteCode = inviteCode,
    isPublic = isPublic,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// HabitEntry Mappers
fun HabitEntryEntity.toDomain(): HabitEntry = HabitEntry(
    habitId = habitId,
    userId = userId,
    date = date,
    completed = completed,
    completedAt = completedAt,
    value = value,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HabitEntry.toEntity(): HabitEntryEntity = HabitEntryEntity(
    habitId = habitId,
    userId = userId,
    date = date,
    completed = completed,
    completedAt = completedAt,
    value = value,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HabitEntryWithUserTuple.toDomain(): HabitEntryWithUser = HabitEntryWithUser(
    entry = this.entry.toDomain(),
    user = this.user.toDomain()
)

// Challenge Mappers
fun ChallengeEntity.toDomain(): Challenge = Challenge(
    id = id,
    habitId = habitId,
    title = title,
    description = description,
    createdBy = createdBy,
    participants = participants,
    startDate = startDate,
    endDate = endDate,
    rules = Json.decodeFromString<ChallengeRules>(rules),
    reward = reward,
    status = ChallengeStatus.valueOf(status),
    winnerId = winnerId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Challenge.toEntity(): ChallengeEntity = ChallengeEntity(
    id = id,
    habitId = habitId,
    title = title,
    description = description,
    createdBy = createdBy,
    participants = participants,
    startDate = startDate,
    endDate = endDate,
    rules = Json.encodeToString(rules),
    reward = reward,
    status = status.name,
    winnerId = winnerId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

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

// HabitInvitation Mappers
fun HabitInvitationEntity.toDomain(): HabitInvitation = HabitInvitation(
    id = id,
    habitId = habitId,
    habitTitle = habitTitle,
    fromUserId = fromUserId,
    fromUserName = fromUserName,
    toUserId = toUserId,
    message = message,
    status = HabitInvitationStatus.valueOf(status),
    createdAt = kotlinx.datetime.Instant.fromEpochSeconds(createdAt),
    respondedAt = respondedAt?.let { kotlinx.datetime.Instant.fromEpochSeconds(it) },
    expiresAt = expiresAt?.let { kotlinx.datetime.Instant.fromEpochSeconds(it) }
)

fun HabitInvitation.toEntity(): HabitInvitationEntity = HabitInvitationEntity(
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
