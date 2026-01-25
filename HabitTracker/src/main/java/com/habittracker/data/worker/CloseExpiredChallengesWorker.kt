package com.habittracker.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.domain.usecase.challenge.CloseExpiredChallengesUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

class CloseExpiredChallengesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        internal suspend fun runOnce(useCaseCall: suspend () -> kotlin.Result<Int>): Result {
            return try {
                val result = useCaseCall()
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    Timber.tag("CloseExpiredChallengesWorker").d("closed=$count")
                    Result.success()
                } else {
                    val err = result.exceptionOrNull()
                    if (err != null) {
                        Timber.tag("CloseExpiredChallengesWorker").w(err, "failed: ${err.message}")
                    } else {
                        Timber.tag("CloseExpiredChallengesWorker").w("failed")
                    }
                    Result.retry()
                }
            } catch (e: Exception) {
                Timber.tag("CloseExpiredChallengesWorker").w(e, "failed: ${e.message}")
                Result.retry()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CloseExpiredChallengesWorkerEntryPoint {
        fun closeExpiredChallengesUseCase(): CloseExpiredChallengesUseCase
    }

    private val useCase: CloseExpiredChallengesUseCase by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CloseExpiredChallengesWorkerEntryPoint::class.java
        )
        entryPoint.closeExpiredChallengesUseCase()
    }

    override suspend fun doWork(): Result {
        return runOnce { useCase() }
    }
}
