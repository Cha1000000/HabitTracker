package com.habittracker.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.habittracker.MainActivity
import com.habittracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Помощник для работы с уведомлениями о привычках
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val HABIT_REMINDER_CHANNEL_ID = "habit_reminder_channel"
        const val HABIT_REMINDER_CHANNEL_NAME = "Напоминания о привычках"
        const val HABIT_REMINDER_CHANNEL_DESCRIPTION = "Уведомления для напоминания о выполнении привычек"
        
        private const val NOTIFICATION_REQUEST_CODE = 1001
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
                HABIT_REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = HABIT_REMINDER_CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationHelper", "Уведомительный канал создан")
        }
    }

    /**
     * Показывает уведомление о напоминании привычки
     */
    fun showHabitReminderNotification(
        habitId: String,
        habitTitle: String,
        habitIcon: String = "🎯"
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("habitId", habitId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HABIT_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$habitIcon Время для привычки!")
            .setContentText(habitTitle)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Пора выполнить привычку: $habitTitle")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(habitId.hashCode(), notification)
        Log.d("NotificationHelper", "Уведомление показано для привычки $habitId")
    }

    /**
     * Отменяет уведомление для конкретной привычки
     */
    fun cancelHabitNotification(habitId: String) {
        notificationManager.cancel(habitId.hashCode())
        Log.d("NotificationHelper", "Уведомление отменено для привычки $habitId")
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
