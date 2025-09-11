package com.habittracker.data.remote.dto.auth

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String?,
    val user: AuthUserDto
)

data class AuthUserDto(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val avatarThumbnailUrl: String?,
    val level: Int,
    val points: Int,
    val streak: Int,
    val longestStreak: Int,
    val friendsCount: Int,
    val timezone: String?,
    val createdAt: Long,
    val lastActiveAt: Long
)
