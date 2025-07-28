package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val level: Int = 1,
    val points: Int = 0,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val createdAt: Instant,
    val lastActiveAt: Instant,
    val timezone: String
)
