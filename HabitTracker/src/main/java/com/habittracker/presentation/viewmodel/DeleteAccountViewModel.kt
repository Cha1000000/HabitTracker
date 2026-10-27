package com.habittracker.presentation.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.AuthCancelledException
import com.habittracker.domain.model.InvalidCredentialsException
import com.habittracker.domain.model.NotSignedInException
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.ReauthRequiredException
import com.habittracker.domain.model.SignInMethod
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.account.DeleteAccountUseCase
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deleteAccount: DeleteAccountUseCase,
    private val analyticsManager: AnalyticsManager,
    private val errorLogger: ErrorLogger
) : ViewModel() {

    data class UiState(
        val email: String = "",
        /** Для Google пароль не нужен — подтверждение идёт через выбор аккаунта. */
        val needsPassword: Boolean = true,
        val isDeleting: Boolean = false,
        @StringRes val errorRes: Int? = null,
        val isDeleted: Boolean = false,
        /** Сессии уже нет (вышли на другом экране) — удалять нечего, уходим на вход. */
        val isSignedOut: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val method = authRepository.currentSignInMethod()
        _state.update { it.copy(needsPassword = method != SignInMethod.GOOGLE) }
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
        val reauth = if (current.needsPassword) {
            ReauthRequest.Password(password)
        } else {
            ReauthRequest.Google(activityContext)
        }
        _state.update { it.copy(isDeleting = true, errorRes = null) }
        viewModelScope.launch {
            deleteAccount(reauth)
                .onSuccess {
                    // Как при обычном выходе: удалённый uid больше не должен уходить в аналитику и отчёты
                    analyticsManager.setUserId(null)
                    errorLogger.setUserId("")
                    _state.update { it.copy(isDeleting = false, isDeleted = true) }
                }
                .onFailure { e ->
                    if (e is NotSignedInException) {
                        _state.update { it.copy(isDeleting = false, isSignedOut = true) }
                        return@onFailure
                    }
                    val errorRes = errorFor(e, current.needsPassword)
                    // Ожидаемые исходы (неверный пароль, отмена) — не ошибки приложения
                    if (errorRes == R.string.delete_account_error_generic) {
                        Timber.tag(TAG).e(e, "Account deletion failed")
                    } else {
                        Timber.tag(TAG).w(e, "Account deletion not confirmed")
                    }
                    _state.update { it.copy(isDeleting = false, errorRes = errorRes) }
                }
        }
    }

    @StringRes
    private fun errorFor(e: Throwable, needsPassword: Boolean): Int? = when (e) {
        // Пользователь сам закрыл выбор аккаунта — это не ошибка
        is AuthCancelledException -> null
        is InvalidCredentialsException ->
            if (needsPassword) R.string.delete_account_error_wrong_password
            else R.string.delete_account_error_wrong_google_account
        is ReauthRequiredException -> R.string.delete_account_error_reauth
        else -> R.string.delete_account_error_generic
    }

    private companion object {
        const val TAG = "DeleteAccountViewModel"
    }
}
