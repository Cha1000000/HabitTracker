package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "🏆",
    val category: String, // AchievementCategory as String
    val type: String, // AchievementType as String
    val conditionMetric: String, // AchievementMetric as String
    val conditionTargetValue: Int,
    val conditionTimeframe: String?, // AchievementTimeframe as String?
    val pointsReward: Int,
    val isSecret: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Instant
)
