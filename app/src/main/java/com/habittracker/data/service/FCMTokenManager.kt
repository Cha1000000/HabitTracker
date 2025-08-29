package com.habittracker.data.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.habittracker.BuildConfig
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
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

    // Guard to avoid concurrent refreshes
    private val isRefreshing = AtomicBoolean(false)

    /**
     * Получает текущий FCM токен и сохраняет его в базе данных
     */
    suspend fun refreshToken(): Result<String> {
        if (!isRefreshing.compareAndSet(false, true)) {
            // Already refreshing; avoid duplicate work
            Log.d(TAG, "Refresh already in progress, skipping")
            return Result.failure(IllegalStateException("FCM refresh in progress"))
        }
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("Пользователь не авторизован"))

            val token = FirebaseMessaging.getInstance().token.await()
            if (BuildConfig.DEBUG) {
                val masked = if (token.length > 8) "${token.take(4)}****${token.takeLast(4)}" else "****"
                Log.d(TAG, "Получен FCM токен (masked): $masked")
            }
            socialRepository.updateUserFcmToken(currentUserId, token)
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "FCM токен успешно сохранен для пользователя: $currentUserId")
            }
            
            Result.success(token)
        } catch (e: CancellationException) {
            // Coroutine was cancelled (e.g., scope disposed). Not a hard error.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Получение FCM токена отменено: ${e.message}")
            }
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения FCM токена", e)
            Result.failure(e)
        } finally {
            isRefreshing.set(false)
        }
    }

    /**
     * Раньше удаляли FCM токен при выходе. Это приводило к пропуску важных пушей для инвайтера,
     * если приглашение принималось, пока он был разлогинен. Теперь НЕ очищаем токен, чтобы
     * устройство могло продолжать получать адресные уведомления. Фильтрация по recipientUserId
     * на клиенте предотвратит показ неактуальных уведомлений не тому пользователю.
     */
    suspend fun clearToken(): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "clearToken(): skip deleting token to keep receiving critical notifications. user=$currentUserId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при clearToken()", e)
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
