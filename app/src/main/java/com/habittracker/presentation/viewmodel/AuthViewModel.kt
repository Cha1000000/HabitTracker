package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.LoginUseCase
import com.habittracker.domain.usecase.LogoutUseCase
import com.habittracker.domain.usecase.RegisterUseCase
import com.habittracker.domain.usecase.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State для экранов аутентификации.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    val googleSignInClient: GoogleSignInClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val email = _uiState.value.email
            val password = _uiState.value.password

            loginUseCase(email, password)
                .onEach { result ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            isSuccess = result.isSuccess,
                            error = result.exceptionOrNull()?.message
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun register() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val email = _uiState.value.email
            val password = _uiState.value.password

            registerUseCase(email, password)
                .onEach { result ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            isSuccess = result.isSuccess,
                            error = result.exceptionOrNull()?.message
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            signInWithGoogleUseCase(idToken).collect { result ->
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                    },
                    onFailure = { throwable: Throwable ->
                        _uiState.update { it.copy(isLoading = false, error = throwable.message) }
                    }
                )
            }
        }
    }
}
