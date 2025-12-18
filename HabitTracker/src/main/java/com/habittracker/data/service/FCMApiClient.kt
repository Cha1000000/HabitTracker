package com.habittracker.data.service

import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Исключение для обозначения невалидного FCM токена
 */
class UnregisteredTokenException(message: String) : Exception(message)

/**
 * Клиент для отправки push-уведомлений через FCM HTTP v1 API
 * Использует OAuth 2.0 авторизацию через Service Account
 */
@Singleton
class FCMApiClient @Inject constructor() {

    companion object {
        private const val TAG = "FCMApiClient"
        // FCM HTTP v1 API endpoint
        private const val FCM_URL = "https://fcm.googleapis.com/v1/projects/habitttracker-505b6/messages:send"
        private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        private const val DEFAULT_TTL = "3600s" // 1 час на доставку
        private const val CHANNEL_ID = "habit_tracker_notifications"
    }

    private fun maskToken(token: String?): String {
        if (token.isNullOrBlank()) return "(null)"
        return if (token.length > 8) "${token.take(4)}****${token.takeLast(4)}" else "****"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        // Не сериализуем значения по умолчанию, чтобы не тащить пустые поля
        encodeDefaults = false
        // Не включаем поля со значением null (например, notification=null)
        explicitNulls = false
    }

    // Service Account JSON для OAuth 2.0
    private val serviceAccountJson = """
    {
        "type": "service_account",
        "project_id": "habitttracker-505b6",
        "private_key_id": "83f370d2b0510474b8fd937b0e443c1367b12af1",
        "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCN9R1NFKgnrQe5\nF5rRJu+NKTi18vIebQEHbQidwzEwhaIeu4rTFeoS5WQ3NkomyHAsYC/9mmn3s3SQ\nFTNE59eDMDjEFZ9dXKFuvOrPVFPom4A9DZAC2v4IK/AyWvJWHEM+gbLkfWkq1QD2\nOZIufwrTPZzqkw4vGXeSmbfu9lwFGb8t2X5Zy9bs5sxQfpRtnMFEvQozf5Ckq36R\nJZnsOy07Z/OETg5xbPXXPu4ZVXUjNLBMntiA2u6IaWR+EWisJ5eyVTDoF2v1zhK1\nKPB/kEzUrEdnsynn7ehj+k5bKDWdG/809HakRq1F0+CLwb6zY2JkSA8/l4MsvbVg\nFMufUr2JAgMBAAECggEAOUreAof9vdWVslwWfacrM1uZqcVddm4j5i1ZNuLabWzh\nm2mxg7PqfqnaInZkwvZOX3qKo924OuJQfv4vbPXtAuNpZaocYX/LH3hbhEXSf6SS\nwxFAOAbOU9n33TrbZ2mMsiOCYblDwqeo9bcSinV3lv3c4MdtCBFNMvubQnRnex7p\nHJzSuvLCX6TIlsIc0s+VR6Bp0/QQ6OCr4hJNj4Fiq7Y0LSS1wRkqPwwExdXAVk/O\ncUwDtjp+7JGr098QXHZg0VPEvQzeIM4/bllTc/j1rqY83clWh/R+Kl6r870ez/58\n0rlgW1HqGUDyMBVK+wBCTOdodxIE7Aw6OjP9P7TVaQKBgQDD4nn9ZXWcl8wqpoHh\nzPq4kyEiX3KnzECZK0+1I+DJI+Sj9ta095ph3ZbtLQesiSMgTe3baPR5Exu9qVSl\n2QD3i2oCWg7dd7VauxUp1fQYTorp5vBlHYfOzELDJ6zRZ7ufT52jIxg2h58rG2oe\nff7IaIMWowg30JXoLCAUHuwYLwKBgQC5heQHru0zq20hRYkPuh9fF8yOQja2geAO\n7msuOdriBQmhyeQb/78mUG6wPkvTrG/w9tPwKQx8FmOKeh4Hc3D73qIHsmPkZ0BX\nOTGqY2qsWWpxk4QbK21MuenF1JAZMDILcptNQVG4FPSBjSakQFtHLl9GK+pBGZgS\nNR2CBTXfxwKBgEfrWRq+K6Zo3V8zbzB2T/+AlYPHQ5N0HKugCU3adGelNAc3U7Fx\nz07rSsSd6dlhKHJt6kzKhigF6gOCrj+OIxrAJxXh9t89WmqLkcOgeFSMUdh7H/wg\nEYLN4B1C7eKckXh2hMwm0HcE6F2tXRMA85ClPqwLA7Nqc/+gY86/Y7OlAoGBAIrH\n1tHMkFKDnUozWxlxsMAQ7ItKiuUx+GsS/QiCbPOAszjc7w/m25YMuxCKOWzp5H9M\nFTTlcBpvIR1YxpfqSo2PeIPrdYBJoL7e4QFGB2lQJik6Iau1a7jmpjLAFN9Sx0z6\n+aPsBfZ/bnxvG4XpQp7X2JuEhGMYg0KzixRVU3zHAoGANVHZUyM0YSXjK0NMiTYv\nhKzHt9xN6JPkDvcpEoTB0rPJ14JuQ6S6gI6YhWfQK8zgZQNRGArqkiSliRhb0/1C\npVrRqNA9ZePwua4NA1UG5rO1K8HuP+8e/blqtvHlBOXldjxY7eh2g5tRjiVMw3I8\nbVrA/TmM+ktKkxuxhdqjSmU=\n-----END PRIVATE KEY-----\n",
        "client_email": "firebase-adminsdk-fbsvc@habitttracker-505b6.iam.gserviceaccount.com",
        "client_id": "112428448984284425837",
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40habitttracker-505b6.iam.gserviceaccount.com",
        "universe_domain": "googleapis.com"
    }
    """.trimIndent()

