package com.habittracker.util

import android.util.Log
import com.habittracker.data.service.FCMApiClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Утилита для тестирования FCM push-уведомлений в реальной среде
 * Можно использовать в debug сборке для проверки уведомлений
 */
@Singleton
class FCMTestingUtil @Inject constructor(
    private val fcmApiClient: FCMApiClient
) {

    companion object {
        private const val TAG = "FCMTestingUtil"
    }

    /**
     * Отправляет тестовое уведомление для проверки FCM
     */
    suspend fun sendTestNotification(
        fcmToken: String,
        testType: TestNotificationType = TestNotificationType.SIMPLE
    ): Result<Unit> {
        return try {
            Log.d(TAG, "🧪 Отправка тестового FCM уведомления типа: $testType")
            
            when (testType) {
                TestNotificationType.SIMPLE -> sendSimpleTest(fcmToken)
                TestNotificationType.FRIEND_REQUEST -> sendFriendRequestTest(fcmToken)
                TestNotificationType.HABIT_INVITATION -> sendHabitInvitationTest(fcmToken)
                TestNotificationType.CHALLENGE -> sendChallengeTest(fcmToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка отправки тестового уведомления", e)
            Result.failure(e)
        }
    }

    /**
     * Отправляет тестовые уведомления всех типов последовательно
     */
    suspend fun sendAllTestNotifications(
        fcmToken: String,
        delayBetweenMs: Long = 3000
    ) {
        Log.d(TAG, "🚀 Начинаем отправку всех типов тестовых уведомлений...")
        
        TestNotificationType.entries.forEach { type ->
            try {
                Log.d(TAG, "📤 Отправка теста: $type")
                val result = sendTestNotification(fcmToken, type)
                
                if (result.isSuccess) {
                    Log.d(TAG, "✅ Тест $type отправлен успешно")
                } else {
                    Log.w(TAG, "⚠️ Тест $type завершился с ошибкой: ${result.exceptionOrNull()?.message}")
                }
                
                // Задержка между уведомлениями
                kotlinx.coroutines.delay(delayBetweenMs)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Критическая ошибка при отправке теста $type", e)
            }
        }
        
        Log.d(TAG, "🏁 Завершена отправка всех тестовых уведомлений")
    }

    private suspend fun sendSimpleTest(fcmToken: String): Result<Unit> {
        return fcmApiClient.sendPushNotification(
            fcmToken = fcmToken,
            title = "🧪 HabitTracker Test",
            body = "Тестовое уведомление. Если вы видите это - FCM работает!",
            data = mapOf(
                "type" to "test",
                "action" to "open_app"
            )
        )
    }

    private suspend fun sendFriendRequestTest(fcmToken: String): Result<Unit> {
        return fcmApiClient.sendPushNotification(
            fcmToken = fcmToken,
            title = "👥 Новый запрос дружбы",
            body = "TestUser хочет добавить вас в друзья",
            data = mapOf(
                "type" to "friend_request",
                "fromUserId" to "test_user_123",
                "fromUserName" to "TestUser",
                "action" to "open_friend_requests"
            )
        )
    }

    private suspend fun sendHabitInvitationTest(fcmToken: String): Result<Unit> {
        return fcmApiClient.sendPushNotification(
            fcmToken = fcmToken,
            title = "🎯 Приглашение к привычке",
            body = "TestUser приглашает вас к участию в привычке 'Утренняя зарядка'",
            data = mapOf(
                "type" to "habit_invitation",
                "habitId" to "test_habit_123",
                "habitTitle" to "Утренняя зарядка",
                "fromUserId" to "test_user_123",
                "fromUserName" to "TestUser",
                "action" to "open_habit_invitations"
            )
        )
    }

    private suspend fun sendChallengeTest(fcmToken: String): Result<Unit> {
        return fcmApiClient.sendPushNotification(
            fcmToken = fcmToken,
            title = "🏆 Новый челлендж",
            body = "TestUser создал челлендж 'Неделя без пропусков' по привычке 'Утренняя зарядка'",
            data = mapOf(
                "type" to "challenge_invitation",
                "challengeId" to "test_challenge_123",
                "challengeTitle" to "Неделя без пропусков",
                "habitTitle" to "Утренняя зарядка",
                "fromUserId" to "test_user_123",
                "fromUserName" to "TestUser",
                "action" to "open_challenges"
            )
        )
    }

    /**
     * Проверяет конфигурацию FCM
     */
    fun checkFCMConfiguration(): FCMConfigStatus {
        return try {
            // Здесь можно добавить проверки конфигурации
            Log.d(TAG, "🔧 Проверка конфигурации FCM...")
            
            // Простая проверка что ключ настроен (без его отображения в логах)
            val hasValidKey = com.habittracker.BuildConfig.FCM_SERVER_KEY.let { key ->
                !key.isBlank() && key != "YOUR_FCM_SERVER_KEY_HERE"
            }
            
            if (hasValidKey) {
                Log.d(TAG, "✅ FCM_SERVER_KEY настроен")
                FCMConfigStatus.CONFIGURED
            } else {
                Log.w(TAG, "⚠️ FCM_SERVER_KEY не настроен")
                FCMConfigStatus.KEY_NOT_CONFIGURED
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки конфигурации FCM", e)
            FCMConfigStatus.ERROR
        }
    }
}

enum class TestNotificationType {
    SIMPLE,
    FRIEND_REQUEST,
    HABIT_INVITATION,
    CHALLENGE
}

enum class FCMConfigStatus {
    CONFIGURED,
    KEY_NOT_CONFIGURED,
    ERROR
}
