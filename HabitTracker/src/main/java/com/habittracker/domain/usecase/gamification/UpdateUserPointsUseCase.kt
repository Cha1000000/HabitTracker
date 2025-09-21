package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.gamification.GamificationConstants
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Use case для обновления очков и уровня пользователя на основе игровых событий.
 */
open class UpdateUserPointsUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    /**
     * @param userId ID пользователя
     * @param event Игровое событие, вызвавшее начисление очков
     */
    open suspend operator fun invoke(userId: String, event: GamificationEvent): Result<Unit> {
        return try {
            val user = userRepository.getUserById(userId) ?: return Result.failure(Exception("User with id $userId not found"))

            val pointsToAdd = when (event) {
                is GamificationEvent.HabitCompleted -> GamificationConstants.POINTS_HABIT_COMPLETION
                is GamificationEvent.ChallengeJoined -> GamificationConstants.POINTS_CHALLENGE_PARTICIPATION
                is GamificationEvent.ChallengeWon -> GamificationConstants.POINTS_CHALLENGE_VICTORY
                is GamificationEvent.NewFriendAdded -> GamificationConstants.POINTS_NEW_FRIEND
                is GamificationEvent.PerfectDay -> GamificationConstants.POINTS_PERFECT_DAY
                is GamificationEvent.StreakBonus -> when (event.days) {
                    3 -> GamificationConstants.POINTS_STREAK_3_DAYS
                    7 -> GamificationConstants.POINTS_STREAK_7_DAYS
                    30 -> GamificationConstants.POINTS_STREAK_30_DAYS
                    else -> 0
                }
            }

            if (pointsToAdd > 0) {
                val newPoints = user.points + pointsToAdd
                val currentLevel = user.level
                val newLevel = GamificationConstants.getLevelFromPoints(newPoints)

                val updatedUser = user.copy(
                    points = newPoints,
                    level = newLevel
                )

                userRepository.updateUser(updatedUser)

                if (newLevel > currentLevel) {
                    // TODO: Отправить уведомление о повышении уровня (локальное или push)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            // Логирование ошибки
            Result.failure(e)
        }
    }
}
