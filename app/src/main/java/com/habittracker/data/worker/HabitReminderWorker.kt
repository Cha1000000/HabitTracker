package com.habittracker.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.data.notification.NotificationHelper
import com.habittracker.data.local.HabitTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Worker для отправки напоминаний о привычках
 * Умная версия - не отправляет уведомление, если привычка уже выполнена за день
 */
class HabitReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val HABIT_ID_KEY = "habit_id"
        const val HABIT_TITLE_KEY = "habit_title"
        const val HABIT_ICON_KEY = "habit_icon"
        const val USER_ID_KEY = "user_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("HabitReminderWorker", "Starting work")
            val habitId = inputData.getString(HABIT_ID_KEY)
            val habitTitle = inputData.getString(HABIT_TITLE_KEY)
            val habitIcon = inputData.getString(HABIT_ICON_KEY) ?: "🎯"
            val userId = inputData.getString(USER_ID_KEY)

            if (habitId.isNullOrBlank() || habitTitle.isNullOrBlank() || userId.isNullOrBlank()) {
                Log.e("HabitReminderWorker", "Missing habit ID, title or user ID")
                return@withContext Result.failure()
            }

            Log.d("HabitReminderWorker", "Creating database and repositories")
            // Получаем доступ к базе данных напрямую (без Hilt)
            val database = HabitTrackerDatabase.getDatabase(applicationContext)
            val habitDao = database.habitDao()
            val habitEntryDao = database.habitEntryDao()

            // Проверяем, что привычка все еще активна и напоминания включены
            val habit = habitDao.getHabitById(habitId)
            if (habit == null || !habit.isActive || !habit.reminderEnabled) {
                Log.d("HabitReminderWorker", "Habit is not active or reminders are disabled")
                return@withContext Result.success() // Привычка удалена или напоминания отключены
            }

            // Умная проверка: не отправляем уведомление, если привычка уже выполнена сегодня
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            
            Log.d("HabitReminderWorker", "Checking if habit is already completed today: $today")
            val todayEntry = habitEntryDao.getEntry(habitId, userId, today)
            
            if (todayEntry != null && todayEntry.completed) {
                Log.d("HabitReminderWorker", "Habit already completed today - skipping notification")
                return@withContext Result.success() // Привычка уже выполнена сегодня
            }

            Log.d("HabitReminderWorker", "Creating NotificationHelper")
            // Создаем NotificationHelper напрямую без Hilt
            val notificationHelper = NotificationHelper(applicationContext)

            // Проверяем разрешения на уведомления
            if (!notificationHelper.areNotificationsEnabled()) {
                Log.d("HabitReminderWorker", "Notifications are disabled by user")
                return@withContext Result.success() // Уведомления отключены пользователем
            }

            // Отправляем уведомление
            Log.d("HabitReminderWorker", "Sending notification for habit: $habitTitle")
            notificationHelper.showHabitReminderNotification(
                habitId = habitId,
                habitTitle = habitTitle,
                habitIcon = habitIcon
            )
            Log.d("HabitReminderWorker", "Notification sent successfully")

            Result.success()
        } catch (e: Exception) {
            // Логируем ошибку и возвращаем неудачу
            Log.e("HabitReminderWorker", "Error occurred while sending notification", e)
            Result.failure()
        }
    }
}
