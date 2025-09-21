package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.User
import com.habittracker.domain.model.UserLevelInfo
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Use case для обновления уровня пользователя на основе накопленных очков
 */
class UpdateUserLevelUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val socialRepository: SocialRepository
) {

    companion object {
        // Формула расчета очков для уровня: level * 1000
        // Уровень 1: 0-999 очков
        // Уровень 2: 1000-1999 очков
        // Уровень 3: 2000-2999 очков и т.д.
        const val POINTS_PER_LEVEL = 1000
        
        // Максимальный уровень
        const val MAX_LEVEL = 100
    }

    /**
     * Обновить уровень пользователя на основе текущих очков
     */
    suspend fun updateUserLevel(userId: String): LevelUpdateResult {
        val user = userRepository.getUserById(userId)
            ?: return LevelUpdateResult.UserNotFound

        val newLevel = calculateLevelFromPoints(user.points)
        val oldLevel = user.level

        if (newLevel != oldLevel) {
            val updatedUser = user.copy(level = newLevel)
            userRepository.updateUser(updatedUser)

            // Отправляем уведомление о повышении уровня
            if (newLevel > oldLevel) {
                sendLevelUpNotification(user, newLevel)
            }

            return LevelUpdateResult.Success(
                oldLevel = oldLevel,
                newLevel = newLevel,
                leveledUp = newLevel > oldLevel
            )
        }

        return LevelUpdateResult.NoChange(user.level)
    }

    /**
     * Добавить очки пользователю и обновить уровень
     */
    suspend fun addPointsAndUpdateLevel(
        userId: String, 
        points: Int,
        reason: String = ""
    ): PointsUpdateResult {
        val user = userRepository.getUserById(userId)
            ?: return PointsUpdateResult.UserNotFound

        val newPoints = (user.points + points).coerceAtLeast(0)
        val newLevel = calculateLevelFromPoints(newPoints)
        val oldLevel = user.level

        val updatedUser = user.copy(
            points = newPoints,
            level = newLevel
        )
        
        userRepository.updateUser(updatedUser)

        // Отправляем уведомление о повышении уровня
        if (newLevel > oldLevel) {
            sendLevelUpNotification(user, newLevel)
        }

        return PointsUpdateResult.Success(
            oldPoints = user.points,
            newPoints = newPoints,
            pointsAdded = points,
            oldLevel = oldLevel,
            newLevel = newLevel,
            leveledUp = newLevel > oldLevel,
            reason = reason
        )
    }

    /**
     * Рассчитать уровень на основе очков
     */
    fun calculateLevelFromPoints(points: Int): Int {
        val calculatedLevel = (points / POINTS_PER_LEVEL) + 1
        return calculatedLevel.coerceIn(1, MAX_LEVEL)
    }

    /**
     * Рассчитать очки, необходимые для следующего уровня
     */
    fun getPointsForNextLevel(currentLevel: Int): Int {
        return if (currentLevel >= MAX_LEVEL) {
            0 // Максимальный уровень достигнут
        } else {
            currentLevel * POINTS_PER_LEVEL
        }
    }

    /**
     * Получить информацию об уровне пользователя
     */
    suspend fun getUserLevelInfo(userId: String): UserLevelInfo? {
        val user = userRepository.getUserById(userId) ?: return null

        val currentLevel = user.level
        val currentPoints = user.points
        val pointsForNextLevel = getPointsForNextLevel(currentLevel)
        val pointsToNextLevel = pointsForNextLevel - currentPoints
        val progress = calculateProgressToNextLevel(currentPoints, currentLevel)
        val levelTitle = getLevelTitle(currentLevel)
        val isMaxLevel = currentLevel >= MAX_LEVEL

        return UserLevelInfo(
            currentLevel = currentLevel,
            currentPoints = currentPoints,
            pointsForNextLevel = pointsForNextLevel,
            pointsToNextLevel = pointsToNextLevel,
            progress = progress,
            levelTitle = levelTitle,
            isMaxLevel = isMaxLevel
        )
    }

    /**
     * Рассчитать прогресс до следующего уровня (0.0 - 1.0)
     */
    private fun calculateProgressToNextLevel(currentPoints: Int, currentLevel: Int): Float {
        if (currentLevel >= MAX_LEVEL) return 1f

        val pointsForCurrentLevel: Int = (currentLevel - 1) * POINTS_PER_LEVEL
        val pointsForNextLevel: Int = currentLevel * POINTS_PER_LEVEL
        val currentLevelPoints: Int = currentPoints - pointsForCurrentLevel
        val pointsNeeded: Int = pointsForNextLevel - pointsForCurrentLevel

        return if (pointsNeeded > 0) {
            (currentLevelPoints.toFloat() / pointsNeeded.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    /**
     * Отправить уведомление о повышении уровня
     */
    private suspend fun sendLevelUpNotification(user: User, newLevel: Int) {
        try {
            user.fcmToken?.let { token ->
                val title = "🎉 Поздравляем!"
                val body = "Вы достигли ${newLevel} уровня! ${getLevelTitle(newLevel)}"
                val data = mapOf(
                    "type" to "level_up",
                    "level" to newLevel.toString(),
                    "userId" to user.id
                )
                
                socialRepository.sendPushNotification(token, title, body, data)
            }
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем основной процесс
            println("Failed to send level up notification: ${e.message}")
        }
    }

    fun getLevelTitle(level: Int): String {
        return when (level) {
            1 -> "Новичок"
            2 -> "Ученик"
            3 -> "Практик"
            4 -> "Мастер"
            5 -> "Эксперт"
            else -> "Легенда"
        }
    }
}

/**
 * Результат обновления уровня
 */
sealed class LevelUpdateResult {
    data class Success(
        val oldLevel: Int,
        val newLevel: Int,
        val leveledUp: Boolean
    ) : LevelUpdateResult()
    
    data class NoChange(val currentLevel: Int) : LevelUpdateResult()
    object UserNotFound : LevelUpdateResult()
}

/**
 * Результат обновления очков
 */
sealed class PointsUpdateResult {
    data class Success(
        val oldPoints: Int,
        val newPoints: Int,
        val pointsAdded: Int,
        val oldLevel: Int,
        val newLevel: Int,
        val leveledUp: Boolean,
        val reason: String
    ) : PointsUpdateResult()
    
    object UserNotFound : PointsUpdateResult()
}
