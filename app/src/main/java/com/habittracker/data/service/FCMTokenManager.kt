package com.habittracker.data.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер для управления FCM токенами
 */
@Singleton
class FCMTokenManager @Inject constructor(
    private val socialRepository: SocialRepository,
    private val authRepository: AuthRepository
) {
    
    companion object {
        private const val TAG = "FCMTokenManager"
    }

    /**
     * Получает текущий FCM токен и сохраняет его в базе данных
     */
    suspend fun refreshToken(): Result<String> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("Пользователь не авторизован"))

            // Получаем токен от Firebase
            val token = FirebaseMessaging.getInstance().token.await()
            
            Log.d(TAG, "Получен FCM токен: $token")
            
            // Сохраняем токен в базе данных
            socialRepository.updateUserFcmToken(currentUserId, token)
            
            Log.d(TAG, "FCM токен успешно сохранен для пользователя: $currentUserId")
            
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения FCM токена", e)
            Result.failure(e)
        }
    }

    /**
     * Удаляет FCM токен при выходе пользователя
     */
    suspend fun clearToken(): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("Пользователь не авторизован"))

            // Удаляем токен из Firebase
            FirebaseMessaging.getInstance().deleteToken().await()
            
            // Очищаем токен в базе данных
            socialRepository.updateUserFcmToken(currentUserId, "")
            
            Log.d(TAG, "FCM токен успешно удален для пользователя: $currentUserId")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления FCM токена", e)
            Result.failure(e)
        }
    }

    /**
     * Подписывается на топик для групповых уведомлений
     */
    suspend fun subscribeToTopic(topic: String): Result<Unit> {
        return try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            Log.d(TAG, "Подписка на топик: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подписки на топик: $topic", e)
            Result.failure(e)
        }
    }

    /**
     * Отписывается от топика
     */
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        return try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
            Log.d(TAG, "Отписка от топика: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отписки от топика: $topic", e)
            Result.failure(e)
        }
    }

    /**
     * Подписывается на топики групповых привычек пользователя
     */
    suspend fun subscribeToUserHabits(userId: String, habitIds: List<String>): Result<Unit> {
        return try {
            habitIds.forEach { habitId ->
                val topic = "habit_$habitId"
                subscribeToTopic(topic)
            }
            Log.d(TAG, "Подписка на ${habitIds.size} привычек для пользователя: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подписки на привычки пользователя", e)
            Result.failure(e)
        }
    }
}
