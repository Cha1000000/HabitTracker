package com.habittracker.data.local.dao

import androidx.room.*
import com.habittracker.data.local.entity.UserAchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAchievementDao {

    @Query("SELECT * FROM user_achievements WHERE userId = :userId ORDER BY createdAt DESC")
    fun getUserAchievements(userId: String): Flow<List<UserAchievementEntity>>

    @Query("SELECT * FROM user_achievements WHERE userId = :userId")
    suspend fun getUserAchievementsSync(userId: String): List<UserAchievementEntity>

    @Query("SELECT * FROM user_achievements WHERE userId = :userId AND isUnlocked = 1 ORDER BY unlockedAt DESC")
    fun getUnlockedAchievements(userId: String): Flow<List<UserAchievementEntity>>

    @Query("SELECT * FROM user_achievements WHERE userId = :userId AND progress > 0 AND isUnlocked = 0 ORDER BY progress DESC")
    fun getInProgressAchievements(userId: String): Flow<List<UserAchievementEntity>>

    @Query("SELECT * FROM user_achievements WHERE userId = :userId AND achievementId = :achievementId")
    suspend fun getUserAchievement(userId: String, achievementId: String): UserAchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAchievement(userAchievement: UserAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAchievements(userAchievements: List<UserAchievementEntity>)

    @Update
    suspend fun updateUserAchievement(userAchievement: UserAchievementEntity)

    @Delete
    suspend fun deleteUserAchievement(userAchievement: UserAchievementEntity)

    @Query("DELETE FROM user_achievements WHERE userId = :userId")
    suspend fun deleteAllUserAchievements(userId: String)

    @Query("DELETE FROM user_achievements WHERE userId = :userId AND achievementId = :achievementId")
    suspend fun deleteUserAchievement(userId: String, achievementId: String)

    @Query("SELECT COUNT(*) FROM user_achievements WHERE userId = :userId AND isUnlocked = 1")
    suspend fun getUnlockedAchievementsCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM user_achievements WHERE userId = :userId")
    suspend fun getTotalAchievementsCount(userId: String): Int
}
