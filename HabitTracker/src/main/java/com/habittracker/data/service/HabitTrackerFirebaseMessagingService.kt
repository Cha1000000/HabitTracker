package com.habittracker.data.service

import android.annotation.SuppressLint
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.habittracker.MainActivity
import com.habittracker.R
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.dao.PendingNotificationDao
import com.habittracker.data.local.entity.PendingNotificationEntity
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
    
    @Inject
    lateinit var appStateManager: AppStateManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson by lazy { Gson() }
    private val pendingDao: PendingNotificationDao by lazy {
        HabitTrackerDatabase.getDatabase(applicationContext).pendingNotificationDao()
    }

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "habit_tracker_notifications"
        private const val CHANNEL_NAME = "HabitTracker Уведомления"
        private const val CHANNEL_DESCRIPTION = "Уведомления о групповых привычках и социальных взаимодействиях"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Попробуем доставить отложенные уведомления для текущего пользователя при старте сервиса
        serviceScope.launch {
            try {
                deliverPendingForCurrentUser()
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось доставить отложенные при старте", e)
            }
        }
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

        // Если указали целевого пользователя в payload, не показываем уведомление не тому пользователю
        val recipientUserId = data["recipientUserId"]
        if (recipientUserId != null) {
            val currentUserId = try { authRepository.getCurrentUserId() } catch (e: Exception) {
                Log.w(TAG, "authRepository.getCurrentUserId() failed in onMessageReceived", e)
                null
            }
            if (currentUserId == null || currentUserId != recipientUserId) {
                Log.d(TAG, "Уведомление не для текущего пользователя. current=$currentUserId, recipient=$recipientUserId — сохраняем в очередь")
                // Сохраняем в локальную очередь
                serviceScope.launch {
                    try {
                        val titleFromData = data["title"] ?: notification?.title ?: "HabitTracker"
                        val bodyFromData = data["body"] ?: notification?.body ?: "У вас новое уведомление"
                        savePendingNotification(
                            recipientUserId = recipientUserId,
                            type = notificationType,
                            title = titleFromData,
                            body = bodyFromData,
                            data = data,
                            ttlSeconds = 3600L
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка сохранения отложенного уведомления", e)
                    }
                }
                return
            }
        }
        
        // Показываем уведомление пользователю (только если не на главном экране, или не на экране уведомлений)
        val shouldShowNotification = !(appStateManager.isHabitsScreenActive() || appStateManager.isNotificationsScreenActive())
        
        if (shouldShowNotification) {
            val titleToShow = data["title"] ?: notification?.title ?: "HabitTracker"
            val bodyToShow = data["body"] ?: notification?.body ?: "У вас новое уведомление"
            showNotification(
                title = titleToShow,
                body = bodyToShow,
                data = data
            )
            Log.d(TAG, "Notification shown: app not on HabitsScreen")
        } else {
            Log.d(TAG, "Notification suppressed: HabitsScreen is active")
        }
        
        // Сохраняем уведомление в базу данных для отображения на экране уведомлений
        // Сохраняем уведомления в базу данных для определенных типов
        if (recipientUserId != null && notificationType in listOf(
                "group_habit_completed",
                "habit_invitation_accepted",
                "new_participant"
            )) {
            serviceScope.launch {
                try {
                    saveGroupHabitNotification(data, notificationType)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка сохранения уведомления в базу", e)
                }
            }
        }

        // После успешного показа попробуем доставить очередь для текущего пользователя
        serviceScope.launch {
            try {
                deliverPendingForCurrentUser()
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось доставить отложенные после показа", e)
            }
        }
    }

    /**
     * Создает канал уведомлений для Android 8+
     */
    @SuppressLint("ObsoleteSdkInt")
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

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        Log.d(TAG, "Уведомление показано: $title")
    }

    private suspend fun savePendingNotification(
        recipientUserId: String,
        type: String,
        title: String,
        body: String,
        data: Map<String, String>,
        ttlSeconds: Long? = 3600L
    ) {
        // дедуп ключ: type + ключевые поля
        val dedupKey = buildString {
            append(type)
            data["habitTitle"]?.let { append("|ht=").append(it) }
            data["acceptedByName"]?.let { append("|ab=").append(it) }
            data["fromUserName"]?.let { append("|fu=").append(it) }
            data["habit_id"]?.let { append("|hid=").append(it) }
        }
        // Удалим предыдущие с тем же ключом (если есть)
        if (dedupKey.isNotEmpty()) {
            runCatching { pendingDao.deleteByDedupKey(dedupKey) }
        }

        val entity = PendingNotificationEntity(
            recipientUserId = recipientUserId,
            type = type,
            title = title,
            body = body,
            dataJson = gson.toJson(data),
            receivedAt = System.currentTimeMillis(),
            ttlSeconds = ttlSeconds,
            dedupKey = dedupKey.ifEmpty { null }
        )
        pendingDao.insert(entity)
        // подчистим просроченные
        pendingDao.deleteExpired(System.currentTimeMillis())
    }

    private suspend fun deliverPendingForCurrentUser() {
        val currentUserId = try { authRepository.getCurrentUserId() } catch (e: Exception) {
            Log.w(TAG, "authRepository.getCurrentUserId() failed in deliverPendingForCurrentUser", e)
            null
        } ?: return
        pendingDao.deleteExpired(System.currentTimeMillis())
        val items = pendingDao.getAllForUser(currentUserId)
        if (items.isEmpty()) return
        Log.d(TAG, "Доставка отложенных уведомлений: count=${items.size} для user=$currentUserId")
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        items.forEach { item ->
            val map: Map<String, String> = runCatching {
                gson.fromJson<Map<String, String>>(item.dataJson, mapType) ?: emptyMap()
            }.getOrElse { emptyMap() }
            showNotification(item.title, item.body, map)
            runCatching { pendingDao.deleteById(item.id) }
        }
    }

    /**
     * Создает Intent для открытия соответствующего экрана при нажатии на уведомление
     */
    private fun createNotificationIntent(data: Map<String, String>): Intent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            "friend_accepted" -> {
                intent.putExtra("navigate_to", "friends")
            }
            "habit_invitation" -> {
                intent.putExtra("navigate_to", "habit_invitations")
            }
            "habit_invitation_accepted" -> {
                val habitId = data["habit_id"]
                if (habitId != null) {
                    intent.putExtra("navigate_to", "group_habit_detail")
                    intent.putExtra("habit_id", habitId)
                } else {
                    intent.putExtra("navigate_to", "notifications")
                }
            }
            "habit_reminder" -> {
                // На всякий случай, если бэк начнет отправлять отдельный тип напоминаний
                intent.putExtra("navigate_to", "habits")
            }
            else -> {
                intent.putExtra("navigate_to", "notifications")
            }
        }

        val dbgNavigateTo = intent.getStringExtra("navigate_to")
        val dbgHabitId = intent.getStringExtra("habit_id")
        Log.d(TAG, "[push-nav] Built intent navigate_to=${dbgNavigateTo} habit_id=${dbgHabitId}")
        return intent
    }
    
    /**
     * Сохраняет уведомление в базу данных
     */
    private fun saveGroupHabitNotification(data: Map<String, String>, notificationType: String) {
        val habitId = data["habit_id"] ?: data["habitId"] ?: return
        
        // Определяем тип и данные уведомления
        val (type) = when (notificationType) {
            "group_habit_completed" -> {
                val name = data["participant_name"] ?: "Участник"
                Triple("PARTICIPANT_COMPLETED", name, data["body"] ?: "$name выполнил привычку!")
            }
            "habit_invitation_accepted" -> {
                val name = data["acceptedByName"] ?: "Пользователь"
                Triple("INVITATION_ACCEPTED", name, data["body"] ?: "$name принял ваше приглашение")
            }
            "new_participant" -> {
                val name = data["participant_name"] ?: "Пользователь"
                Triple("FRIEND_JOINED", name, data["body"] ?: "$name присоединился к привычке")
            }
            else -> return
        }

        Log.d(TAG, "Push-уведомление получено: type=$type, habitId=$habitId (синхронизация через Firestore)")
    }
}
