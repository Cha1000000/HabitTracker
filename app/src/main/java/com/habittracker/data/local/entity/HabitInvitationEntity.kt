package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "habit_invitations",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
    val createdAt: Long, // Instant as epochSeconds
    val respondedAt: Long?, // Instant as epochSeconds
    val expiresAt: Long? // Instant as epochSeconds
)
