package com.habittracker.presentation.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class TodayHabitsWidgetAutoRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_REFRESH_MIDNIGHT &&
            action != ACTION_REFRESH_AFTERNOON &&
            action != ACTION_FORCE_REFRESH_MORNING
        ) return

        Timber.tag(TAG).d("Auto refresh alarm received: $action")

        runCatching {
            val force = action == ACTION_FORCE_REFRESH_MORNING
            TodayHabitsWidgetScheduler.enqueueRefresh(context, force = force)
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
        const val ACTION_FORCE_REFRESH_MORNING = "com.habittracker.ACTION_WIDGET_FORCE_REFRESH_MORNING"

        private const val TAG = "WidgetAutoRefreshRcvr"
    }
}
