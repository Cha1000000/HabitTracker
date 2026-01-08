package com.habittracker.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Worker для отправки повторного напоминания (follow-up),
 * если пользователь забыл отметить привычку.
 */
class HabitFollowUpWorker(
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
            Log.d("HabitFollowUpWorker", "Starting follow-up work")
            val habitId = inputData.getString(HABIT_ID_KEY)
            val habitTitle = inputData.getString(HABIT_TITLE_KEY)
            val habitIcon = inputData.getString(HABIT_ICON_KEY) ?: "🎯"
            val userId = inputData.getString(USER_ID_KEY)

            if (habitId.isNullOrBlank() || habitTitle.isNullOrBlank() || userId.isNullOrBlank()) {
                Log.e("HabitFollowUpWorker", "Missing habit ID, title or user ID")
                return@withContext Result.failure()
            }

            // Получаем доступ к базе данных напрямую
            val database = HabitTrackerDatabase.getDatabase(applicationContext)
            val habitDao = database.habitDao()
            val habitEntryDao = database.habitEntryDao()
            val participantDao = database.habitParticipantSettingsDao()

            // 1. Проверяем, существует ли привычка и активна ли она
            val habit = habitDao.getHabitById(habitId)
            if (habit == null) {
                Log.d("HabitFollowUpWorker", "Habit not found: $habitId - skipping")
                return@withContext Result.success()
            }

            // 2. Проверяем даты активности
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val isActiveOnDate = (habit.startDate == null || habit.startDate <= today) &&
                                 (habit.endDate == null || habit.endDate >= today)
            
            if (!isActiveOnDate) {
                Log.d("HabitFollowUpWorker", "Habit expired or not started: $habitId - skipping")
                return@withContext Result.success()
            }

            // 3. Проверяем персональные настройки
            val settings = participantDao.get(habitId, userId)
            if (settings != null) {
                if (settings.notificationsMuted) {
                    Log.d("HabitFollowUpWorker", "Notifications muted by user - skipping")
                    return@withContext Result.success()
                }
                // Если пользователь отключил основное напоминание, скорее всего он не хочет и повторное
                if (!settings.reminderEnabled) {
                    Log.d("HabitFollowUpWorker", "Reminder disabled in settings - skipping")
                    return@withContext Result.success() 
                }
            } else {
                if (!habit.reminderEnabled) {
                    Log.d("HabitFollowUpWorker", "Reminder disabled in habit - skipping")
                    return@withContext Result.success()
                }
            }
            
            // Также проверяем само поле followUpReminderEnabled (глобальное для привычки)
            // В идеале оно тоже может быть персонализировано, но пока оно только в HabitEntity
            if (!habit.followUpReminderEnabled) {
                 Log.d("HabitFollowUpWorker", "Follow-up reminder disabled for habit - skipping")
                 return@withContext Result.success()
            }

            // 4. ГЛАВНАЯ ПРОВЕРКА: Выполнена ли привычка сегодня?
            val todayEntry = habitEntryDao.getEntry(habitId, userId, today)
            if (todayEntry != null && todayEntry.completed) {
                Log.d("HabitFollowUpWorker", "Habit already completed today! No follow-up needed.")
                return@withContext Result.success()
            }

            // 5. Отправляем уведомление
            val notificationHelper = NotificationHelper(applicationContext)
            if (!notificationHelper.areNotificationsEnabled()) {
                Log.d("HabitFollowUpWorker", "Notifications disabled system-wide")
                return@withContext Result.success()
            }

            Log.d("HabitFollowUpWorker", "Sending FOLLOW-UP notification for: $habitTitle")
            notificationHelper.showHabitReminderNotification(
                habitId = habitId,
                habitTitle = habitTitle,
                habitIcon = habitIcon,
                isFollowUp = true
            )

            Result.success()
        } catch (e: Exception) {
            Log.e("HabitFollowUpWorker", "Error in follow-up worker", e)
            Result.failure()
        }
    }
}
