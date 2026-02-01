package com.habittracker.presentation.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class TodayHabitsWidgetAutoRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_REFRESH_MIDNIGHT && action != ACTION_REFRESH_AFTERNOON) return

        Timber.tag(TAG).d("Auto refresh alarm received: $action")

        runCatching {
            TodayHabitsWidgetScheduler.enqueueRefresh(context)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to enqueue widget refresh: ${e.message}")
        }

        runCatching {
            TodayHabitsWidgetAutoRefreshScheduler.scheduleAll(context)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to reschedule widget auto refresh: ${e.message}")
        }
    }

    companion object {
        const val ACTION_REFRESH_MIDNIGHT = "com.habittracker.ACTION_WIDGET_REFRESH_MIDNIGHT"
        const val ACTION_REFRESH_AFTERNOON = "com.habittracker.ACTION_WIDGET_REFRESH_AFTERNOON"

        private const val TAG = "WidgetAutoRefreshRcvr"
    }
}
