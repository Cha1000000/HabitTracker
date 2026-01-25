package com.habittracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habittracker.R
import com.habittracker.data.notification.NotificationHelper
import com.habittracker.domain.repository.UserPreferencesRepository
import com.habittracker.domain.scheduler.DailyReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

/**
 * BroadcastReceiver для обработки ежедневных напоминаний о привычках.
 * Срабатывает по AlarmManager в точное указанное время.
 * 
 * После показа уведомления перепланирует alarm на следующий день,
 * так как AlarmManager не поддерживает автоматическое повторение для exact alarms.
 */
@AndroidEntryPoint
class DailyReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var dailyReminderScheduler: DailyReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        Timber.tag(TAG).d("Daily reminder alarm received")
        
        val notificationHelper = NotificationHelper(context)
        
        if (!notificationHelper.areNotificationsEnabled()) {
            Timber.tag(TAG).d("Notifications disabled, skipping")
            rescheduleForNextDay()
            return
        }

        notificationHelper.showDailyReminderNotification(
            title = context.getString(R.string.daily_reminder_title),
            message = context.getString(R.string.daily_reminder_message)
        )

        Timber.tag(TAG).d("Daily reminder notification sent")
        
        // Перепланируем на следующий день
        rescheduleForNextDay()
    }
    
    /**
     * Перепланирует alarm на следующий день.
     * AlarmManager не поддерживает автоматическое повторение для exact alarms,
     * поэтому нужно вручную планировать каждый следующий день.
     */
    private fun rescheduleForNextDay() {
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = userPreferencesRepository.userPreferences.firstOrNull()
                if (prefs?.dailyReminderEnabled == true) {
                    // schedule() автоматически вычислит время на следующий день,
                    // так как текущее время уже прошло
                    dailyReminderScheduler.schedule(prefs.dailyReminderTime)
                    Timber.tag(TAG).d("Daily reminder rescheduled for next day at ${prefs.dailyReminderTime}")
                } else {
                    Timber.tag(TAG).d("Daily reminder is disabled, not rescheduling")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error rescheduling daily reminder")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DailyReminderReceiver"
        const val ACTION_DAILY_REMINDER = "com.habittracker.ACTION_DAILY_REMINDER"
    }
}
