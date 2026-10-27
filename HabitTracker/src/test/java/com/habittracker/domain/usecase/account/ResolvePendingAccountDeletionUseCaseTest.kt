package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.AccountAlreadyDeletedException
import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.NoConnectionException
import com.habittracker.domain.model.PendingAccountDeletion
import com.habittracker.domain.model.ReauthRequiredException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Процесс убит посреди удаления: что делает следующий запуск на каждой стадии. */
class ResolvePendingAccountDeletionUseCaseTest {

    private val calls = mutableListOf<String>()
    private val auth = FakeAuth(calls)
    private val account = FakeAccount(calls)
    private val resolve = ResolvePendingAccountDeletionUseCase(auth, account)

    @Test
    fun no_marker_is_a_normal_start() = runTest {
        assertEquals(PendingDeletionOutcome.NONE, resolve())
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun killed_after_cloud_deletion_finishes_auth_and_wipes() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)

        assertEquals(PendingDeletionOutcome.FINISHED, resolve())
        assertEquals(listOf("deleteAuth", "wipeLocal"), calls)
        assertNull(account.pendingDeletion())
    }

    @Test
    fun auth_failure_after_cloud_deletion_asks_user() = runTest {
        // Вход устарел — удалить учётку можно только после подтверждения на экране удаления
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)
        auth.deleteError = ReauthRequiredException()

        assertEquals(PendingDeletionOutcome.NEEDS_CONFIRMATION, resolve())
        assertEquals(listOf("deleteAuth"), calls)
        assertEquals(DeletionStage.CLOUD_DONE, account.pendingDeletion()?.stage)
    }

    @Test
    fun network_failure_after_cloud_deletion_asks_user() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)
        auth.deleteError = NoConnectionException()

        assertEquals(PendingDeletionOutcome.NEEDS_CONFIRMATION, resolve())
        assertTrue("wipeLocal" !in calls)
    }

    @Test
    fun auth_account_already_gone_after_cloud_deletion_wipes() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)
        auth.deleteError = AccountAlreadyDeletedException()

        assertEquals(PendingDeletionOutcome.FINISHED, resolve())
        assertEquals(listOf("deleteAuth", "wipeLocal"), calls)
    }

    @Test
    fun killed_during_cloud_deletion_asks_user() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_IN_PROGRESS)

        assertEquals(PendingDeletionOutcome.NEEDS_CONFIRMATION, resolve())
        // Отметка остаётся: очередь синхронизации заблокирована, пока пользователь не решит
        assertEquals(DeletionStage.CLOUD_IN_PROGRESS, account.pendingDeletion()?.stage)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun account_deleted_elsewhere_during_cloud_deletion_wipes() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_IN_PROGRESS)
        auth.deletedOnServer = true

        assertEquals(PendingDeletionOutcome.FINISHED, resolve())
        assertEquals(listOf("wipeLocal"), calls)
    }

    @Test
    fun signed_out_after_cloud_deletion_wipes_device() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)
        auth.userId = null

        assertEquals(PendingDeletionOutcome.FINISHED, resolve())
        assertEquals(listOf("wipeLocal"), calls)
    }

    @Test
    fun signed_out_during_cloud_deletion_drops_marker() = runTest {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_IN_PROGRESS)
        auth.userId = null

        assertEquals(PendingDeletionOutcome.NONE, resolve())
        assertEquals(listOf("cancelPending"), calls)
    }

    @Test
    fun marker_of_another_user_mid_deletion_is_dropped_without_touching_data() = runTest {
        account.marker.value = PendingAccountDeletion("someone", DeletionStage.CLOUD_IN_PROGRESS)

        assertEquals(PendingDeletionOutcome.NONE, resolve())
        assertEquals(listOf("cancelPending"), calls)
    }

    @Test
    fun data_of_deleted_account_under_another_session_is_wiped() = runTest {
        // Прошлое стирание сорвалось: облако того аккаунта пустое, а данные на устройстве остались
        account.marker.value = PendingAccountDeletion("someone", DeletionStage.CLOUD_DONE)

        assertEquals(PendingDeletionOutcome.FINISHED, resolve())
        assertEquals(listOf("wipeLocal"), calls)
    }
}
