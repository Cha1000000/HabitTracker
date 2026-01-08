package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.gamification.GamificationConstants
import com.habittracker.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Use case для обновления игровой статистики пользователя (очки, уровень).
 */
open class UpdateUserStatsUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    /**
     * Добавляет очки пользователю и асинхронно обновляет его уровень.
     */
    open suspend operator fun invoke(userId: String, pointsToAdd: Int) {
        val user = userRepository.getUserById(userId) ?: return

        val newPoints = user.points + pointsToAdd
        val newLevel = GamificationConstants.getLevelFromPoints(newPoints)

        val updatedUser = user.copy(
            points = newPoints,
            level = newLevel
        )

        userRepository.updateUser(updatedUser)
    }
}
