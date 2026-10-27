package com.habittracker.data.service

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.edit
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
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

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
        private const val KEY_FCM_UNAVAILABLE_DETECTED = "fcm_unavailable_detected"
        private const val KEY_FCM_UNAVAILABLE_DIALOG_SHOWN = "fcm_unavailable_dialog_shown"
        private const val KEY_SAVED_TOKEN = "saved_token"
        private const val KEY_SAVED_FOR_USER = "saved_token_user"
        private const val KEY_SAVED_AT = "saved_token_at"

        /** Раз в неделю пишем токен заново, даже если он не менялся: вдруг его убрали из профиля. */
        private const val TOKEN_RESAVE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Guard to avoid concurrent refreshes
    private val isRefreshing = AtomicBoolean(false)

    /**
     * Нужно ли показать сообщение о недоступности FCM.
     * Важно: показываем только 1 раз и только если недоступность была именно обнаружена,
     * а не из-за транзиентных сетевых ошибок.
     */
    fun shouldShowFcmUnavailableMessage(): Boolean {
        val unavailableDetected = prefs.getBoolean(KEY_FCM_UNAVAILABLE_DETECTED, false)
        val dialogShown = prefs.getBoolean(KEY_FCM_UNAVAILABLE_DIALOG_SHOWN, false)
        return unavailableDetected && !dialogShown
    }

    /**
     * Отмечает, что сообщение о недоступности FCM было показано
     */
    fun markFcmUnavailableMessageShown() {
        prefs.edit { putBoolean(KEY_FCM_UNAVAILABLE_DIALOG_SHOWN, true) }
    }

    /**
     * Сбрасывает флаг показа сообщения (например, при выходе из аккаунта)
     */
    fun resetFcmUnavailableMessage() {
        prefs.edit {
            remove(KEY_FCM_UNAVAILABLE_DETECTED)
                .remove(KEY_FCM_UNAVAILABLE_DIALOG_SHOWN)
        }
    }

    private fun hasGooglePlayServices(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Проверяет, является ли ошибка FCM ожидаемой (устройство без GMS)
     */
    fun isFcmServiceUnavailableError(error: Throwable?): Boolean {
        if (error !is IOException) return false
        val message = error.message ?: ""
        // Важно не путать транзиентные/конфигурационные ошибки с отсутствием GMS.
        // Для сообщения пользователю используем только явный маркер недоступности сервиса.
        return message.contains("SERVICE_NOT_AVAILABLE") ||
            message.contains("MISSING_INSTANCEID_SERVICE")
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

                    // Каждая запись профиля рассылается всем, кто его слушает, а старт процесса
                    // частый (виджет, пуши) — неизменившийся токен заново не пишем
                    if (!forceRefresh && isTokenSaved(currentUserId, token)) {
                        if (BuildConfig.DEBUG) {
                            Timber.tag(TAG).d("[fcm] Token already saved for user=$currentUserId, skip")
                        }
                        result = Result.success(token)
                        break
                    }

                    // Используем NonCancellable, чтобы гарантировать сохранение токена
                    val queued = withContext(NonCancellable) {
                        socialRepository.updateUserFcmToken(currentUserId, token)
                    }
                    // Не попал в очередь — не запоминаем, чтобы следующий старт попробовал снова
                    if (queued) markTokenSaved(currentUserId, token)

                    // Авто-восстановление: если FCM-токен успешно получен и сохранён,
                    // сбрасываем ранее выставленные флаги «FCM недоступен».
                    prefs.edit {
                        remove(KEY_FCM_UNAVAILABLE_DETECTED)
                        remove(KEY_FCM_UNAVAILABLE_DIALOG_SHOWN)
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
                val hasGms = hasGooglePlayServices()
                
                if (isServiceUnavailable && !hasGms) {
                    // Это ожидаемая ошибка для устройств без Google Play Services или с ограничениями
                    Timber.tag(TAG).w("FCM token unavailable: ${lastError?.message} (no Google Play Services)")
                    // Сохраняем флаг для показа сообщения пользователю
                    prefs.edit { putBoolean(KEY_FCM_UNAVAILABLE_DETECTED, true) }
                } else {
                    Timber.tag(TAG).e(lastError, "Ошибка получения FCM токена")
                }
                Result.failure(lastError ?: Exception("FCM token refresh failed"))
            }
        } finally {
            isRefreshing.set(false)
        }
    }

    private fun isTokenSaved(userId: String, token: String): Boolean =
        prefs.getString(KEY_SAVED_FOR_USER, null) == userId &&
            prefs.getString(KEY_SAVED_TOKEN, null) == token &&
            System.currentTimeMillis() - prefs.getLong(KEY_SAVED_AT, 0L) in 0 until TOKEN_RESAVE_INTERVAL_MS

    /** Токен поставлен в очередь синхронизации — дальше его доставку обеспечивает outbox. */
    fun markTokenSaved(userId: String, token: String) {
        prefs.edit {
            putString(KEY_SAVED_FOR_USER, userId)
            putString(KEY_SAVED_TOKEN, token)
            putLong(KEY_SAVED_AT, System.currentTimeMillis())
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
            if (!hasGooglePlayServices() && (
                errorMessage.contains("SERVICE_NOT_AVAILABLE") ||
                    errorMessage.contains("MISSING_INSTANCEID_SERVICE")
                )
            ) {
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
            if (!hasGooglePlayServices() && (
                errorMessage.contains("SERVICE_NOT_AVAILABLE") ||
                    errorMessage.contains("MISSING_INSTANCEID_SERVICE")
                )
            ) {
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
