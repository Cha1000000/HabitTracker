package com.habittracker.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

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
            Timber.tag("SyncWorker").d("Start drainPending()")
            syncManager.drainPending()
            Timber.tag("SyncWorker").d("drainPending() completed")
            Result.success()
        } catch (e: Exception) {
            Timber.tag("SyncWorker").w(e, "Sync failed: ${e.message}")
            Result.retry()
        }
    }
}
