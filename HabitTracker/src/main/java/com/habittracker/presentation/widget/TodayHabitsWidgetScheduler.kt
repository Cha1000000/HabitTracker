package com.habittracker.presentation.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object TodayHabitsWidgetScheduler {

    fun enqueueRefresh(context: Context, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<TodayHabitsWidgetWorker>()
            .setInputData(workDataOf(TodayHabitsWidgetWorker.KEY_FORCE_REFRESH to force))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(TodayHabitsWidgetWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
