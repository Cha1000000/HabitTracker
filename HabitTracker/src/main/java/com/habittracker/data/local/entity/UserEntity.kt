package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val avatarThumbnailUrl: String? = null,
    val level: Int = 1,
    val points: Int = 0,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val friendsCount: Int = 0,
    val fcmToken: String? = null,
    val createdAt: Long, // timestamp в миллисекундах
    val lastActiveAt: Long, // timestamp в миллисекундах
    val timezone: String
)
