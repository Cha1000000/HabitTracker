package com.habittracker.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.habittracker.data.worker.CloseExpiredChallengesWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChallengeExpirationWorkScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        private const val UNIQUE_PERIODIC_CLOSE_EXPIRED = "periodic_close_expired_challenges"
        private const val UNIQUE_ONE_TIME_CLOSE_EXPIRED = "one_time_close_expired_challenges"
        private const val TAG_CLOSE_EXPIRED = "tag_close_expired_challenges"
    }

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleDailyAt0030() {
        val request = PeriodicWorkRequestBuilder<CloseExpiredChallengesWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setInitialDelay(calcInitialDelayMsTo0030(), TimeUnit.MILLISECONDS)
            .addTag(TAG_CLOSE_EXPIRED)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_CLOSE_EXPIRED,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleOneTimeNow() {
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<CloseExpiredChallengesWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(TAG_CLOSE_EXPIRED)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_ONE_TIME_CLOSE_EXPIRED,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun calcInitialDelayMsTo0030(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
}
