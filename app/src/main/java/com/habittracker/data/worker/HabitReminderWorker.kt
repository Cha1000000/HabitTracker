package com.habittracker.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.data.notification.NotificationHelper
import com.habittracker.domain.repository.HabitRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker для отправки напоминаний о привычках
 */
@HiltWorker
class HabitReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val habitRepository: HabitRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val HABIT_ID_KEY = "habit_id"
        const val HABIT_TITLE_KEY = "habit_title"
        const val HABIT_ICON_KEY = "habit_icon"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val habitId = inputData.getString(HABIT_ID_KEY)
            val habitTitle = inputData.getString(HABIT_TITLE_KEY)
            val habitIcon = inputData.getString(HABIT_ICON_KEY) ?: "🎯"

            if (habitId.isNullOrBlank() || habitTitle.isNullOrBlank()) {
                return@withContext Result.failure()
            }

            // Проверяем, что привычка все еще активна и напоминания включены
            val habit = habitRepository.getHabitById(habitId)
            if (habit == null || !habit.isActive || !habit.reminderEnabled) {
                return@withContext Result.success() // Привычка удалена или напоминания отключены
            }

            // Проверяем разрешения на уведомления
            if (!notificationHelper.areNotificationsEnabled()) {
                return@withContext Result.success() // Уведомления отключены пользователем
            }

            // Отправляем уведомление
            notificationHelper.showHabitReminderNotification(
                habitId = habitId,
                habitTitle = habitTitle,
                habitIcon = habitIcon
            )

            Result.success()
        } catch (e: Exception) {
            // Логируем ошибку и возвращаем неудачу
            e.printStackTrace()
            Result.failure()
        }
    }
}
