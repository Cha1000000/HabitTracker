package com.habittracker.domain.usecase.gamification

import android.util.Log
import com.habittracker.domain.model.gamification.GamificationConstants
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Use case для обновления очков и уровня пользователя на основе игровых событий.
 */
open class UpdateUserPointsUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val socialRepository: SocialRepository
) {

    /**
     * @param userId ID пользователя
     * @param event Игровое событие, вызвавшее начисление или вычитание очков
     */
    open suspend operator fun invoke(userId: String, event: GamificationEvent): Result<Unit> {
        return try {
            val user = userRepository.getUserById(userId) ?: return Result.failure(Exception("User with id $userId not found"))

            val pointsChange = when (event) {
                is GamificationEvent.HabitCompleted -> GamificationConstants.POINTS_HABIT_COMPLETION
                is GamificationEvent.HabitUncompleted -> -GamificationConstants.POINTS_HABIT_COMPLETION // Вычитаем очки
                is GamificationEvent.ChallengeJoined -> GamificationConstants.POINTS_CHALLENGE_PARTICIPATION
                is GamificationEvent.ChallengeWon -> GamificationConstants.POINTS_CHALLENGE_VICTORY
                is GamificationEvent.NewFriendAdded -> GamificationConstants.POINTS_NEW_FRIEND
                is GamificationEvent.FriendInvited -> GamificationConstants.POINTS_FRIEND_INVITE
                is GamificationEvent.GroupHabitCreated -> GamificationConstants.POINTS_GROUP_HABIT_CREATION
                is GamificationEvent.PerfectDay -> GamificationConstants.POINTS_PERFECT_DAY
                is GamificationEvent.StreakBonus -> when (event.days) {
                    3 -> GamificationConstants.POINTS_STREAK_3_DAYS
                    7 -> GamificationConstants.POINTS_STREAK_7_DAYS
                    30 -> GamificationConstants.POINTS_STREAK_30_DAYS
                    else -> 0
                }
            }

            if (pointsChange != 0) {
                val newPoints = (user.points + pointsChange).coerceAtLeast(0) // Не даём очкам уйти в минус
                val currentLevel = user.level
                val newLevel = GamificationConstants.getLevelFromPoints(newPoints)

                val updatedUser = user.copy(
                    points = newPoints,
                    level = newLevel
                )

                userRepository.updateUser(updatedUser)

                if (newLevel > currentLevel) {
                    sendLevelUpNotification(user, currentLevel, newLevel)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            // Логирование ошибки
            Result.failure(e)
        }
    }
    
    /**
     * Отправляет уведомление о повышении уровня
     */
    private suspend fun sendLevelUpNotification(
        user: com.habittracker.domain.model.User,
        oldLevel: Int,
        newLevel: Int
    ) {
        try {
            val levelTitle = getLevelTitle(newLevel)
            val title = "🎉 Поздравляем с повышением!"
            val body = "Вы достигли $newLevel уровня! $levelTitle"
            
            Log.d("UpdateUserPoints", "Level up: ${user.name} $oldLevel → $newLevel ($levelTitle)")
            
            // Отправляем push-уведомление, если есть FCM токен
            user.fcmToken?.takeIf { it.isNotBlank() }?.let { token ->
                val data = mapOf(
                    "type" to "level_up",
                    "level" to newLevel.toString(),
                    "oldLevel" to oldLevel.toString(),
                    "levelTitle" to levelTitle,
                    "userId" to user.id,
                    "screen" to "stats"
                )
                
                socialRepository.sendPushNotification(token, title, body, data)
                Log.d("UpdateUserPoints", "Push notification sent for level up")
            } ?: Log.d("UpdateUserPoints", "No FCM token, skipping push notification")
            
        } catch (e: Exception) {
            // Не прерываем основной процесс при ошибке отправки уведомления
            Log.w("UpdateUserPoints", "Failed to send level up notification: ${e.message}", e)
        }
    }
    
    /**
     * Возвращает название уровня
     */
    private fun getLevelTitle(level: Int): String {
        return when (level) {
            in 1..1 -> "Новичок"
            in 2..3 -> "Ученик"
            in 4..5 -> "Практик"
            in 6..10 -> "Опытный"
            in 11..20 -> "Мастер"
            in 21..30 -> "Эксперт"
            in 31..50 -> "Гуру"
            in 51..75 -> "Чемпион"
            in 76..99 -> "Легенда"
            else -> "Бог привычек"
        }
    }
}
