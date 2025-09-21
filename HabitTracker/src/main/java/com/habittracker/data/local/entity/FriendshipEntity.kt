package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.habittracker.data.local.Converters

@Entity(
    tableName = "friendships",
    primaryKeys = ["userId1", "userId2"],
    indices = [Index(value = ["userId1"]), Index(value = ["userId2"])],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId1"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId2"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(Converters::class)
data class FriendshipEntity(
    val userId1: String,
    val userId2: String,
    val status: String, // "PENDING", "ACCEPTED", "BLOCKED", "DECLINED"
    val requestedBy: String,
    val requestedAt: Long,
    val acceptedAt: Long?,
    val blockedBy: String?,
    val blockedAt: Long?,
    val updatedAt: Long
)
