package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.AccountAlreadyDeletedException
import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.InvalidCredentialsException
import com.habittracker.domain.model.NoConnectionException
import com.habittracker.domain.model.NotSignedInException
import com.habittracker.domain.model.PendingAccountDeletion
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.SyncInProgressException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            listOf(
                "reauth", "pauseSync", "mark:CLOUD_IN_PROGRESS", "deleteRemote:me",
                "mark:CLOUD_DONE", "deleteAuth", "wipeLocal", "resumeSync"
            ),
            calls
        )
        assertNull(account.pendingDeletion())
    }

    @Test
    fun cloud_failure_keeps_local_data() = runTest {
        account.remoteError = IOException("offline")

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue("wipeLocal" !in calls)
        assertTrue("deleteAuth" !in calls)
        assertEquals("resumeSync", calls.last())
        // Часть облака могла уже удалиться — отметка держит очередь, пока пользователь не решит
        assertEquals(DeletionStage.CLOUD_IN_PROGRESS, account.pendingDeletion()?.stage)
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
    fun auth_failure_after_cloud_deletion_keeps_device_for_retry() = runTest {
        // Устройство не стираем: пользователь в аккаунте и может повторить — иначе осталась бы
        // пустая учётка, которую уже не удалить из приложения без нового входа
        auth.deleteError = NoConnectionException()

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is NoConnectionException)
        assertTrue("wipeLocal" !in calls)
        assertEquals(DeletionStage.CLOUD_DONE, account.pendingDeletion()?.stage)
    }

    @Test
    fun retry_after_cloud_deletion_skips_cloud() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)

        val result = useCase(reauth)

        assertTrue(result.isSuccess)
        assertEquals(listOf("reauth", "deleteAuth", "wipeLocal"), calls)
        assertNull(account.pendingDeletion())
    }

    @Test
    fun auth_account_gone_after_cloud_deletion_still_wipes() = runTest {
        auth.deleteError = AccountAlreadyDeletedException()

        val result = useCase(reauth)

        assertTrue(result.isSuccess)
        assertEquals("wipeLocal", calls.dropLast(1).last())
        assertNull(account.pendingDeletion())
    }

    @Test
    fun cancelled_cloud_deletion_drops_marker() = runTest {
        account.hangOnRemote = true

        val job = launch { useCase(reauth) }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        // Отмена — как «Назад»: синк возобновлён, отметка не держит его до перезапуска
        assertNull(account.pendingDeletion())
        assertTrue("cancelPending" in calls)
        assertEquals("resumeSync", calls.last())
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
        account.pauseError = SyncInProgressException()

        val result = useCase(reauth)

        assertTrue(result.exceptionOrNull() is SyncInProgressException)
        assertNull(account.pendingDeletion())
        // pauseSync сам снимает паузу при неудаче — resumeSync не нужен
        assertEquals(listOf("reauth", "pauseSync"), calls)
    }
}
