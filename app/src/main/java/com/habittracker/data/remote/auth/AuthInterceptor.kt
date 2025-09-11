package com.habittracker.data.remote.auth

import com.habittracker.data.remote.api.AuthApi
import com.habittracker.data.remote.dto.auth.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds Authorization header and auto-refreshes access token on 401 once.
 * Skips attaching token for /api/v1/auth/ endpoints.
 */
class AuthInterceptor(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val isAuthEndpoint = path.startsWith("/api/v1/auth")

        // Attach access token if present and not an auth endpoint
        val accessToken = tokenStorage.getAccessToken()
        val request = if (!isAuthEndpoint && !accessToken.isNullOrEmpty()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        } else original

        val response = chain.proceed(request)

        // If unauthorized and not auth endpoint, try one refresh
        if (response.code == 401 && !isAuthEndpoint) {
            response.close()
            val refreshToken = tokenStorage.getRefreshToken()
            if (!refreshToken.isNullOrEmpty()) {
                val refreshed = runBlocking {
                    try {
                        authApi.refresh(RefreshRequest(refreshToken)).body()
                    } catch (_: Exception) { null }
                }
                if (refreshed != null) {
                    tokenStorage.saveTokens(refreshed.accessToken, refreshed.refreshToken)
                    val retry = original.newBuilder()
                        .removeHeader("Authorization")
                        .addHeader("Authorization", "Bearer ${refreshed.accessToken}")
                        .build()
                    return chain.proceed(retry)
                }
            }
        }
        return response
    }
}
