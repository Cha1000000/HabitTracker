package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.habittracker.data.local.Converters
import kotlinx.datetime.LocalDate

@Entity(tableName = "challenges")
@TypeConverters(Converters::class)
data class ChallengeEntity(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val title: String,
    val description: String,
    val createdBy: String, // userId
    val participants: List<String>, // Stored as JSON - приглашённые участники
    val acceptedParticipants: List<String> = emptyList(), // Stored as JSON - принявшие приглашение
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rules: String, // JSON representation of ChallengeRules
    val reward: String,
    val status: String, // e.g., "ACTIVE", "COMPLETED", "CANCELLED"
    val winnerId: String?,
    val createdAt: LocalDate,
    val updatedAt: LocalDate,
    val updatedAtMillis: Long = System.currentTimeMillis(), // For LWW sync
    val deletedAtMillis: Long? = null
)
