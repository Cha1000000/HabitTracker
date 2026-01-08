package com.habittracker.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.habittracker.data.receiver.DailyReminderReceiver
import com.habittracker.domain.scheduler.DailyReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.LocalTime
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация планировщика ежедневных напоминаний на базе AlarmManager.
 * 
 * Используем AlarmManager вместо WorkManager, потому что:
 * 1. WorkManager имеет минимальный интервал 15 минут для периодических задач
 * 2. WorkManager не гарантирует точное время выполнения (оптимизирует батарею)
 * 3. AlarmManager с setExactAndAllowWhileIdle работает даже в Doze mode
 */
@Singleton
class DailyReminderSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DailyReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(time: LocalTime) {
        // Отменяем предыдущий alarm перед установкой нового
        cancel()
        
        val triggerTimeMillis = calculateNextTriggerTime(time)
        
        Log.d(TAG, "Scheduling daily reminder at $time (trigger in ${(triggerTimeMillis - System.currentTimeMillis()) / 1000 / 60} min)")

        val pendingIntent = createPendingIntent()

        // Используем setExactAndAllowWhileIdle для точного срабатывания даже в Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
        }
        
        Log.d(TAG, "Daily reminder scheduled successfully")
    }

    override fun scheduleIfNotSet(time: LocalTime) {
        // AlarmManager не имеет встроенного способа проверить, установлен ли alarm.
        // Используем PendingIntent.FLAG_NO_CREATE для проверки существования.
        val existingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyReminderReceiver::class.java).apply {
                action = DailyReminderReceiver.ACTION_DAILY_REMINDER
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (existingIntent != null) {
            Log.d(TAG, "Daily reminder already scheduled, skipping")
            return
        }
        
        schedule(time)
    }

    override fun cancel() {
        Log.d(TAG, "Cancelling daily reminder")
        val pendingIntent = createPendingIntent()
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    
    /**
     * Вычисляет время следующего срабатывания alarm в миллисекундах.
     * Если указанное время уже прошло сегодня, планируем на завтра.
     */
    private fun calculateNextTriggerTime(time: LocalTime): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Если время уже прошло сегодня, планируем на завтра
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return calendar.timeInMillis
    }
    
    /**
     * Создаёт PendingIntent для alarm.
     */
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = DailyReminderReceiver.ACTION_DAILY_REMINDER
        }
        
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "DailyReminderScheduler"
        private const val REQUEST_CODE = 1001
    }
}
