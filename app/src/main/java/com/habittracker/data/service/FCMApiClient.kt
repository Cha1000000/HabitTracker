package com.habittracker.data.service

import android.util.Log
import com.habittracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Клиент для отправки push-уведомлений через FCM REST API
 */
@Singleton
class FCMApiClient @Inject constructor() {

    companion object {
        private const val TAG = "FCMApiClient"
        private const val FCM_URL = "https://fcm.googleapis.com/fcm/send"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Отправляет push-уведомление через FCM REST API
     */
    suspend fun sendPushNotification(
        fcmToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val serverKey = BuildConfig.FCM_SERVER_KEY
            if (serverKey.isBlank() || serverKey == "YOUR_FCM_SERVER_KEY_HERE") {
                Log.w(TAG, "FCM Server Key не настроен. Получите ключ из Firebase Console → Project Settings → Cloud Messaging")
                return@withContext Result.failure(Exception("FCM Server Key не настроен"))
            }

            val fcmMessage = FCMMessage(
                to = fcmToken,
                notification = FCMNotification(
                    title = title,
                    body = body,
                    sound = "default",
                    clickAction = "FLUTTER_NOTIFICATION_CLICK"
                ),
                data = data,
                priority = "high",
                timeToLive = 3600 // 1 час
            )

            val requestBody = json.encodeToString(fcmMessage)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(FCM_URL)
                .addHeader("Authorization", "key=$serverKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d(TAG, "Отправка FCM сообщения: $requestBody")

            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "FCM ответ: $responseBody")
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Ошибка FCM: ${response.code} - $errorBody")
                Result.failure(Exception("FCM Error: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при отправке FCM", e)
            Result.failure(e)
        }
    }

    /**
     * Отправляет push-уведомление нескольким пользователям
     */
    suspend fun sendToMultipleTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Result<BatchResult> = withContext(Dispatchers.IO) {
        try {
            val serverKey = BuildConfig.FCM_SERVER_KEY
            if (serverKey.isBlank() || serverKey == "YOUR_FCM_SERVER_KEY_HERE") {
                Log.w(TAG, "FCM Server Key не настроен. Получите ключ из Firebase Console → Project Settings → Cloud Messaging")
                return@withContext Result.failure(Exception("FCM Server Key не настроен"))
            }

            if (tokens.isEmpty()) {
                return@withContext Result.success(BatchResult(0, 0, 0))
            }

            var successCount = 0
            var failureCount = 0

            // Отправляем уведомления пакетами по 1000 (лимит FCM)
            tokens.chunked(1000).forEach { tokenBatch ->
                tokenBatch.forEach { token ->
                    val result = sendPushNotification(token, title, body, data)
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failureCount++
                        Log.w(TAG, "Не удалось отправить уведомление на токен: $token")
                    }
                }
            }

            Log.d(TAG, "Отправка завершена: успешно=$successCount, ошибок=$failureCount")
            Result.success(BatchResult(tokens.size, successCount, failureCount))
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при пакетной отправке push-уведомлений", e)
            Result.failure(e)
        }
    }

    /**
     * Отправляет уведомление группе пользователей (batch)
     */
    suspend fun sendBatchNotification(
        fcmTokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Result<BatchResult> = withContext(Dispatchers.IO) {
        try {
            val serverKey = BuildConfig.FCM_SERVER_KEY
            if (serverKey.isBlank() || serverKey == "YOUR_FCM_SERVER_KEY_HERE") {
                Log.w(TAG, "FCM Server Key не настроен. Получите ключ из Firebase Console → Project Settings → Cloud Messaging")
                return@withContext Result.failure(Exception("FCM Server Key не настроен"))
            }

            val results = mutableListOf<SingleResult>()
            
            // Отправляем уведомления пакетами по 1000 (лимит FCM)
            fcmTokens.chunked(1000).forEach { tokenBatch ->
                tokenBatch.forEach { token ->
                    val result = sendPushNotification(token, title, body, data)
                    results.add(
                        SingleResult(
                            token = token,
                            success = result.isSuccess,
                            error = result.exceptionOrNull()?.message
                        )
                    )
                }
            }
            
            val successCount = results.count { it.success }
            val failureCount = results.count { !it.success }
            
            Log.d(TAG, "Batch результат: $successCount успешно, $failureCount ошибок")
            
            Result.success(
                BatchResult(
                    totalSent = fcmTokens.size,
                    successCount = successCount,
                    failureCount = failureCount,
                    results = results
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка batch отправки", e)
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
            val serverKey = BuildConfig.FCM_SERVER_KEY
            if (serverKey.isBlank() || serverKey == "YOUR_FCM_SERVER_KEY_HERE") {
                Log.w(TAG, "FCM Server Key не настроен. Получите ключ из Firebase Console → Project Settings → Cloud Messaging")
                return@withContext Result.failure(Exception("FCM Server Key не настроен"))
            }

            val fcmMessage = FCMTopicMessage(
                to = "/topics/$topic",
                notification = FCMNotification(
                    title = title,
                    body = body,
                    sound = "default"
                ),
                data = data,
                priority = "high"
            )

            val requestBody = json.encodeToString(fcmMessage)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(FCM_URL)
                .addHeader("Authorization", "key=$serverKey")
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

@Serializable
data class FCMMessage(
    val to: String,
    val notification: FCMNotification,
    val data: Map<String, String>,
    val priority: String,
    val timeToLive: Int? = null
)

@Serializable
data class FCMTopicMessage(
    val to: String,
    val notification: FCMNotification,
    val data: Map<String, String>,
    val priority: String
)

@Serializable
data class FCMNotification(
    val title: String,
    val body: String,
    val sound: String,
    val clickAction: String? = null
)

data class BatchResult(
    val totalSent: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<SingleResult> = emptyList()
)

data class SingleResult(
    val token: String,
    val success: Boolean,
    val error: String? = null
)
