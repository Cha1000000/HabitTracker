package com.habittracker.data.local.dao

import androidx.room.*
import com.habittracker.data.local.entity.AchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE isActive = 1 ORDER BY category, title")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE isActive = 1")
    suspend fun getAllAchievementsSync(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE category = :category AND isActive = 1 ORDER BY title")
    fun getAchievementsByCategory(category: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE id = :achievementId")
    suspend fun getAchievementById(achievementId: String): AchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: AchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)

    @Delete
    suspend fun deleteAchievement(achievement: AchievementEntity)

    @Query("DELETE FROM achievements WHERE id = :achievementId")
    suspend fun deleteAchievementById(achievementId: String)
}
