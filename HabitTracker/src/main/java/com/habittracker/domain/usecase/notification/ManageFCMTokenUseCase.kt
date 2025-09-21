package com.habittracker.domain.usecase.notification

import com.habittracker.data.service.FCMTokenManager
import com.habittracker.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Use Case для управления FCM токенами пользователя
 */
class ManageFCMTokenUseCase @Inject constructor(
    private val fcmTokenManager: FCMTokenManager,
    private val authRepository: AuthRepository
) {

    /**
     * Инициализирует FCM токен для текущего пользователя
     * Вызывается при входе в приложение
     */
    suspend fun initializeFCMToken(): Result<String> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("Пользователь не авторизован"))

            fcmTokenManager.refreshToken()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Обновляет FCM токен
     * Вызывается при получении нового токена
     */
    suspend fun refreshFCMToken(): Result<String> {
        return fcmTokenManager.refreshToken()
    }

    /**
     * Очищает FCM токен при выходе пользователя
     */
    suspend fun clearFCMToken(): Result<Unit> {
        return fcmTokenManager.clearToken()
    }

    /**
     * Подписывается на уведомления групповых привычек
     */
    suspend fun subscribeToGroupHabits(habitIds: List<String>): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("Пользователь не авторизован"))

            fcmTokenManager.subscribeToUserHabits(currentUserId, habitIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отписывается от уведомлений конкретной привычки
     */
    suspend fun unsubscribeFromHabit(habitId: String): Result<Unit> {
        return fcmTokenManager.unsubscribeFromTopic("habit_$habitId")
    }
}
