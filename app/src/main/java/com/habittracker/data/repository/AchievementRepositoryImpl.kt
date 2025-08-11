package com.habittracker.data.repository

import com.habittracker.data.local.dao.AchievementDao
import com.habittracker.data.local.dao.UserAchievementDao
import com.habittracker.data.local.entity.UserAchievementEntity
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.domain.model.Achievement
import com.habittracker.domain.model.UserAchievement
import com.habittracker.domain.repository.AchievementRepository
import com.habittracker.domain.repository.AchievementStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementRepositoryImpl @Inject constructor(
    private val achievementDao: AchievementDao,
    private val userAchievementDao: UserAchievementDao
) : AchievementRepository {

    override fun getAllAchievements(): Flow<List<Achievement>> {
        return achievementDao.getAllAchievements().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAchievementsByCategory(category: String): Flow<List<Achievement>> {
        return achievementDao.getAchievementsByCategory(category).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAchievementById(achievementId: String): Achievement? {
        return achievementDao.getAchievementById(achievementId)?.toDomain()
    }

    override fun getUserAchievements(userId: String): Flow<List<UserAchievement>> {
        return userAchievementDao.getUserAchievements(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnlockedAchievements(userId: String): Flow<List<UserAchievement>> {
        return userAchievementDao.getUnlockedAchievements(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getInProgressAchievements(userId: String): Flow<List<UserAchievement>> {
        return userAchievementDao.getInProgressAchievements(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun unlockAchievement(userId: String, achievementId: String, progress: Int) {
        val existing = userAchievementDao.getUserAchievement(userId, achievementId)
        if (existing != null) {
            userAchievementDao.updateUserAchievement(
                existing.copy(
                    progress = progress,
                    isUnlocked = true,
                    unlockedAt = Clock.System.now()
                )
            )
        } else {
            userAchievementDao.insertUserAchievement(
                UserAchievementEntity(
                    userId = userId,
                    achievementId = achievementId,
                    progress = progress,
                    isUnlocked = true,
                    unlockedAt = Clock.System.now(),
                    createdAt = Clock.System.now()
                )
            )
        }
    }

    override suspend fun updateAchievementProgress(userId: String, achievementId: String, progress: Int) {
        val existing = userAchievementDao.getUserAchievement(userId, achievementId)
        if (existing != null) {
            userAchievementDao.updateUserAchievement(
                existing.copy(progress = progress)
            )
        } else {
            userAchievementDao.insertUserAchievement(
                UserAchievementEntity(
                    userId = userId,
                    achievementId = achievementId,
                    progress = progress,
                    isUnlocked = false,
                    unlockedAt = null,
                    createdAt = Clock.System.now()
                )
            )
        }
    }

    override suspend fun getAchievementProgress(userId: String, achievementId: String): UserAchievement? {
        return userAchievementDao.getUserAchievement(userId, achievementId)?.toDomain()
    }

    override suspend fun initializeUserAchievements(userId: String) {
        val allAchievements = achievementDao.getAllAchievementsSync()
        val userAchievements = allAchievements.map { achievement ->
            UserAchievementEntity(
                userId = userId,
                achievementId = achievement.id,
                progress = 0,
                isUnlocked = false,
                unlockedAt = null,
                createdAt = Clock.System.now()
            )
        }
        userAchievementDao.insertUserAchievements(userAchievements)
    }

    override suspend fun getUserAchievementStats(userId: String): AchievementStats {
        val allUserAchievements = userAchievementDao.getUserAchievementsSync(userId)
        val totalAchievements = allUserAchievements.size
        val unlockedAchievements = allUserAchievements.count { it.isUnlocked }
        val inProgressAchievements = allUserAchievements.count { it.progress > 0 && !it.isUnlocked }
        val completionPercentage = if (totalAchievements > 0) {
            (unlockedAchievements.toFloat() / totalAchievements) * 100f
        } else 0f

        return AchievementStats(
            totalAchievements = totalAchievements,
            unlockedAchievements = unlockedAchievements,
            inProgressAchievements = inProgressAchievements,
            completionPercentage = completionPercentage
        )
    }
}
