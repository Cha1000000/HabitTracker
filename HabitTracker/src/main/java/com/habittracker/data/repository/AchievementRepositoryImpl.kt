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
import com.habittracker.data.sync.SyncManager
import com.habittracker.domain.util.SyncPayloads
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

@Singleton
class AchievementRepositoryImpl @Inject constructor(
    private val achievementDao: AchievementDao,
    private val userAchievementDao: UserAchievementDao,
    private val syncManager: SyncManager
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
        val now = Clock.System.now()
        val existing = userAchievementDao.getUserAchievement(userId, achievementId)
        val updatedAchievement = if (existing != null) {
            existing.copy(
                progress = progress,
                isUnlocked = true,
                unlockedAt = now.toEpochMilliseconds()
            )
        } else {
            UserAchievementEntity(
                userId = userId,
                achievementId = achievementId,
                progress = progress,
                isUnlocked = true,
                unlockedAt = now.toEpochMilliseconds(),
                createdAt = now.toEpochMilliseconds()
            )
        }

        if (existing != null) {
            userAchievementDao.updateUserAchievement(updatedAchievement)
        } else {
            userAchievementDao.insertUserAchievement(updatedAchievement)
        }

        // Sync with Firestore
        enqueueSync(updatedAchievement.toDomain())
    }

    override suspend fun updateAchievementProgress(userId: String, achievementId: String, progress: Int) {
        val now = Clock.System.now()
        val existing = userAchievementDao.getUserAchievement(userId, achievementId)
        val updatedAchievement = if (existing != null) {
            existing.copy(progress = progress)
        } else {
            UserAchievementEntity(
                userId = userId,
                achievementId = achievementId,
                progress = progress,
                isUnlocked = false,
                unlockedAt = null,
                createdAt = now.toEpochMilliseconds()
            )
        }

        if (existing != null) {
            userAchievementDao.updateUserAchievement(updatedAchievement)
        } else {
            userAchievementDao.insertUserAchievement(updatedAchievement)
        }

        // Sync with Firestore
        enqueueSync(updatedAchievement.toDomain())
    }

    private suspend fun enqueueSync(userAchievement: UserAchievement) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val payload = SyncPayloads.userAchievementToJson(userAchievement, now)
            val docId = "${userAchievement.userId}_${userAchievement.achievementId}"
            
            syncManager.enqueueUpsert(
                entityType = "user_achievement",
                entityId = docId,
                payloadJson = payload,
                updatedAt = now
            )
        } catch (e: Exception) {
            // Log error but don't crash, local data is already saved
            android.util.Log.e("AchievementRepo", "Failed to enqueue sync", e)
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
                createdAt = Clock.System.now().toEpochMilliseconds()
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
