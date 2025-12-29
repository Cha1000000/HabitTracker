package com.habittracker.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.R
import com.habittracker.data.notification.NotificationHelper

class DailyHabitReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting DailyHabitReminderWorker")
            
            val notificationHelper = NotificationHelper(applicationContext)
            
            if (!notificationHelper.areNotificationsEnabled()) {
                Log.d(TAG, "Notifications disabled, skipping")
                return Result.success()
            }

            notificationHelper.showDailyReminderNotification(
                title = applicationContext.getString(R.string.daily_reminder_title),
                message = applicationContext.getString(R.string.daily_reminder_message)
            )

            Log.d(TAG, "Daily reminder sent")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending daily reminder", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "DailyHabitReminderWorker"
    }
}
