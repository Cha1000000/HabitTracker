package com.habittracker.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.habittracker.MainDispatcherRule
import com.habittracker.R
import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.NoConnectionException
import com.habittracker.domain.model.NoGoogleAccountException
import com.habittracker.domain.model.PendingAccountDeletion
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.RequestRejectedException
import com.habittracker.domain.model.SignInMethod
import com.habittracker.domain.model.SyncInProgressException
import com.habittracker.domain.model.TooManyAttemptsException
import com.habittracker.domain.usecase.SignOutUseCase
import com.habittracker.domain.usecase.account.DeleteAccountUseCase
import com.habittracker.domain.usecase.account.FakeAccount
import com.habittracker.domain.usecase.account.FakeAuth
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import com.habittracker.presentation.viewmodel.DeleteAccountViewModel.Confirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class DeleteAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val calls = mutableListOf<String>()
    private val auth = FakeAuth(calls)
    private val account = FakeAccount(calls)
    private val context = mock<Context>()

    private fun viewModel() = DeleteAccountViewModel(
        authRepository = auth,
        accountRepository = account,
        deleteAccount = DeleteAccountUseCase(auth, account),
        signOutUseCase = SignOutUseCase(auth),
        analyticsManager = NoopAnalytics,
        errorLogger = NoopErrorLogger
    )

    private fun errorAfterDelete(): Int? = viewModel().run {
        delete("secret", context)
        state.value.errorRes
    }

    @Test
    fun each_failure_cause_gets_its_own_message() {
        account.remoteError = NoConnectionException()
        assertEquals(R.string.delete_account_error_network, errorAfterDelete())

        account.remoteError = RequestRejectedException()
        assertEquals(R.string.delete_account_error_rejected, errorAfterDelete())

        account.remoteError = IllegalStateException("bug")
        assertEquals(R.string.delete_account_error_generic, errorAfterDelete())

        account.remoteError = null
        account.pauseError = SyncInProgressException()
        assertEquals(R.string.delete_account_error_sync_busy, errorAfterDelete())

        account.pauseError = null
        auth.reauthError = TooManyAttemptsException()
        assertEquals(R.string.delete_account_error_too_many_requests, errorAfterDelete())

        auth.reauthError = NoGoogleAccountException()
        assertEquals(R.string.delete_account_error_no_google_account, errorAfterDelete())
    }

    @Test
    fun cloud_failure_shows_interrupted_banner() {
        account.remoteError = NoConnectionException()
        val vm = viewModel()
        assertFalse(vm.state.value.isInterrupted)

        vm.delete("secret", context)

        assertTrue(vm.state.value.isInterrupted)
    }

    @Test
    fun opened_after_killed_deletion_shows_banner() {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_IN_PROGRESS)

        assertTrue(viewModel().state.value.isInterrupted)
    }

    @Test
    fun leaving_after_partial_deletion_resumes_sync() {
        account.remoteError = NoConnectionException()
        val store = ViewModelStore()
        val vm = viewModel()
        store.put("vm", vm)
        vm.delete("secret", context)

        store.clear()

        assertNull(account.pendingDeletion())
        assertEquals("cancelPending", calls.last())
    }

    @Test
    fun leaving_without_attempt_does_not_touch_sync() {
        val store = ViewModelStore()
        store.put("vm", viewModel())

        store.clear()

        assertTrue("cancelPending" !in calls)
    }

    @Test
    fun recent_other_sign_in_deletes_without_reauth_prompt() {
        auth.signInMethod = SignInMethod.OTHER
        auth.recentSignIn = true
        val vm = viewModel()
        assertEquals(Confirmation.RECENT_SIGN_IN, vm.state.value.confirmation)

        vm.delete("", context)

        assertEquals(ReauthRequest.None, auth.lastReauth)
        assertTrue(vm.state.value.isDeleted)
    }

    @Test
    fun expired_other_sign_in_switches_to_sign_in_again() {
        auth.signInMethod = SignInMethod.OTHER
        auth.recentSignIn = true
        val vm = viewModel()
        // Пока пользователь читал экран, вход устарел
        auth.recentSignIn = false

        vm.delete("", context)

        assertEquals(Confirmation.SIGN_IN_AGAIN, vm.state.value.confirmation)
        assertTrue("deleteRemote:me" !in calls)
    }

    @Test
    fun old_other_sign_in_offers_sign_out() {
        auth.signInMethod = SignInMethod.OTHER
        val vm = viewModel()
        assertEquals(Confirmation.SIGN_IN_AGAIN, vm.state.value.confirmation)

        vm.delete("", context)
        assertTrue(calls.isEmpty())

        vm.signOut()
        assertTrue(vm.state.value.isSignedOut)
    }

    @Test
    fun auth_failure_after_cloud_deletion_switches_to_finishing_mode() {
        auth.deleteError = NoConnectionException()
        val vm = viewModel()

        vm.delete("secret", context)

        assertEquals(DeletionStage.CLOUD_DONE, vm.state.value.interruptedStage)
        assertEquals(R.string.delete_account_error_network, vm.state.value.errorRes)
        assertFalse(vm.state.value.isDeleted)
    }

    @Test
    fun leaving_after_cloud_deletion_wipes_device() {
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)
        val store = ViewModelStore()
        val vm = viewModel()
        store.put("vm", vm)

        vm.abandonAfterCloudDeletion()
        store.clear()

        assertTrue(vm.state.value.isDeleted)
        assertEquals(listOf("wipeLocal"), calls)
        assertNull(account.pendingDeletion())
    }

    @Test
    fun sign_out_after_cloud_deletion_wipes_device() {
        auth.signInMethod = SignInMethod.OTHER
        account.marker.value = PendingAccountDeletion("me", DeletionStage.CLOUD_DONE)
        val vm = viewModel()

        vm.signOut()

        assertTrue(vm.state.value.isDeleted)
        assertEquals(listOf("wipeLocal"), calls)
    }

    @Test
    fun google_account_mismatch_and_wrong_password_differ() {
        auth.reauthError = com.habittracker.domain.model.InvalidCredentialsException()
        assertEquals(R.string.delete_account_error_wrong_password, errorAfterDelete())

        auth.signInMethod = SignInMethod.GOOGLE
        assertEquals(R.string.delete_account_error_wrong_google_account, errorAfterDelete())
    }

    private object NoopAnalytics : AnalyticsManager {
        override fun logEvent(name: String, params: Map<String, Any>) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun logScreenView(screenName: String, screenClass: String?) = Unit
    }

    private object NoopErrorLogger : ErrorLogger {
        override fun logError(throwable: Throwable, message: String?) = Unit
        override fun logNonFatal(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
        override fun setUserId(userId: String) = Unit
    }
}
