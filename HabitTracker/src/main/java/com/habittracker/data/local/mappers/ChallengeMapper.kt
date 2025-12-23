package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.ChallengeEntity
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeRules
import com.habittracker.domain.model.ChallengeStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Challenge Mappers
fun ChallengeEntity.toDomain(): Challenge = Challenge(
    id = id,
    habitId = habitId,
    title = title,
    description = description,
    createdBy = createdBy,
    participants = participants,
    acceptedParticipants = acceptedParticipants,
    startDate = startDate,
    endDate = endDate,
    rules = Json.decodeFromString<ChallengeRules>(rules),
    reward = reward,
    status = ChallengeStatus.valueOf(status),
    winnerId = winnerId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Challenge.toEntity(updatedAtMillis: Long = System.currentTimeMillis()): ChallengeEntity = ChallengeEntity(
    id = id,
    habitId = habitId,
    title = title,
    description = description,
    createdBy = createdBy,
    participants = participants,
    acceptedParticipants = acceptedParticipants,
    startDate = startDate,
    endDate = endDate,
    rules = Json.encodeToString(rules),
    reward = reward,
    status = status.name,
    winnerId = winnerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    updatedAtMillis = updatedAtMillis,
    deletedAtMillis = null
)
