package com.habittracker.presentation.viewmodel

import android.util.Log
import com.habittracker.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.habittracker.domain.usecase.LoginUseCase
import com.habittracker.domain.usecase.RegisterUseCase
import com.habittracker.domain.usecase.SignInWithGoogleUseCase
import com.habittracker.domain.usecase.SignOutUseCase
import com.habittracker.domain.usecase.notification.ManageFCMTokenUseCase
import com.habittracker.presentation.state.AuthUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthEvent {
    data class EmailChanged(val email: String) : AuthEvent()
    data class PasswordChanged(val password: String) : AuthEvent()
    object Login : AuthEvent()
    object Register : AuthEvent()
    object SignInWithGoogle : AuthEvent()
    object SignOut : AuthEvent()
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
    object Loading : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val manageFCMTokenUseCase: ManageFCMTokenUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState = _authState.asStateFlow()

    // Single-shot guard to avoid duplicate FCM init from multiple success flows
    private var fcmInitialized: Boolean = false

    init {
        checkUserAuthentication()
    }

    private fun checkUserAuthentication() {
        viewModelScope.launch {
            if (FirebaseAuth.getInstance().currentUser != null) {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.EmailChanged -> {
                _uiState.update { it.copy(email = event.email, error = null) }
            }

            is AuthEvent.PasswordChanged -> {
                _uiState.update { it.copy(password = event.password, error = null) }
            }

            AuthEvent.Login -> {
                signIn()
            }

            AuthEvent.Register -> {
                signUp()
            }

            AuthEvent.SignInWithGoogle -> {
                signInWithGoogle()
            }
            
            AuthEvent.SignOut -> {
                signOut()
            }
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _authState.value = AuthState.Loading
            loginUseCase(email = uiState.value.email, password = uiState.value.password).collect { result ->
                result.onSuccess {
                    _authState.value = AuthState.Success
                    _uiState.update { it.copy(isLoading = false) }
                    // Единоразовая инициализация FCM токена после первого успешного входа
                    ensureInitializeFcmOnce()
                }
                result.onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Unknown error")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun signUp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _authState.value = AuthState.Loading
            registerUseCase(email = uiState.value.email, password = uiState.value.password).collect { result ->
                result.onSuccess {
                    _authState.value = AuthState.Success
                    _uiState.update { it.copy(isLoading = false) }
                    // Единоразовая инициализация FCM токена после первого успешного входа
                    ensureInitializeFcmOnce()
                }
                result.onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Unknown error")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _authState.value = AuthState.Loading
            signInWithGoogleUseCase().collect { result ->
                result.onSuccess {
                    _authState.value = AuthState.Success
                    _uiState.update { it.copy(isLoading = false) }
                    // Единоразовая инициализация FCM токена после первого успешного входа
                    ensureInitializeFcmOnce()
                }
                result.onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Google Sign-In failed")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Очищаем FCM токен перед выходом
            clearFCMToken()
            
            signOutUseCase().collect { result ->
                result.onSuccess {
                    _authState.value = AuthState.Unauthenticated
                    _uiState.update { AuthUiState() } // Сбрасываем состояние
                }
                result.onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Sign out failed")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    /**
     * Ensures FCM token is initialized only once per app session from ViewModel.
     * Application-level init also runs on startup; this is a safety net for first successful login.
     */
    private fun ensureInitializeFcmOnce() {
        if (fcmInitialized) return
        fcmInitialized = true
        initializeFCMToken()
    }

    /**
     * Инициализирует FCM токен для текущего пользователя
     */
    private fun initializeFCMToken() {
        viewModelScope.launch {
            manageFCMTokenUseCase.initializeFCMToken().onFailure { error ->
                // Логируем ошибку, но не показываем пользователю
                // FCM не критичен для основной функциональности
                if (BuildConfig.DEBUG) {
                    Log.i("AuthViewModel", "FCM init failed: ${error.message}")
                }
            }
        }
    }

    /**
     * Очищает FCM токен при выходе пользователя
     */
    private fun clearFCMToken() {
        viewModelScope.launch {
            manageFCMTokenUseCase.clearFCMToken().onFailure { error ->
                // Логируем ошибку, но не блокируем выход
                if (BuildConfig.DEBUG) {
                    Log.i("AuthViewModel", "FCM clear failed: ${error.message}")
                }
            }
        }
    }
}
