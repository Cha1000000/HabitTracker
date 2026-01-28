package com.habittracker.data.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
import timber.log.Timber
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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Попробуем доставить отложенные уведомления для текущего пользователя при старте сервиса
        serviceScope.launch {
            try {
                deliverPendingForCurrentUser()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Не удалось доставить отложенные при старте")
            }
        }
    }

    /**
     * Вызывается при получении нового FCM токена
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag(TAG).d("Получен новый FCM токен: $token")
        
        // Сохраняем токен в базе данных
        serviceScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    socialRepository.updateUserFcmToken(currentUserId, token)
                    Timber.tag(TAG).d("FCM токен успешно обновлен для пользователя: $currentUserId")
                } else {
                    Timber.tag(TAG).w("Пользователь не авторизован, токен не сохранен")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка сохранения FCM токена")
            }
        }
    }

    /**
     * Вызывается при получении push-уведомления
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Timber.tag(TAG).d("Получено push-уведомление от: ${remoteMessage.from}")
        
        // Обрабатываем данные уведомления
        val data = remoteMessage.data
        val notification = remoteMessage.notification
        
        // Определяем тип уведомления
        val notificationType = data["type"] ?: "unknown"
        
        Timber.tag(TAG).d("Тип уведомления: $notificationType")
        Timber.tag(TAG).d("Данные: $data")

        // Если указали целевого пользователя в payload, не показываем уведомление не тому пользователю
        val recipientUserId = data["recipientUserId"]
        if (recipientUserId != null) {
            val currentUserId = try { authRepository.getCurrentUserId() } catch (e: Exception) {
                Timber.tag(TAG).w(e, "authRepository.getCurrentUserId() failed in onMessageReceived")
                null
            }
            if (currentUserId == null || currentUserId != recipientUserId) {
                Timber.tag(TAG).d("Уведомление не для текущего пользователя. current=$currentUserId, recipient=$recipientUserId — сохраняем в очередь")
                // Сохраняем в локальную очередь
                serviceScope.launch {
                    try {
                        val titleFromData = data["title"] ?: notification?.title ?: getString(R.string.notification_default_title)
                        val bodyFromData = data["body"] ?: notification?.body ?: getString(R.string.notification_default_body)
                        savePendingNotification(
                            recipientUserId = recipientUserId,
                            type = notificationType,
                            title = titleFromData,
                            body = bodyFromData,
                            data = data,
                            ttlSeconds = 3600L
                        )
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Ошибка сохранения отложенного уведомления")
                    }
                }
                return
            }
        }
        
        // Типы уведомлений, которые всегда должны показываться (приглашения, запросы дружбы и т.п.)
        val alwaysShowTypes = listOf(
            "challenge_invitation",
            "challenge_completed",
            "challenge_cancelled",
            "habit_invitation",
            "friend_request",
            "friend_request_accepted"
        )
        
        // Показываем уведомление пользователю (только если не на главном экране, или не на экране уведомлений)
        // Исключение: важные уведомления (приглашения) показываем всегда
        val shouldShowNotification = notificationType in alwaysShowTypes ||
            !(appStateManager.isHabitsScreenActive() || appStateManager.isNotificationsScreenActive())
        
        if (shouldShowNotification) {
            val titleToShow = data["title"] ?: notification?.title ?: getString(R.string.notification_default_title)
            val bodyToShow = data["body"] ?: notification?.body ?: getString(R.string.notification_default_body)
            showNotification(
                title = titleToShow,
                body = bodyToShow,
                data = data
            )
            Timber.tag(TAG).d("Notification shown: type=$notificationType, alwaysShow=${notificationType in alwaysShowTypes}")
        } else {
            Timber.tag(TAG).d("Notification suppressed: HabitsScreen is active, type=$notificationType")
        }
        
        // Сохраняем уведомление в базу данных для отображения на экране уведомлений
        // Сохраняем уведомления в базу данных для определенных типов
        if (recipientUserId != null && notificationType in listOf(
                "group_habit_completed",
                "habit_invitation_accepted",
                "new_participant",
                "challenge_invitation",
                "challenge_completed",
                "challenge_cancelled",
                "challenge_joined",
                "challenge_left"
            )) {
            serviceScope.launch {
                try {
                    saveGroupHabitNotification(data, notificationType)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Ошибка сохранения уведомления в базу")
                }
            }
        }

        // После успешного показа попробуем доставить очередь для текущего пользователя
        serviceScope.launch {
            try {
                deliverPendingForCurrentUser()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Не удалось доставить отложенные после показа")
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
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Timber.tag(TAG).d("Канал уведомлений создан: $CHANNEL_ID")
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
            "challenge_invitation" -> {
                notificationBuilder.setColor(getColor(R.color.primary))
            }
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        Timber.tag(TAG).d("Уведомление показано: $title")
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
            Timber.tag(TAG).w(e, "authRepository.getCurrentUserId() failed in deliverPendingForCurrentUser")
            null
        } ?: return
        pendingDao.deleteExpired(System.currentTimeMillis())
        val items = pendingDao.getAllForUser(currentUserId)
        if (items.isEmpty()) return
        Timber.tag(TAG).d("Доставка отложенных уведомлений: count=${items.size} для user=$currentUserId")
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
            "challenge_completed", "challenge_cancelled", "challenge_joined", "challenge_left" -> {
                val challengeId = data["challenge_id"] ?: data["challengeId"]
                if (challengeId != null) {
                    intent.putExtra("navigate_to", "challenge_detail")
                    intent.putExtra("challenge_id", challengeId)
                    intent.putExtra("challengeId", challengeId)
                } else {
                    intent.putExtra("navigate_to", "challenge_invitations")
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
            "challenge_invitation" -> {
                intent.putExtra("navigate_to", "challenge_invitations")
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
            "nudge" -> {
                // При нажатии на nudge открываем экран привычки или список привычек
                val habitId = data["habit_id"]
                if (habitId != null) {
                    intent.putExtra("navigate_to", "group_habit_detail")
                    intent.putExtra("habit_id", habitId)
                } else {
                    intent.putExtra("navigate_to", "habits")
                }
            }
            else -> {
                intent.putExtra("navigate_to", "notifications")
            }
        }

        val dbgNavigateTo = intent.getStringExtra("navigate_to")
        val dbgHabitId = intent.getStringExtra("habit_id")
        Timber.tag(TAG).d("[push-nav] Built intent navigate_to=${dbgNavigateTo} habit_id=${dbgHabitId}")
        return intent
    }
    
    /**
     * Сохраняет уведомление в базу данных
     */
    private fun saveGroupHabitNotification(data: Map<String, String>, notificationType: String) {
        // Для челленджей habitId может отсутствовать
        val habitId = data["habit_id"] ?: data["habitId"]
        val challengeId = data["challenge_id"] ?: data["challengeId"]
        
        // Определяем тип и данные уведомления
        val (type, entityId, message) = when (notificationType) {
            "group_habit_completed" -> {
                if (habitId == null) return
                val name = data["participant_name"] ?: getString(R.string.default_participant_name)
                Triple("PARTICIPANT_COMPLETED", habitId, data["body"] ?: getString(R.string.notification_fallback_participant_completed, name))
            }
            "habit_invitation_accepted" -> {
                if (habitId == null) return
                val name = data["acceptedByName"] ?: getString(R.string.default_user_name)
                Triple("INVITATION_ACCEPTED", habitId, data["body"] ?: getString(R.string.notification_fallback_invitation_accepted, name))
            }
            "new_participant" -> {
                if (habitId == null) return
                val name = data["participant_name"] ?: getString(R.string.default_user_name)
                Triple("FRIEND_JOINED", habitId, data["body"] ?: getString(R.string.notification_fallback_friend_joined, name))
            }
            "challenge_invitation" -> {
                val challengeTitle = data["challengeTitle"] ?: getString(R.string.challenge_default_title)
                val id = challengeId ?: "unknown"
                Triple("CHALLENGE_INVITATION", id, data["body"] ?: getString(R.string.notification_fallback_challenge_invitation, challengeTitle))
            }
            "challenge_completed" -> {
                val challengeTitle = data["challengeTitle"] ?: getString(R.string.challenge_default_title)
                val id = challengeId ?: "unknown"
                Triple("CHALLENGE_COMPLETED", id, data["body"] ?: getString(R.string.notification_fallback_challenge_completed, challengeTitle))
            }
            "challenge_cancelled" -> {
                val challengeTitle = data["challengeTitle"] ?: getString(R.string.challenge_default_title)
                val id = challengeId ?: "unknown"
                Triple("CHALLENGE_CANCELLED", id, data["body"] ?: getString(R.string.notification_fallback_challenge_cancelled, challengeTitle))
            }
            "challenge_joined" -> {
                val challengeTitle = data["challengeTitle"] ?: getString(R.string.challenge_default_title)
                val id = challengeId ?: "unknown"
                Triple("CHALLENGE_JOINED", id, data["body"] ?: getString(R.string.notification_fallback_challenge_joined, challengeTitle))
            }
            "challenge_left" -> {
                val challengeTitle = data["challengeTitle"] ?: getString(R.string.challenge_default_title)
                val id = challengeId ?: "unknown"
                Triple("CHALLENGE_LEFT", id, data["body"] ?: getString(R.string.notification_fallback_challenge_left, challengeTitle))
            }
            else -> return
        }

        Timber.tag(TAG).d("Push-уведомление получено: type=$type, entityId=$entityId (синхронизация через Firestore)")
        
        // Сохраняем уведомление в базу данных
        if (notificationType.startsWith("challenge_")) {
            saveChallengeNotificationToDb(data, type, entityId, message)
        }
    }
    
    /**
     * Сохраняет уведомление о челлендже в базу данных
     */
    private fun saveChallengeNotificationToDb(data: Map<String, String>, type: String, challengeId: String, message: String) {
        val recipientUserId = data["recipientUserId"] ?: return
        val body = message
        val challengeTitle = data["challengeTitle"] ?: ""
        val habitTitle = data["habitTitle"] ?: ""
        
        serviceScope.launch {
            try {
                val groupHabitNotificationDao = HabitTrackerDatabase.getDatabase(applicationContext).groupHabitNotificationDao()
                val notification = com.habittracker.data.local.entity.GroupHabitNotificationEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = recipientUserId,
                    habitId = challengeId,
                    type = type,
                    participantId = "",
                    participantName = "",
                    habitTitle = "$challengeTitle ($habitTitle)",
                    message = body,
                    isRead = false,
                    readAt = null,
                    createdAt = System.currentTimeMillis()
                )
                groupHabitNotificationDao.insertNotification(notification)
                Timber.tag(TAG).d("Уведомление о челлендже сохранено в базу: $type")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка сохранения уведомления о челлендже")
            }
        }
    }
}
