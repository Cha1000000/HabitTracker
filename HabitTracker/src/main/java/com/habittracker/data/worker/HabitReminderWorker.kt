package com.habittracker.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber

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
            Timber.tag("HabitReminderWorker").d("Starting work")
            val habitId = inputData.getString(HABIT_ID_KEY)
            val habitTitle = inputData.getString(HABIT_TITLE_KEY)
            val habitIcon = inputData.getString(HABIT_ICON_KEY) ?: "🎯"
            val userId = inputData.getString(USER_ID_KEY)

            if (habitId.isNullOrBlank() || habitTitle.isNullOrBlank() || userId.isNullOrBlank()) {
                Timber.tag("HabitReminderWorker").e("Missing habit ID, title or user ID")
                return@withContext Result.failure()
            }

            Timber.tag("HabitReminderWorker").d("Creating database and repositories")
            // Получаем доступ к базе данных напрямую (без Hilt)
            val database = HabitTrackerDatabase.getDatabase(applicationContext)
            val habitDao = database.habitDao()
            val habitEntryDao = database.habitEntryDao()
            val participantDao = database.habitParticipantSettingsDao()

            // Проверяем, что привычка все еще активна и имеет время напоминания (основной источник истины)
            val habit = habitDao.getHabitById(habitId)
            if (habit == null) {
                // После удаления или миграций привычка может отсутствовать локально.
                // В таком случае НЕ отправляем уведомление.
                Timber.tag("HabitReminderWorker").d("Habit not found in local DB: $habitId — skip notification")
                return@withContext Result.success()
            }
            Timber.tag("HabitReminderWorker").d(
                "Loaded habit from DB: id=${habit.id}, isActive=${habit.isActive}, reminderEnabled=${habit.reminderEnabled}, reminderTime=${habit.reminderTime}, startDate=${habit.startDate}, endDate=${habit.endDate}"
            )
            
            // Проверяем, что привычка активна на текущую дату (учитывая startDate и endDate)
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val isActiveOnDate = (habit.startDate == null || habit.startDate <= today) &&
                                 (habit.endDate == null || habit.endDate >= today)
            if (!isActiveOnDate) {
                Timber.tag("HabitReminderWorker").d("Habit is not active on $today (startDate=${habit.startDate}, endDate=${habit.endDate}) — skip notification")
                return@withContext Result.success()
            }

            // Загружаем персональные настройки участника, если есть
            val settings = participantDao.get(habitId, userId)
            if (settings != null) {
                Timber.tag("HabitReminderWorker").d("Participant settings loaded: enabled=${settings.reminderEnabled}, rt=${settings.reminderTime}, muted=${settings.notificationsMuted}")
                if (settings.notificationsMuted) {
                    Timber.tag("HabitReminderWorker").d("Notifications muted by user — skipping notification")
                    return@withContext Result.success()
                }
                if (!habit.isActive || settings.reminderTime == null || !settings.reminderEnabled) {
                    Timber.tag("HabitReminderWorker").d("Habit inactive or participant reminder off — skipping notification")
                    return@withContext Result.success()
                }
            } else {
                if (!habit.isActive || habit.reminderTime == null) {
                    Timber.tag("HabitReminderWorker").d("Habit inactive or reminderTime is null — skipping notification")
                    return@withContext Result.success()
                }
            }

            // Умная проверка: не отправляем уведомление, если привычка уже выполнена сегодня
            Timber.tag("HabitReminderWorker").d("Checking if habit is already completed today: $today")
            val todayEntry = habitEntryDao.getEntry(habitId, userId, today)
            
            if (todayEntry != null && todayEntry.completed) {
                Timber.tag("HabitReminderWorker").d("Habit already completed today - skipping notification")
                return@withContext Result.success() // Привычка уже выполнена сегодня
            }

            Timber.tag("HabitReminderWorker").d("Creating NotificationHelper")
            // Создаем NotificationHelper напрямую без Hilt
            val notificationHelper = NotificationHelper(applicationContext)

            // Проверяем разрешения на уведомления
            if (!notificationHelper.areNotificationsEnabled()) {
                Timber.tag("HabitReminderWorker").d("Notifications are disabled by user")
                return@withContext Result.success() // Уведомления отключены пользователем
            }

            // Отправляем уведомление
            Timber.tag("HabitReminderWorker").d("Sending notification for habit: $habitTitle")
            notificationHelper.showHabitReminderNotification(
                habitId = habitId,
                habitTitle = habitTitle,
                habitIcon = habitIcon
            )
            Timber.tag("HabitReminderWorker").d("Notification sent successfully")

            Result.success()
        } catch (e: Exception) {
            // Логируем ошибку и возвращаем неудачу
            Timber.tag("HabitReminderWorker").e(e, "Error occurred while sending notification")
            Result.failure()
        }
    }
}
