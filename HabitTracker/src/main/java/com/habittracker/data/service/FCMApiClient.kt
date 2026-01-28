package com.habittracker.data.service

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
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
      "private_key_id": "dae2a47403cfafb9d555042c4cb8b59f026e9f1a",
      "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDrAxaVMY6MpIa+\nG2x9smW/9q1q2nVcpcH+X2FQ/kEGO9wkofQYZ67QfGhqnYjJgQRSkQFBzVUwHXye\n8V3J2q7v0R7xkk+DE7xL2wE+LVZ/zRfdR36cY9XTBCYTylF1b2fsVUxO0SEuhUFb\n9DTn9t+UqCGGpUULDiRk7/kU6htTb+cnaMjihpUdzSNwwczR0bS3zmsmg9bPVNE3\nBhX+CVaZhVqoGLLTXMHfKkzjt3aH2vbtQTrfNyvScDyOiMlN2y8I9Jt/JiE4eulN\nnn9QNFZ/7bF/xh8BP8zA0zgoo0rhl73YQsDhp/k8qnQOg2Fsv9VcIZSTRxGIaS8a\nKunCFiItAgMBAAECggEALmzpQKbODL2RuqtrNrePvyjJYFLwnVFHbetEq73WdZdM\nqVXvBQUydRAmhm1BcOESTAdGMswP2JXyoOl4iCAxr8Fldf5bw76Z03hbmf9V16BZ\n5wYxHk7gljN7HCy7A9Jyuq6tCOLqRajgXPx0mSBdUZ4XJWSvq3vrN59qWrXWmRgg\nnNqSBKZlo5AOm5wr3CyqeOwSbEoW12k9mKqiFwqgFbe/WOh8ah1xausqM7ocPjHP\n1GBhVSYUX7qra84xsh480CuCpcGHs+lghPYQWsxf3x+lNq5R/N3hKz8rSX7I+NAo\n+okNBN5PTUJsfCsXTT7WH39vA48gWP0xcjlKi1B2pwKBgQD+skflh7v4hzQ2rwUz\nAcOvs59JdhlEbbQno8Nona+Zil0Nupx9V39PiDtLnLzamxOvNWY98TxhEu/Z1UXr\nxos+ZpZt1JmFWy3H//NHnXtxHqrSnTLoTTLpWB93q+LKrYtE1QF8IPIxHFOQRSpn\nMWMy+wC/5PtPbd2+ewx5JHzN0wKBgQDsNwQFZAR+UMhRqJlOLnGlemFjtNDRpbJm\nNUgPfWxpicwQmt/AzqZ+s2xjNWTaisny+W5CHf3s+F6onT/enq0a5HcEiqQSNgtD\n66EWUqg7u0zjpOFnxyT7WjicUWgX4qNKbAuFyj6QFhohXCVZVi6fn89IrGUXFC0/\nz9YpaxtP/wKBgQCNTE7CsejAWhHHfJV/1KzBXv8xfAlMgyf9Dccs1L5EihRRt4N1\nrgL9FnD+47eJ95qWCyPrnVeH6z+RM9aUlRNOzQ8LY3TAHPNrDz3iLyX6fa77JSbG\n5X0nIzJh68Pn+RtYVmt6d7SlNjfNw/FgSm/kKogh7WDWkfcA47bpxipO7QKBgHqV\n9o1BoZ9YFw1hvOW47pudDkurO6+BGTs3rYT0Zz3CGFgJt9Mcq4UXYkl+HS/ZVQK2\ndYT0gaHoJ1/D87B5koZhSG7f8FPPHjuKXhfyn4IYKsf8+klcLRIQeU6OZMH4iRuH\nRNeS5LLr9XWYl31sX1fRnMYvSWsXun/iQVdPUWZLAoGAUmcLXkVdQQmis0rHgpOm\nSJ+LZNUXrSEpOWVmF1vDCTVMtt2SxwBcGeZKnPtwNJtqvgDcn0A2E6c2sGjzFY5B\nRjEvwd0LLBD8IoH2RADYQVGxQQvv7/1v/9fZzkBKN6I2qOnbF5o0tjIL1ZovBekG\nKNB9/LQC9HUsgOYt0t/ZMcY=\n-----END PRIVATE KEY-----\n",
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
            Timber.tag(TAG).e(e, "Ошибка получения access token")
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

            Timber.tag(TAG).d("Отправка FCM HTTP v1 сообщения (payloadSize=${requestBody.contentLength()} bytes, to=${maskToken(fcmToken)})")

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Timber.tag(TAG).d("FCM HTTP v1 ответ: ${response.code}")

            if (response.isSuccessful) {
                Timber.tag(TAG).d("✅ Push-уведомление успешно отправлено")
                Result.success(Unit)
            } else {
                Timber.tag(TAG).w("❌ Ошибка FCM HTTP v1: ${response.code}")
                
                // Проверяем, является ли ошибка UNREGISTERED
                if (responseBody.contains("UNREGISTERED")) {
                    Timber.tag(TAG).w("🔄 Токен UNREGISTERED, требуется обновление токена на устройстве получателя")
                    // Возвращаем специальную ошибку для обработки
                    Result.failure(UnregisteredTokenException("FCM token is unregistered: ${response.code}"))
                } else {
                    Result.failure(Exception("FCM HTTP v1 ошибка: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Критическая ошибка FCM HTTP v1")
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
                        Timber.tag(TAG).d("✅ Отправлено на токен: ${token.take(20)}...")
                    } else {
                        results.add(SingleResult(token, false, "HTTP ${response.code}: $responseBody"))
                        Timber.tag(TAG).w("❌ Ошибка для токена ${token.take(20)}...: ${response.code} - $responseBody")
                    }
                } catch (e: Exception) {
                    results.add(SingleResult(token, false, e.message ?: "Unknown error"))
                    Timber.tag(TAG).e(e, "❌ Исключение для токена ${token.take(20)}...")
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

            Timber.tag(TAG).d("📊 Батч результат: $successCount успешных, $failureCount ошибок из ${results.size}")
            Result.success(batchResult)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Критическая ошибка батч отправки")
            Result.failure(e)
        }
    }
}

// FCM models moved to FcmModels.kt for clarity and to avoid IDE highlighting glitches.
