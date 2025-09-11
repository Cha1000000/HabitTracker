package com.habittracker.data.remote.dto.auth

// Google ID Token sign-in
data class GoogleIdTokenRequest(
    val idToken: String,
    val deviceId: String? = null
)

// OAuth Authorization Code (VK, Yandex)
data class OAuthCodeRequest(
    val code: String,
    val redirectUri: String,
    val codeVerifier: String? = null,
    val deviceId: String? = null
)

// Refresh tokens
data class RefreshRequest(
    val refreshToken: String,
    val deviceId: String? = null
)

// Logout request
data class LogoutRequest(
    val refreshToken: String,
    val deviceId: String? = null
)
