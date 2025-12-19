package com.habittracker.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.domain.usecase.challenge.CloseExpiredChallengesUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

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
                    Log.d("CloseExpiredChallengesWorker", "closed=$count")
                    Result.success()
                } else {
                    Log.w(
                        "CloseExpiredChallengesWorker",
                        "failed: ${result.exceptionOrNull()?.message}",
                        result.exceptionOrNull()
                    )
                    Result.retry()
                }
            } catch (e: Exception) {
                Log.w("CloseExpiredChallengesWorker", "failed: ${e.message}", e)
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
