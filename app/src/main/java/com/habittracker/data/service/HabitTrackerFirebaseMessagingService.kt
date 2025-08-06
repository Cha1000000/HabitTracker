package com.habittracker.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.habittracker.MainActivity
import com.habittracker.R
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Messaging Service для обработки push-уведомлений
 * и управления FCM токенами
 */
@AndroidEntryPoint
class HabitTrackerFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var socialRepository: SocialRepository
    
    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "habit_tracker_notifications"
        private const val CHANNEL_NAME = "HabitTracker Уведомления"
        private const val CHANNEL_DESCRIPTION = "Уведомления о групповых привычках и социальных взаимодействиях"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Вызывается при получении нового FCM токена
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Получен новый FCM токен: $token")
        
        // Сохраняем токен в базе данных
        serviceScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    socialRepository.updateUserFcmToken(currentUserId, token)
                    Log.d(TAG, "FCM токен успешно обновлен для пользователя: $currentUserId")
                } else {
                    Log.w(TAG, "Пользователь не авторизован, токен не сохранен")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения FCM токена", e)
            }
        }
    }

    /**
     * Вызывается при получении push-уведомления
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Получено push-уведомление от: ${remoteMessage.from}")
        
        // Обрабатываем данные уведомления
        val data = remoteMessage.data
        val notification = remoteMessage.notification
        
        // Определяем тип уведомления
        val notificationType = data["type"] ?: "unknown"
        
        Log.d(TAG, "Тип уведомления: $notificationType")
        Log.d(TAG, "Данные: $data")
        
        // Показываем уведомление пользователю
        showNotification(
            title = notification?.title ?: "HabitTracker",
            body = notification?.body ?: "У вас новое уведомление",
            data = data
        )
    }

    /**
     * Создает канал уведомлений для Android 8+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Канал уведомлений создан: $CHANNEL_ID")
        }
    }

    /**
     * Показывает уведомление пользователю
     */
    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val intent = createNotificationIntent(data)
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // Добавляем дополнительную информацию в зависимости от типа
        when (data["type"]) {
            "group_habit_completed" -> {
                notificationBuilder.setColor(getColor(R.color.success_color))
            }
            "group_achievement" -> {
                notificationBuilder.setColor(getColor(R.color.achievement_color))
            }
            "new_participant" -> {
                notificationBuilder.setColor(getColor(R.color.info_color))
            }
            "participant_streak" -> {
                notificationBuilder.setColor(getColor(R.color.streak_color))
            }
            "friend_request" -> {
                notificationBuilder.setColor(getColor(R.color.primary))
            }
            "habit_invitation" -> {
                notificationBuilder.setColor(getColor(R.color.secondary))
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        Log.d(TAG, "Уведомление показано: $title")
    }

    /**
     * Создает Intent для открытия соответствующего экрана при нажатии на уведомление
     */
    private fun createNotificationIntent(data: Map<String, String>): Intent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Добавляем данные для навигации
        when (data["type"]) {
            "group_habit_completed", "group_achievement", "new_participant", "participant_streak" -> {
                val habitId = data["habit_id"]
                if (habitId != null) {
                    intent.putExtra("navigate_to", "group_habit_detail")
                    intent.putExtra("habit_id", habitId)
                }
            }
            "friend_request" -> {
                intent.putExtra("navigate_to", "friend_requests")
            }
            "habit_invitation" -> {
                intent.putExtra("navigate_to", "habit_invitations")
            }
            else -> {
                intent.putExtra("navigate_to", "notifications")
            }
        }

        return intent
    }
}
