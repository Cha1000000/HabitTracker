package com.habittracker.data.local.mappers

import com.habittracker.data.local.entity.AchievementEntity
import com.habittracker.data.local.entity.UserAchievementEntity
import com.habittracker.domain.model.Achievement
import com.habittracker.domain.model.AchievementCategory
import com.habittracker.domain.model.AchievementCondition
import com.habittracker.domain.model.AchievementMetric
import com.habittracker.domain.model.AchievementTimeframe
import com.habittracker.domain.model.AchievementType
import com.habittracker.domain.model.UserAchievement
import kotlinx.datetime.Instant

// Achievement mappers
fun AchievementEntity.toDomain(): Achievement {
    return Achievement(
        id = id,
        title = title,
        description = description,
        icon = icon,
        category = AchievementCategory.valueOf(category),
        type = AchievementType.valueOf(type),
        condition = AchievementCondition(
            metric = AchievementMetric.valueOf(conditionMetric),
            targetValue = conditionTargetValue,
            timeframe = conditionTimeframe?.let { AchievementTimeframe.valueOf(it) } ?: AchievementTimeframe.ALL_TIME
        ),
        pointsReward = pointsReward,
        isSecret = isSecret,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(createdAt)
    )
}

fun Achievement.toEntity(): AchievementEntity {
    return AchievementEntity(
        id = id,
        title = title,
        description = description,
        icon = icon,
        category = category.name,
        type = type.name,
        conditionMetric = condition.metric.name,
        conditionTargetValue = condition.targetValue,
        conditionTimeframe = condition.timeframe?.name,
        pointsReward = pointsReward,
        isSecret = isSecret,
        isActive = isActive,
        createdAt = createdAt.toEpochMilliseconds()
    )
}

// UserAchievement mappers
fun UserAchievementEntity.toDomain(): UserAchievement {
    return UserAchievement(
        userId = userId,
        achievementId = achievementId,
        progress = progress,
        isUnlocked = isUnlocked,
        unlockedAt = unlockedAt?.let { Instant.fromEpochMilliseconds(it) },
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
        tier = tier,
        isNotified = isNotified
    )
}

fun UserAchievement.toEntity(): UserAchievementEntity {
    return UserAchievementEntity(
        // id генерируется базой данных, поэтому мы его не передаем
        userId = userId,
        achievementId = achievementId,
        progress = progress,
        isUnlocked = isUnlocked,
        unlockedAt = unlockedAt?.toEpochMilliseconds(),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAtMillis = updatedAt.toEpochMilliseconds(),
        tier = tier,
        isNotified = isNotified
    )
}
