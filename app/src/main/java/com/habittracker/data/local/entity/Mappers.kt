package com.habittracker.data.local.entity

import com.google.gson.Gson
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipStatus
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.User
import kotlinx.datetime.TimeZone

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

fun List<HabitEntryEntity>.toDomain(): List<HabitEntry> = map { it.toDomain() }

// Challenge Mappers
fun Challenge.toEntity(gson: Gson): ChallengeEntity = ChallengeEntity(
    id = id,
    habitId = habitId,
    title = title,
    description = description,
    createdBy = createdBy,
    participants = participants,
    startDate = startDate,
    endDate = endDate,
    rules = gson.toJson(rules), // Serialize ChallengeRules to JSON
    reward = reward,
    status = status.name,
    winnerId = winnerId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChallengeEntity.toDomain(gson: Gson): Challenge {
    val rulesObject = gson.fromJson(rules, ChallengeRules::class.java)
    return Challenge(
        id = id,
        habitId = habitId,
        title = title,
        description = description,
        createdBy = createdBy,
        participants = participants,
        startDate = startDate,
        endDate = endDate,
        rules = rulesObject, // Deserialize JSON to ChallengeRules
        reward = reward,
        status = ChallengeStatus.valueOf(status),
        winnerId = winnerId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun List<ChallengeEntity>.toDomainChallenges(gson: Gson): List<Challenge> = map { it.toDomain(gson) }

// Friendship Mappers
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

fun List<FriendshipEntity>.toDomainFriendships(): List<Friendship> = map { it.toDomain() }
