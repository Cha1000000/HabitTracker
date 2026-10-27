package com.habittracker.presentation.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.AuthCancelledException
import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.InvalidCredentialsException
import com.habittracker.domain.model.NoConnectionException
import com.habittracker.domain.model.NoGoogleAccountException
import com.habittracker.domain.model.RequestRejectedException
import com.habittracker.domain.model.SyncInProgressException
import com.habittracker.domain.model.TooManyAttemptsException
import com.habittracker.domain.model.NotSignedInException
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.ReauthRequiredException
import com.habittracker.domain.model.SignInMethod
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.SignOutUseCase
import com.habittracker.domain.usecase.account.DeleteAccountUseCase
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val deleteAccount: DeleteAccountUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val analyticsManager: AnalyticsManager,
    private val errorLogger: ErrorLogger
) : ViewModel() {

    /** Чем пользователь подтверждает, что это он. */
    enum class Confirmation {
        PASSWORD,

        /** Пароль не нужен — подтверждение идёт через выбор аккаунта Google. */
        GOOGLE,

        /** Вход незнакомым способом, но только что — Firebase удалит без повторного входа. */
        RECENT_SIGN_IN,

        /** Вход незнакомым способом и давно — подтвердить нечем, нужно выйти и войти снова. */
        SIGN_IN_AGAIN
    }

    data class UiState(
        val email: String = "",
        val confirmation: Confirmation = Confirmation.PASSWORD,
        val isDeleting: Boolean = false,
        @StringRes val errorRes: Int? = null,
        val isDeleted: Boolean = false,
        /** Сессии уже нет (вышли на другом экране) — удалять нечего, уходим на вход. */
        val isSignedOut: Boolean = false,
        /**
         * Стадия прерванного удаления, `null` — удаление не начиналось.
         * - `CLOUD_IN_PROGRESS`: часть данных могла уже пропасть. Уход с экрана отменяет
         *   удаление, повтор — доводит его до конца.
         * - `CLOUD_DONE`: облако уже чистое, осталась учётка в Auth. Повтор удаляет её, а уход
         *   с экрана стирает устройство — вернуться в пустой аккаунт нельзя.
         */
        val interruptedStage: DeletionStage? = null
    ) {
        val isInterrupted: Boolean get() = interruptedStage != null
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val confirmation = when (authRepository.currentSignInMethod()) {
            SignInMethod.GOOGLE -> Confirmation.GOOGLE
            SignInMethod.OTHER ->
                if (authRepository.isRecentSignIn()) Confirmation.RECENT_SIGN_IN else Confirmation.SIGN_IN_AGAIN
            // null — сессии уже нет; удаление само скажет об этом
            SignInMethod.PASSWORD, null -> Confirmation.PASSWORD
        }
        _state.update {
            it.copy(
                confirmation = confirmation,
                interruptedStage = accountRepository.pendingDeletion()?.stage
            )
        }
        viewModelScope.launch {
            val email = try {
                authRepository.getCurrentUser().first()?.email.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load current user email")
                ""
            }
            _state.update { it.copy(email = email) }
        }
    }

    /** [activityContext] используется только на время вызова — для окна выбора аккаунта Google. */
    fun delete(password: String, activityContext: Context) {
        val current = _state.value
        if (current.isDeleting) return
        val reauth = when (current.confirmation) {
            Confirmation.PASSWORD -> ReauthRequest.Password(password)
            Confirmation.GOOGLE -> ReauthRequest.Google(activityContext)
            Confirmation.RECENT_SIGN_IN -> ReauthRequest.None
            Confirmation.SIGN_IN_AGAIN -> return
        }
        _state.update { it.copy(isDeleting = true, errorRes = null) }
        viewModelScope.launch {
            deleteAccount(reauth)
                .onSuccess { onDeviceWiped() }
                .onFailure { e ->
                    if (e is NotSignedInException) {
                        _state.update { it.copy(isDeleting = false, isSignedOut = true) }
                        return@onFailure
                    }
                    if (e is ReauthRequiredException && current.confirmation == Confirmation.RECENT_SIGN_IN) {
                        // Вход успел устареть, а подтвердить его здесь нечем — только выйти и войти
                        Timber.tag(TAG).w(e, "Recent sign-in expired before deletion")
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                confirmation = Confirmation.SIGN_IN_AGAIN,
                                interruptedStage = accountRepository.pendingDeletion()?.stage
                            )
                        }
                        return@onFailure
                    }
                    val errorRes = errorFor(e, current.confirmation)
                    // Ожидаемые исходы (неверный пароль, нет сети, отмена) — не ошибки приложения
                    if (errorRes in UNEXPECTED_ERRORS) {
                        Timber.tag(TAG).e(e, "Account deletion failed")
                    } else {
                        Timber.tag(TAG).w(e, "Account deletion not confirmed")
                    }
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            errorRes = errorRes,
                            interruptedStage = accountRepository.pendingDeletion()?.stage
                        )
                    }
                }
        }
    }

    /**
     * Уход с экрана, когда облако уже удалено: аккаунт пустой, оставлять в нём пользователя
     * незачем. Стираем устройство; учётка в Auth остаётся, её удалит повторный вход и удаление.
     */
    fun abandonAfterCloudDeletion() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true, errorRes = null) }
        viewModelScope.launch {
            Timber.tag(TAG).w("Deletion abandoned after cloud deletion, Auth account is left")
            withContext(NonCancellable) { accountRepository.wipeLocalData() }
            onDeviceWiped()
        }
    }

    /** Для входа, который нечем подтвердить: выйти, чтобы войти заново и вернуться сюда. */
    fun signOut() {
        if (_state.value.isDeleting) return
        // Облако уже пустое — выход без стирания оставил бы на устройстве данные удалённого аккаунта
        if (accountRepository.pendingDeletion()?.stage == DeletionStage.CLOUD_DONE) {
            abandonAfterCloudDeletion()
            return
        }
        viewModelScope.launch {
            signOutUseCase().first()
                .onSuccess {
                    analyticsManager.setUserId(null)
                    errorLogger.setUserId("")
                    _state.update { it.copy(isSignedOut = true) }
                }
                .onFailure { e ->
                    Timber.tag(TAG).e(e, "Sign out failed")
                    _state.update { it.copy(errorRes = R.string.delete_account_error_generic) }
                }
        }
    }

    private fun onDeviceWiped() {
        // Как при обычном выходе: удалённый uid больше не должен уходить в аналитику и отчёты
        analyticsManager.setUserId(null)
        errorLogger.setUserId("")
        _state.update { it.copy(isDeleting = false, isDeleted = true) }
    }

    override fun onCleared() {
        // Ушли с экрана, не закончив удаление, — пользователь передумал: возвращаем синхронизацию.
        // Во время удаления не трогаем: процесс могут убить, и отметка нужна на следующем старте
        val current = _state.value
        // После CLOUD_DONE отказаться уже нельзя: облако пустое, удаление доведёт следующий старт
        val canCancel = accountRepository.pendingDeletion()?.stage == DeletionStage.CLOUD_IN_PROGRESS
        if (!current.isDeleting && !current.isDeleted && canCancel) {
            Timber.tag(TAG).w("Account deletion abandoned after partial cloud deletion")
            accountRepository.cancelPendingDeletion()
        }
    }

    @StringRes
    private fun errorFor(e: Throwable, confirmation: Confirmation): Int? = when (e) {
        // Пользователь сам закрыл выбор аккаунта — это не ошибка
        is AuthCancelledException -> null
        is InvalidCredentialsException ->
            if (confirmation == Confirmation.GOOGLE) R.string.delete_account_error_wrong_google_account
            else R.string.delete_account_error_wrong_password
        is ReauthRequiredException -> R.string.delete_account_error_reauth
        // Таймаут, не переведённый в NoConnectionException ниже по стеку, — та же нехватка сети
        is NoConnectionException, is TimeoutCancellationException -> R.string.delete_account_error_network
        is TooManyAttemptsException -> R.string.delete_account_error_too_many_requests
        is NoGoogleAccountException -> R.string.delete_account_error_no_google_account
        is SyncInProgressException -> R.string.delete_account_error_sync_busy
        is RequestRejectedException -> R.string.delete_account_error_rejected
        else -> R.string.delete_account_error_generic
    }

    private companion object {
        const val TAG = "DeleteAccountViewModel"

        /** Эти исходы стоит увидеть в Crashlytics: пользователь тут ни при чём. */
        val UNEXPECTED_ERRORS = setOf(
            R.string.delete_account_error_generic,
            R.string.delete_account_error_rejected
        )
    }
}
