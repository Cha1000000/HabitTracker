package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.AccountAlreadyDeletedException
import com.habittracker.domain.model.InvalidCredentialsException
import com.habittracker.domain.model.NotSignedInException
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.ReauthRequiredException
import com.habittracker.domain.model.SignInMethod
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DeleteAccountUseCaseTest {

    private val calls = mutableListOf<String>()
    private val auth = FakeAuth(calls)
    private val account = FakeAccount(calls)
    private val useCase = DeleteAccountUseCase(auth, account)
    private val reauth = ReauthRequest.Password("secret")

    @Test
    fun success_deletes_cloud_then_auth_then_device() = runTest {
        val result = useCase(reauth)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("reauth", "pauseSync", "deleteRemote:me", "deleteAuth", "wipeLocal", "resumeSync"),
            calls
        )
    }

    @Test
    fun cloud_failure_keeps_local_data() = runTest {
        account.remoteError = IOException("offline")

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue("wipeLocal" !in calls)
        assertTrue("deleteAuth" !in calls)
        // Очередь синхронизации не потеряна — отправка возобновлена
        assertEquals("resumeSync", calls.last())
    }

    @Test
    fun timeout_is_reported_as_failure_not_cancellation() = runTest {
        account.timeoutOnRemote = true

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
        assertTrue("wipeLocal" !in calls)
        assertEquals("resumeSync", calls.last())
    }

    @Test
    fun already_deleted_account_only_wipes_device() = runTest {
        auth.reauthError = AccountAlreadyDeletedException()

        val result = useCase(reauth)

        assertTrue(result.isSuccess)
        assertEquals(listOf("reauth", "wipeLocal"), calls)
    }

    @Test
    fun wrong_password_stops_before_any_deletion() = runTest {
        auth.reauthError = InvalidCredentialsException()

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is InvalidCredentialsException)
        assertEquals(listOf("reauth"), calls)
    }

    @Test
    fun auth_requiring_reauth_is_reported_and_local_data_kept() = runTest {
        auth.deleteError = ReauthRequiredException()

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is ReauthRequiredException)
        assertTrue("wipeLocal" !in calls)
    }

    @Test
    fun signed_out_user_fails_without_side_effects() = runTest {
        auth.userId = null

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is NotSignedInException)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun stuck_sync_stops_before_any_deletion() = runTest {
        account.pauseError = IOException("Pending sync did not finish in time")

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is IOException)
        // pauseSync сам снимает паузу при неудаче — resumeSync не нужен
        assertEquals(listOf("reauth", "pauseSync"), calls)
    }

    private class FakeAuth(private val calls: MutableList<String>) : AuthRepository {
        var userId: String? = "me"
        var reauthError: Exception? = null
        var deleteError: Exception? = null

        override fun getCurrentUserId(): String? = userId
        override fun observeCurrentUserId(): Flow<String?> = flowOf(userId)
        override fun currentSignInMethod(): SignInMethod? = SignInMethod.PASSWORD
        override suspend fun reauthenticate(request: ReauthRequest) {
            calls += "reauth"
            reauthError?.let { throw it }
        }
        override suspend fun deleteCurrentAuthUser() {
            calls += "deleteAuth"
            deleteError?.let { throw it }
        }

        override fun getCurrentUser(): Flow<User?> = flowOf(null)
        override suspend fun login(email: String, password: String) = flowOf(Result.success(Unit))
        override suspend fun register(email: String, password: String) = flowOf(Result.success(Unit))
        override suspend fun signInWithGoogle(activityContext: android.content.Context) =
            flowOf(Result.success<android.content.Intent?>(null))
        override suspend fun signOut() = flowOf(Result.success(Unit))
        override suspend fun sendPasswordResetEmail(email: String) = flowOf(Result.success(Unit))
    }

    private class FakeAccount(private val calls: MutableList<String>) : AccountRepository {
        var remoteError: Exception? = null
        var pauseError: Exception? = null

        var timeoutOnRemote = false

        override suspend fun pauseSync() {
            calls += "pauseSync"
            pauseError?.let { throw it }
        }
        override fun resumeSync() {
            calls += "resumeSync"
        }
        override suspend fun deleteRemoteUserData(userId: String) {
            calls += "deleteRemote:$userId"
            remoteError?.let { throw it }
            if (timeoutOnRemote) withTimeout(1) { awaitCancellation() }
        }
        override suspend fun wipeLocalData() {
            calls += "wipeLocal"
        }
    }
}
