package com.habittracker.data.repository

import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.habittracker.data.remote.api.AuthApi
import com.habittracker.data.remote.auth.TokenStorage
import com.habittracker.data.remote.dto.auth.AuthResponseDto
import com.habittracker.data.remote.dto.auth.GoogleIdTokenRequest
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.auth.UpsertSignedInUserUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging,
    private val upsertSignedInUser: UpsertSignedInUserUseCase,
    @ApplicationContext private val context: Context,
    private val getGoogleIdOption: GetGoogleIdOption,
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    override fun getCurrentUser(): Flow<User?> = flow {
        // Эта логика будет усложнена, когда мы добавим Firestore
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser == null) {
            emit(null)
        } else {
            // Временная заглушка, пока нет данных в Firestore
            emit(
                User(
                    id = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    avatarUrl = firebaseUser.photoUrl?.toString(),
                    avatarThumbnailUrl = firebaseUser.photoUrl?.toString(),
                    createdAt = System.currentTimeMillis(), // Placeholder
                    lastActiveAt = System.currentTimeMillis() // Placeholder
                )
            )
        }
    }

    override fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    override suspend fun login(email: String, password: String): Flow<Result<Unit>> = flow {
        try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            emit(Result.success(Unit))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun register(email: String, password: String): Flow<Result<Unit>> = flow {
        try {
            // Создаем аккаунт в Firebase Authentication
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                val user = User(
                    id = firebaseUser.uid,
                    name = firebaseUser.displayName ?: email.substringBefore("@"),
                    email = email,
                    avatarUrl = firebaseUser.photoUrl?.toString(),
                    avatarThumbnailUrl = firebaseUser.photoUrl?.toString(),
                    createdAt = System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis()
                )

                val token = try { firebaseMessaging.token.await() } catch (e: Exception) { null }
                upsertSignedInUser(user, token)
            }
            
            emit(Result.success(Unit))
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "❌ Registration failed: ${e.message}")
            emit(Result.failure(e))
        }
    }

    override suspend fun signInWithGoogle(): Flow<Result<Intent?>> = flow {
        try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(getGoogleIdOption)
                .build()
            
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            
            when (val credential = result.credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val idToken = googleIdTokenCredential.idToken
                            // Call backend with Google ID Token
                            val response = authApi.signInWithGoogleIdToken(GoogleIdTokenRequest(idToken))
                            if (response.isSuccessful) {
                                val body: AuthResponseDto? = response.body()
                                if (body != null) {
                                    // Save tokens
                                    tokenStorage.saveTokens(body.accessToken, body.refreshToken)
                                    // Map backend user to domain
                                    val user = User(
                                        id = body.user.id,
                                        name = body.user.name,
                                        email = body.user.email,
                                        avatarUrl = body.user.avatarUrl,
                                        avatarThumbnailUrl = body.user.avatarThumbnailUrl,
                                        createdAt = body.user.createdAt,
                                        lastActiveAt = body.user.lastActiveAt
                                    )
                                    val fcmToken = try { firebaseMessaging.token.await() } catch (e: Exception) { null }
                                    upsertSignedInUser(user, fcmToken)
                                    emit(Result.success(null))
                                } else {
                                    emit(Result.failure(Exception("Empty auth response")))
                                }
                            } else {
                                emit(Result.failure(Exception("Auth failed: ${response.code()}")))
                            }
                        } catch (e: GoogleIdTokenParsingException) {
                            emit(Result.failure(Exception("Failed to parse Google ID token", e)))
                        }
                    } else {
                        emit(Result.failure(Exception("Unexpected credential type: ${credential.type}")))
                    }
                }
                else -> {
                    emit(Result.failure(Exception("Unexpected credential type")))
                }
            }
        } catch (e: GetCredentialException) {
            emit(Result.failure(e))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun signOut(): Flow<Result<Unit>> = flow {
        try {
            firebaseAuth.signOut()
            // Note: With new Credential Manager API, we don't need to sign out from Google client
            emit(Result.success(Unit))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}
