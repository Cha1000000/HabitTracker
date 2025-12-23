package com.habittracker.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWorkScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        private const val UNIQUE_PERIODIC_SYNC = "periodic_sync_outbox"
        private const val UNIQUE_ONE_TIME_SYNC = "one_time_sync_outbox"
        private const val TAG_OUTBOX_SYNC = "tag_outbox_sync"
    }

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            // Avoid overlap with immediate one-time sync at app start
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleOneTimeSync() {
        val builder = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(TAG_OUTBOX_SYNC)

        // Стартуем как expedited, если есть квота; иначе автоматически как non-expedited
        val request: OneTimeWorkRequest = builder
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_ONE_TIME_SYNC,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
