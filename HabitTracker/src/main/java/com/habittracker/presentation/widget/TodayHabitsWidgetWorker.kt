package com.habittracker.presentation.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TodayHabitsWidgetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        TodayHabitsWidgetRefresh.refresh(applicationContext, force = force)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "today_habits_widget_refresh"
        const val KEY_FORCE_REFRESH = "key_force_refresh"
    }
}
