package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val status: String, // храним как String для Room
    val message: String? = null,
    val createdAt: Instant,
    val respondedAt: Instant? = null,
    val updatedAt: Instant
)
