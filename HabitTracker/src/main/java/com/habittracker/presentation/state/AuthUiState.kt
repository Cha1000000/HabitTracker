package com.habittracker.presentation.state

/**
 * UI State для экранов аутентификации.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val showForgotPasswordDialog: Boolean = false,
    val forgotPasswordEmail: String = "",
    val forgotPasswordLoading: Boolean = false,
    val forgotPasswordSuccess: Boolean = false,
    val forgotPasswordError: String? = null
)