package com.habittracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habittracker.domain.repository.UserPreferencesRepository
import com.habittracker.domain.scheduler.DailyReminderScheduler
import com.habittracker.presentation.widget.TodayHabitsWidgetAutoRefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

/**
 * BroadcastReceiver для перепланирования напоминаний после перезагрузки устройства.
 * AlarmManager теряет все запланированные alarm'ы при перезагрузке,
 * поэтому нужно их восстановить.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var dailyReminderScheduler: DailyReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Timber.tag(TAG).d("Boot completed, rescheduling daily reminder if enabled")

        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayHabitsWidgetAutoRefreshScheduler.scheduleAll(context)

                val prefs = userPreferencesRepository.userPreferences.firstOrNull()
                if (prefs?.dailyReminderEnabled == true) {
                    dailyReminderScheduler.schedule(prefs.dailyReminderTime)
                    Timber.tag(TAG).d("Daily reminder rescheduled for ${prefs.dailyReminderTime}")
                } else {
                    Timber.tag(TAG).d("Daily reminder is disabled, not scheduling")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error rescheduling daily reminder after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
