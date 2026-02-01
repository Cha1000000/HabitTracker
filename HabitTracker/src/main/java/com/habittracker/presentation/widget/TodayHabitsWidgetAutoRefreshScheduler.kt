package com.habittracker.presentation.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TodayHabitsWidgetAutoRefreshScheduler {

    private const val TAG = "WidgetAutoRefreshSched"

    fun scheduleAll(context: Context) {
        Timber.tag(TAG).d("scheduleAll() called")
        schedule(context, TodayHabitsWidgetAutoRefreshReceiver.ACTION_REFRESH_MIDNIGHT, requestCode = 2001, hour = 0, minute = 0)
        schedule(context, TodayHabitsWidgetAutoRefreshReceiver.ACTION_REFRESH_AFTERNOON, requestCode = 2002, hour = 14, minute = 0)
    }

    private fun schedule(
        context: Context,
        action: String,
        requestCode: Int,
        hour: Int,
        minute: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerAtMillis = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TodayHabitsWidgetAutoRefreshReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Timber.tag(TAG).d("Scheduled alarm: action=$action, triggerAt=${dateFormat.format(Date(triggerAtMillis))}")
    }

    fun cancelAll(context: Context) {
        cancel(context, TodayHabitsWidgetAutoRefreshReceiver.ACTION_REFRESH_MIDNIGHT, requestCode = 2001)
        cancel(context, TodayHabitsWidgetAutoRefreshReceiver.ACTION_REFRESH_AFTERNOON, requestCode = 2002)
    }

    private fun cancel(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TodayHabitsWidgetAutoRefreshReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
