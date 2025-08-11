package com.habittracker.domain.repository

import com.habittracker.domain.model.Achievement
import com.habittracker.domain.model.UserAchievement
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для работы с достижениями
 */
interface AchievementRepository {
    
    /**
     * Получает все доступные достижения
     */
    fun getAllAchievements(): Flow<List<Achievement>>
    
    /**
     * Получает достижения по категории
     */
    fun getAchievementsByCategory(category: String): Flow<List<Achievement>>
    
    /**
     * Получает достижение по ID
     */
    suspend fun getAchievementById(achievementId: String): Achievement?
    
    /**
     * Получает достижения пользователя
     */
    fun getUserAchievements(userId: String): Flow<List<UserAchievement>>
    
    /**
     * Получает разблокированные достижения пользователя
     */
    fun getUnlockedAchievements(userId: String): Flow<List<UserAchievement>>
    
    /**
     * Получает достижения в процессе выполнения
     */
    fun getInProgressAchievements(userId: String): Flow<List<UserAchievement>>
    
    /**
     * Разблокирует достижение для пользователя
     */
    suspend fun unlockAchievement(userId: String, achievementId: String, progress: Int = 100)
    
    /**
     * Обновляет прогресс достижения
     */
    suspend fun updateAchievementProgress(userId: String, achievementId: String, progress: Int)
    
    /**
     * Получает прогресс конкретного достижения
     */
    suspend fun getAchievementProgress(userId: String, achievementId: String): UserAchievement?
    
    /**
     * Инициализирует достижения для нового пользователя
     */
    suspend fun initializeUserAchievements(userId: String)
    
    /**
     * Получает статистику достижений пользователя
     */
    suspend fun getUserAchievementStats(userId: String): AchievementStats
}

/**
 * Статистика достижений пользователя
 */
data class AchievementStats(
    val totalAchievements: Int,
    val unlockedAchievements: Int,
    val inProgressAchievements: Int,
    val completionPercentage: Float
)
