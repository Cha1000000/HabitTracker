package com.habittracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.habittracker.domain.repository.UserPreferencesRepository
import com.habittracker.domain.scheduler.DailyReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

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

        Log.d(TAG, "Boot completed, rescheduling daily reminder if enabled")

        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = userPreferencesRepository.userPreferences.firstOrNull()
                if (prefs?.dailyReminderEnabled == true) {
                    dailyReminderScheduler.schedule(prefs.dailyReminderTime)
                    Log.d(TAG, "Daily reminder rescheduled for ${prefs.dailyReminderTime}")
                } else {
                    Log.d(TAG, "Daily reminder is disabled, not scheduling")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling daily reminder after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
