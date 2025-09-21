package com.habittracker.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun syncManager(): SyncManager
    }

    private val syncManager: SyncManager by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        entryPoint.syncManager()
    }

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
