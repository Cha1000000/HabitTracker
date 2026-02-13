package com.habittracker.data.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.messaging.FirebaseMessaging
import com.habittracker.BuildConfig
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Менеджер для управления FCM токенами
 */
@Singleton
class FCMTokenManager @Inject constructor(
    private val socialRepository: SocialRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "FCMTokenManager"
        private const val PREFS_NAME = "fcm_manager_prefs"
        private const val KEY_FCM_UNAVAILABLE = "fcm_unavailable_shown"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Guard to avoid concurrent refreshes
    private val isRefreshing = AtomicBoolean(false)

    /**
     * Проверяет, было ли показано сообщение о недоступности FCM
     */
    fun shouldShowFcmUnavailableMessage(): Boolean {
        return prefs.getBoolean(KEY_FCM_UNAVAILABLE, false)
    }

    /**
     * Отмечает, что сообщение о недоступности FCM было показано
     */
    fun markFcmUnavailableMessageShown() {
        prefs.edit().putBoolean(KEY_FCM_UNAVAILABLE, true).apply()
    }

    /**
     * Сбрасывает флаг показа сообщения (например, при выходе из аккаунта)
     */
    fun resetFcmUnavailableMessage() {
        prefs.edit().remove(KEY_FCM_UNAVAILABLE).apply()
    }

    /**
     * Проверяет, является ли ошибка FCM ожидаемой (устройство без GMS)
     */
    fun isFcmServiceUnavailableError(error: Throwable?): Boolean {
        if (error !is IOException) return false
        val message = error.message ?: ""
        return message.contains("SERVICE_NOT_AVAILABLE") || 
               message.contains("AUTHENTICATION_FAILED") ||
               message.contains("TOO_MANY_REGISTRATIONS") ||
               message.contains("FIS_AUTH_ERROR")
    }

    /**
     * Получает текущий FCM токен и сохраняет его в базе данных
     * @param forceRefresh если true, удаляет старый токен и получает новый (используется при явных проблемах)
     */
    suspend fun refreshToken(forceRefresh: Boolean = false): Result<String> {
        if (!isRefreshing.compareAndSet(false, true)) {
            // Already refreshing; avoid duplicate work
            Timber.tag(TAG).d("Refresh already in progress, skipping")
            return Result.failure(IllegalStateException("FCM refresh in progress"))
        }
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("Пользователь не авторизован"))

            if (BuildConfig.DEBUG) {
                Timber.tag(TAG).d("[fcm] Start token refresh for user=$currentUserId")
            }

            var result: Result<String>? = null
            var lastError: Exception? = null
            var backoff = 500L

            for (attempt in 1..3) {
                try {
                    // Если forceRefresh=true, удаляем старый токен чтобы получить новый
                    if (forceRefresh && attempt == 1) {
                        try {
                            FirebaseMessaging.getInstance().deleteToken().await()
                            if (BuildConfig.DEBUG) {
                                Timber.tag(TAG).d("[fcm] Deleted old token to force refresh")
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).w("[fcm] Failed to delete old token: ${e.message}")
                        }
                    }
                    
                    val token = FirebaseMessaging.getInstance().token.await()
                    if (BuildConfig.DEBUG) {
                        val masked = if (token.length > 8) "${token.take(4)}****${token.takeLast(4)}" else "****"
                        Timber.tag(TAG).d("[fcm] Got token (masked): $masked on attempt $attempt")
                    }

                    // Обновляем токен в базе данных (неотменяемо!)
                    if (BuildConfig.DEBUG) {
                        Timber.tag(TAG).d("[fcm] Updating token for user=$currentUserId")
                    }

                    // Используем NonCancellable, чтобы гарантировать сохранение токена
                    withContext(NonCancellable) {
                        socialRepository.updateUserFcmToken(currentUserId, token)
                    }
                    if (BuildConfig.DEBUG) {
                        Timber.tag(TAG).d("[fcm] Token saved for user=$currentUserId")
                    }
                    result = Result.success(token)
                    break
                } catch (e: CancellationException) {
                    if (BuildConfig.DEBUG) {
                        Timber.tag(TAG).i("FCM token refresh cancelled: ${e.message ?: "cancelled"}")
                    }
                    result = Result.failure(e)
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (BuildConfig.DEBUG) {
                        Timber.tag(TAG).d("[fcm] Attempt $attempt/3 failed: ${e.message}")
                    }
                    if (attempt < 3) {
                        try { delay(backoff) } catch (_: CancellationException) { /* propagate later */ }
                        backoff *= 2
                    }
                }
            }

            if (result != null) {
                result
            } else {
                // Определяем тип ошибки для правильного логирования
                val isServiceUnavailable = isFcmServiceUnavailableError(lastError)
                
                if (isServiceUnavailable) {
                    // Это ожидаемая ошибка для устройств без Google Play Services или с ограничениями
                    Timber.tag(TAG).w("FCM token unavailable: ${lastError?.message} (device may not support FCM)")
                    // Сохраняем флаг для показа сообщения пользователю
                    prefs.edit().putBoolean(KEY_FCM_UNAVAILABLE, true).apply()
                } else {
                    Timber.tag(TAG).e(lastError, "Ошибка получения FCM токена")
                }
                Result.failure(lastError ?: Exception("FCM token refresh failed"))
            }
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
    fun clearToken(): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
            if (BuildConfig.DEBUG) {
                Timber.tag(TAG).d("clearToken(): skip deleting token to keep receiving critical notifications. user=$currentUserId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка при clearToken()")
            Result.failure(e)
        }
    }

    /**
     * Подписывается на топик для групповых уведомлений
     */
    suspend fun subscribeToTopic(topic: String): Result<Unit> {
        return try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            Timber.tag(TAG).d("Подписка на топик: $topic")
            Result.success(Unit)
        } catch (e: IOException) {
            // Ожидаемая ошибка для устройств без Google Play Services
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("SERVICE_NOT_AVAILABLE") || 
                errorMessage.contains("AUTHENTICATION_FAILED") ||
                errorMessage.contains("FIS_AUTH_ERROR")) {
                Timber.tag(TAG).w("Cannot subscribe to topic '$topic': FCM service unavailable (device may not support FCM)")
            } else {
                Timber.tag(TAG).e(e, "Ошибка подписки на топик: $topic")
            }
            Result.failure(e)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка подписки на топик: $topic")
            Result.failure(e)
        }
    }

    /**
     * Отписывается от топика
     */
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        return try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
            Timber.tag(TAG).d("Отписка от топика: $topic")
            Result.success(Unit)
        } catch (e: IOException) {
            // Ожидаемая ошибка для устройств без Google Play Services
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("SERVICE_NOT_AVAILABLE") || 
                errorMessage.contains("AUTHENTICATION_FAILED") ||
                errorMessage.contains("FIS_AUTH_ERROR")) {
                Timber.tag(TAG).w("Cannot unsubscribe from topic '$topic': FCM service unavailable (device may not support FCM)")
            } else {
                Timber.tag(TAG).e(e, "Ошибка отписки от топика: $topic")
            }
            Result.failure(e)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка отписки от топика: $topic")
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
            Timber.tag(TAG).d("Подписка на ${habitIds.size} привычек для пользователя: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка подписки на привычки пользователя")
            Result.failure(e)
        }
    }
}
