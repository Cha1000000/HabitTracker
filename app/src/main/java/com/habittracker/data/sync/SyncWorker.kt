package com.habittracker.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "Start drainPending()")
            syncManager.drainPending()
            Log.d("SyncWorker", "drainPending() completed")
            Result.success()
        } catch (e: Exception) {
            Log.w("SyncWorker", "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
