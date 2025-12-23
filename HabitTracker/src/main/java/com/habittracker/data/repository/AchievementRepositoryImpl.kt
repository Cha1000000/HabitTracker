package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.AchievementDao
import com.habittracker.data.local.dao.UserAchievementDao
import com.habittracker.data.local.entity.UserAchievementEntity
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.domain.model.Achievement
import com.habittracker.domain.model.UserAchievement
import com.habittracker.domain.repository.AchievementRepository
import com.habittracker.domain.repository.AchievementStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.util.SyncPayloads
import kotlinx.datetime.Instant

@Singleton
class AchievementRepositoryImpl @Inject constructor(
    private val achievementDao: AchievementDao,
    private val userAchievementDao: UserAchievementDao,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val firestore: FirebaseFirestore
) : AchievementRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listenersByUserId = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

    private fun kickSync() {
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        repoScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }

    private fun startObservingRemoteUserAchievements(userId: String) {
        if (listenersByUserId.containsKey(userId)) return

        val reg = firestore.collection("user_achievements")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AchievementRepo", "user_achievements listener error: ${error.message}")
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                if (docs.isEmpty()) return@addSnapshotListener

                repoScope.launch {
                    for (doc in docs) {
                        try {
                            val uid = doc.getString("userId") ?: continue
                            val aid = doc.getString("achievementId") ?: continue
                            val progress = (doc.getLong("progress") ?: 0L).toInt()
                            val isUnlocked = doc.getBoolean("isUnlocked") ?: false
                            val createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
                            val unlockedAtMillis = doc.getLong("unlockedAtMillis")
                            val updatedAtMillis = doc.getLong("updatedAtMillis") ?: createdAtMillis
                            val tier = (doc.getLong("tier") ?: 1L).toInt()

                            val entityId = "${uid}_${aid}"
                            val hasPending = try {
                                syncManager.hasPendingOperations("user_achievement", entityId)
                            } catch (_: Exception) {
                                false
                            }
                            if (hasPending) continue

                            val local = userAchievementDao.getUserAchievement(uid, aid)
                            val localUpdatedAt = local?.updatedAtMillis ?: 0L
                            if (updatedAtMillis < localUpdatedAt) continue

                            val merged = UserAchievementEntity(
                                id = local?.id ?: 0,
                                userId = uid,
                                achievementId = aid,
                                progress = progress,
                                isUnlocked = isUnlocked,
                                unlockedAt = unlockedAtMillis,
                                createdAt = createdAtMillis,
                                updatedAtMillis = updatedAtMillis,
                                tier = tier,
                                isNotified = local?.isNotified ?: false
                            )
                            userAchievementDao.insertUserAchievement(merged)
                        } catch (e: Exception) {
                            Log.w("AchievementRepo", "user_achievements merge failed: ${e.message}")
                        }
                    }
                }
            }

        listenersByUserId[userId] = reg
    }

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
        }.onStart { startObservingRemoteUserAchievements(userId) }
    }

    override fun getUnlockedAchievements(userId: String): Flow<List<UserAchievement>> {
        return userAchievementDao.getUnlockedAchievements(userId).map { entities ->
            entities.map { it.toDomain() }
        }.onStart { startObservingRemoteUserAchievements(userId) }
    }

    override fun getInProgressAchievements(userId: String): Flow<List<UserAchievement>> {
        return userAchievementDao.getInProgressAchievements(userId).map { entities ->
            entities.map { it.toDomain() }
        }.onStart { startObservingRemoteUserAchievements(userId) }
    }

    override suspend fun unlockAchievement(userId: String, achievementId: String, progress: Int) {
        val now = Clock.System.now()
        val nowMillis = now.toEpochMilliseconds()
        val existing = userAchievementDao.getUserAchievement(userId, achievementId)
        val updatedAchievement = if (existing != null) {
            existing.copy(
                progress = progress,
                isUnlocked = true,
                unlockedAt = nowMillis,
                updatedAtMillis = nowMillis
            )
        } else {
            UserAchievementEntity(
                userId = userId,
                achievementId = achievementId,
                progress = progress,
                isUnlocked = true,
                unlockedAt = nowMillis,
                createdAt = nowMillis,
                updatedAtMillis = nowMillis
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
        val nowMillis = now.toEpochMilliseconds()
        val existing = userAchievementDao.getUserAchievement(userId, achievementId)
        val updatedAchievement = if (existing != null) {
            existing.copy(progress = progress, updatedAtMillis = nowMillis)
        } else {
            UserAchievementEntity(
                userId = userId,
                achievementId = achievementId,
                progress = progress,
                isUnlocked = false,
                unlockedAt = null,
                createdAt = nowMillis,
                updatedAtMillis = nowMillis
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

            kickSync()
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