    /**
     * Получает OAuth 2.0 access token для FCM API
     */
    private fun getAccessToken(): String {
        return try {
            val credentials = GoogleCredentials
                .fromStream(ByteArrayInputStream(serviceAccountJson.toByteArray(StandardCharsets.UTF_8)))
                .createScoped(listOf(FCM_SCOPE))
            
            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения access token", e)
            throw e
        }
    }

    /**
     * Отправляет push-уведомление через FCM HTTP v1 API
     */
    suspend fun sendPushNotification(
        fcmToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()

            val fcmMessage = FCMV1Message(
                message = FCMV1MessageData(
                    token = fcmToken,
                    // notification не указываем — отправляем data-only
                    notification = null,
                    data = data + mapOf(
                        // передаем заголовок/текст в data, клиент сам строит уведомление
                        "title" to title,
                        "body" to body
                    ),
                    android = AndroidConfig(
                        priority = "high",
                        ttl = DEFAULT_TTL
                    )
                )
            )

            val requestBody = json.encodeToString(fcmMessage)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(FCM_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d(TAG, "Отправка FCM HTTP v1 сообщения (payloadSize=${requestBody.contentLength()} bytes, to=${maskToken(fcmToken)})")

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "FCM HTTP v1 ответ: ${response.code}")

            if (response.isSuccessful) {
                Log.d(TAG, "✅ Push-уведомление успешно отправлено")
                Result.success(Unit)
            } else {
                Log.w(TAG, "❌ Ошибка FCM HTTP v1: ${response.code}")
                
                // Проверяем, является ли ошибка UNREGISTERED
                if (responseBody.contains("UNREGISTERED")) {
                    Log.w(TAG, "🔄 Токен UNREGISTERED, требуется обновление токена на устройстве получателя")
                    // Возвращаем специальную ошибку для обработки
                    Result.failure(UnregisteredTokenException("FCM token is unregistered: ${response.code}"))
                } else {
                    Result.failure(Exception("FCM HTTP v1 ошибка: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Критическая ошибка FCM HTTP v1", e)
            Result.failure(e)
        }
    }

    /**
     * Отправляет push-уведомления на несколько токенов через FCM HTTP v1 API (батч)
     */
    suspend fun sendToMultipleTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Result<BatchResult> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()

            if (tokens.isEmpty()) {
                return@withContext Result.success(BatchResult(0, 0, 0))
            }

            val results = mutableListOf<SingleResult>()
            
            // Отправляем по одному сообщению для каждого токена (FCM v1 не поддерживает batch)
            tokens.forEach { token ->
                try {
                    val fcmMessage = FCMV1Message(
                        message = FCMV1MessageData(
                            token = token,
                            notification = null,
                            data = data + mapOf(
                                "title" to title,
                                "body" to body
                            ),
                            android = AndroidConfig(
                                priority = "high",
                                ttl = DEFAULT_TTL
                            )
                        )
                    )

                    val requestBody = json.encodeToString(fcmMessage)
                        .toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url(FCM_URL)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        results.add(SingleResult(token, true, null))
                        Log.d(TAG, "✅ Отправлено на токен: ${token.take(20)}...")
                    } else {
                        results.add(SingleResult(token, false, "HTTP ${response.code}: $responseBody"))
                        Log.w(TAG, "❌ Ошибка для токена ${token.take(20)}...: ${response.code} - $responseBody")
                    }
                } catch (e: Exception) {
                    results.add(SingleResult(token, false, e.message ?: "Unknown error"))
                    Log.e(TAG, "❌ Исключение для токена ${token.take(20)}...", e)
                }
            }

            val successCount = results.count { it.success }
            val failureCount = results.size - successCount

            val batchResult = BatchResult(
                totalSent = results.size,
                successCount = successCount,
                failureCount = failureCount,
                results = results
            )

            Log.d(TAG, "📊 Батч результат: $successCount успешных, $failureCount ошибок из ${results.size}")
            Result.success(batchResult)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Критическая ошибка батч отправки", e)
            Result.failure(e)
        }
    }

    /**
     * Отправляет уведомление на топик
     */
    suspend fun sendTopicNotification(
        topic: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()

            val fcmMessage = FCMV1Message(
                message = FCMV1MessageData(
                    token = "/topics/$topic",
                    notification = null,
                    data = data + mapOf(
                        "title" to title,
                        "body" to body
                    ),
                    android = AndroidConfig(
                        priority = "high"
                    )
                )
            )

            val requestBody = json.encodeToString(fcmMessage)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(FCM_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Топик уведомление отправлено: $topic")
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Ошибка топик уведомления: ${response.code} - $errorBody")
                Result.failure(Exception("Topic notification error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при отправке топик уведомления", e)
            Result.failure(e)
        }
    }
}

// FCM models moved to FcmModels.kt for clarity and to avoid IDE highlighting glitches.
