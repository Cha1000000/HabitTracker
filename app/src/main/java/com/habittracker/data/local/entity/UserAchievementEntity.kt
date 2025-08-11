package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "user_achievements",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AchievementEntity::class,
            parentColumns = ["id"],
            childColumns = ["achievementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["achievementId"]),
        Index(value = ["userId", "achievementId"], unique = true)
    ]
)
data class UserAchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val achievementId: String,
    val progress: Int = 0, // текущий прогресс (0-100 или абсолютное значение)
    val isUnlocked: Boolean = false,
    val unlockedAt: Instant? = null,
    val createdAt: Instant,
    val tier: Int = 1,
    val isNotified: Boolean = false
)
