package com.habittracker.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.habittracker.MainActivity
import com.habittracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Помощник для работы с уведомлениями о привычках
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val HABIT_REMINDER_CHANNEL_ID = "habit_reminder_channel"
        
        private const val NOTIFICATION_REQUEST_CODE = 1001
        private const val DAILY_REMINDER_REQUEST_CODE = 1002
        private const val DAILY_REMINDER_NOTIFICATION_ID = 9999
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Создает канал уведомлений для напоминаний о привычках
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HABIT_REMINDER_CHANNEL_ID,
                context.getString(R.string.channel_habit_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_habit_reminder_description)
                enableVibration(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(channel)
            Timber.tag("NotificationHelper").d("Уведомительный канал создан")
        }
    }

    /**
     * Показывает уведомление о напоминании привычки
     */
    fun showHabitReminderNotification(
        habitId: String,
        habitTitle: String,
        habitIcon: String = "🎯",
        isFollowUp: Boolean = false
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("habitId", habitId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isFollowUp) NOTIFICATION_REQUEST_CODE + 1000 else NOTIFICATION_REQUEST_CODE, // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentTitle = if (isFollowUp) {
            context.getString(R.string.notification_habit_reminder_title_followup, habitIcon)
        } else {
            context.getString(R.string.notification_habit_reminder_title_normal, habitIcon)
        }

        val contentText = if (isFollowUp) {
            context.getString(R.string.notification_habit_reminder_text_followup, habitTitle)
        } else {
            context.getString(R.string.notification_habit_reminder_text_normal, habitTitle)
        }
        
        val bigText = if (isFollowUp) {
            context.getString(R.string.notification_habit_reminder_big_text_followup, habitTitle)
        } else {
            context.getString(R.string.notification_habit_reminder_big_text_normal, habitTitle)
        }

        val notification = NotificationCompat.Builder(context, HABIT_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        // Use a different notification ID for follow-up to avoid overwriting (or maybe we WANT to overwrite?)
        // If we overwrite, the previous "Time for habit" disappears. That's probably fine/better.
        // But if the user didn't dismiss the first one, maybe we want to stack them?
        // Let's stack them for now to be more annoying (it is a reminder after all).
        // Using habitId.hashCode() + offset for follow-up.
        val notificationId = if (isFollowUp) habitId.hashCode() + 1 else habitId.hashCode()
        
        notificationManager.notify(notificationId, notification)
        Timber.tag("NotificationHelper").d("Уведомление показано для привычки $habitId (followUp=$isFollowUp)")
    }

    /**
     * Показывает ежедневное напоминание
     */
    fun showDailyReminderNotification(
        title: String,
        message: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "habits")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            DAILY_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HABIT_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(DAILY_REMINDER_NOTIFICATION_ID, notification)
        Timber.tag("NotificationHelper").d("Ежедневное напоминание показано")
    }

    /**
     * Отменяет уведомление для конкретной привычки
     */
    fun cancelHabitNotification(habitId: String) {
        notificationManager.cancel(habitId.hashCode())
        Timber.tag("NotificationHelper").d("Уведомление отменено для привычки $habitId")
    }

    /**
     * Проверяет, разрешены ли уведомления
     */
    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }
}
