package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.BuildConfig
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.LoginUseCase
import com.habittracker.domain.usecase.RegisterUseCase
import com.habittracker.domain.usecase.SendPasswordResetEmailUseCase
import com.habittracker.domain.usecase.SignInWithGoogleUseCase
import com.habittracker.domain.usecase.SignOutUseCase
import com.habittracker.domain.usecase.notification.ManageFCMTokenUseCase
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import com.habittracker.presentation.state.AuthUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class AuthEvent {
    data class EmailChanged(val email: String) : AuthEvent()
    data class PasswordChanged(val password: String) : AuthEvent()
    object Login : AuthEvent()
    object Register : AuthEvent()
    object SignInWithGoogle : AuthEvent()
    object SignOut : AuthEvent()
    object ShowForgotPasswordDialog : AuthEvent()
    object HideForgotPasswordDialog : AuthEvent()
    data class ForgotPasswordEmailChanged(val email: String) : AuthEvent()
    object SendPasswordResetEmail : AuthEvent()
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
    private val sendPasswordResetEmailUseCase: SendPasswordResetEmailUseCase,
    private val manageFCMTokenUseCase: ManageFCMTokenUseCase,
    private val authRepository: AuthRepository,
    private val analyticsManager: AnalyticsManager,
    private val errorLogger: ErrorLogger
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
            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                analyticsManager.setUserId(userId)
                errorLogger.setUserId(userId)
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
            
            AuthEvent.ShowForgotPasswordDialog -> {
                _uiState.update { 
                    it.copy(
                        showForgotPasswordDialog = true,
                        forgotPasswordEmail = it.email,
                        forgotPasswordSuccess = false,
                        forgotPasswordError = null
                    ) 
                }
            }
            
            AuthEvent.HideForgotPasswordDialog -> {
                _uiState.update { 
                    it.copy(
                        showForgotPasswordDialog = false,
                        forgotPasswordEmail = "",
                        forgotPasswordLoading = false,
                        forgotPasswordSuccess = false,
                        forgotPasswordError = null
                    ) 
                }
            }
            
            is AuthEvent.ForgotPasswordEmailChanged -> {
                _uiState.update { it.copy(forgotPasswordEmail = event.email, forgotPasswordError = null) }
            }
            
            AuthEvent.SendPasswordResetEmail -> {
                sendPasswordResetEmail()
            }
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _authState.value = AuthState.Loading
            loginUseCase(email = uiState.value.email, password = uiState.value.password).collect { result ->
                result.onSuccess {
                    val userId = authRepository.getCurrentUserId()
                    analyticsManager.setUserId(userId)
                    errorLogger.setUserId(userId ?: "")
                    analyticsManager.logEvent(AnalyticsEvents.LOGIN)
                    
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
                    val userId = authRepository.getCurrentUserId()
                    analyticsManager.setUserId(userId)
                    errorLogger.setUserId(userId ?: "")
                    analyticsManager.logEvent(AnalyticsEvents.SIGN_UP)
                    
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
                    val userId = authRepository.getCurrentUserId()
                    analyticsManager.setUserId(userId)
                    errorLogger.setUserId(userId ?: "")
                    analyticsManager.logEvent(AnalyticsEvents.LOGIN, mapOf("method" to "google"))
                    
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
                    analyticsManager.logEvent(AnalyticsEvents.LOGOUT)
                    analyticsManager.setUserId(null)
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

    private fun sendPasswordResetEmail() {
        val email = uiState.value.forgotPasswordEmail.trim()
        if (email.isEmpty()) {
            _uiState.update { it.copy(forgotPasswordError = "Введите email") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(forgotPasswordLoading = true, forgotPasswordError = null) }
            sendPasswordResetEmailUseCase(email).collect { result ->
                result.onSuccess {
                    _uiState.update { 
                        it.copy(
                            forgotPasswordLoading = false,
                            forgotPasswordSuccess = true
                        ) 
                    }
                }
                result.onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            forgotPasswordLoading = false,
                            forgotPasswordError = error.message ?: "Ошибка отправки письма"
                        ) 
                    }
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
                    Timber.tag("AuthViewModel").i("FCM init failed: ${error.message}")
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
                    Timber.tag("AuthViewModel").i("FCM clear failed: ${error.message}")
                }
            }
        }
    }
}
