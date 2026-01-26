package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "habit_invitations",
    indices = [
        Index(value = ["habitId"]),
        Index(value = ["fromUserId"]),
        Index(value = ["toUserId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class HabitInvitationEntity(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val habitTitle: String,
    val fromUserId: String,
    val fromUserName: String,
    val toUserId: String,
    val message: String?,
    val status: String, // HabitInvitationStatus as String
    val createdAt: LocalDateTime,
    val respondedAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deletedAtMillis: Long? = null
)
