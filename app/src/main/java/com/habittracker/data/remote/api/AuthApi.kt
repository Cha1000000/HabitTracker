package com.habittracker.data.remote.api

import com.habittracker.data.remote.dto.auth.AuthResponseDto
import com.habittracker.data.remote.dto.auth.GoogleIdTokenRequest
import com.habittracker.data.remote.dto.auth.LogoutRequest
import com.habittracker.data.remote.dto.auth.OAuthCodeRequest
import com.habittracker.data.remote.dto.auth.RefreshRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/google-idtoken")
    suspend fun signInWithGoogleIdToken(@Body body: GoogleIdTokenRequest): Response<AuthResponseDto>

    @POST("api/v1/auth/vk")
    suspend fun signInWithVk(@Body body: OAuthCodeRequest): Response<AuthResponseDto>

    @POST("api/v1/auth/yandex")
    suspend fun signInWithYandex(@Body body: OAuthCodeRequest): Response<AuthResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<AuthResponseDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<Unit>
}
