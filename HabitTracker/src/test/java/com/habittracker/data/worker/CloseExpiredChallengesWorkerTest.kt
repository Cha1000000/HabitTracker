package com.habittracker.data.worker

import androidx.work.ListenableWorker
import com.habittracker.data.worker.CloseExpiredChallengesWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseExpiredChallengesWorkerTest {

    @Test
    fun runOnce_success_returnsSuccess() = runTest {
        val result = CloseExpiredChallengesWorker.runOnce { Result.success(1) }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun runOnce_failureResult_returnsRetry() = runTest {
        val result = CloseExpiredChallengesWorker.runOnce { Result.failure(Exception("boom")) }
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun runOnce_exception_returnsRetry() = runTest {
        val result = CloseExpiredChallengesWorker.runOnce { throw RuntimeException("boom") }
        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
