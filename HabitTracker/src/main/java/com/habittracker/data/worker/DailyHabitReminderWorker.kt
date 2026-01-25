package com.habittracker.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.R
import com.habittracker.data.notification.NotificationHelper
import timber.log.Timber

class DailyHabitReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.tag(TAG).d("Starting DailyHabitReminderWorker")
            
            val notificationHelper = NotificationHelper(applicationContext)
            
            if (!notificationHelper.areNotificationsEnabled()) {
                Timber.tag(TAG).d("Notifications disabled, skipping")
                return Result.success()
            }

            notificationHelper.showDailyReminderNotification(
                title = applicationContext.getString(R.string.daily_reminder_title),
                message = applicationContext.getString(R.string.daily_reminder_message)
            )

            Timber.tag(TAG).d("Daily reminder sent")
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error sending daily reminder")
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "DailyHabitReminderWorker"
    }
}
