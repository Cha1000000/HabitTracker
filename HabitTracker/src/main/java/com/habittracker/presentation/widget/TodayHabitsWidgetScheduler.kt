package com.habittracker.presentation.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object TodayHabitsWidgetScheduler {

    fun enqueueRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<TodayHabitsWidgetWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(TodayHabitsWidgetWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
