package com.habittracker.presentation.state

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