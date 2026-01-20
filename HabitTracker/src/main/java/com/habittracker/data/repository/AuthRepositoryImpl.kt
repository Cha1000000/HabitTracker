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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.auth.UpsertSignedInUserUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging,
    private val upsertSignedInUser: UpsertSignedInUserUseCase,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context,
    private val getGoogleIdOption: GetGoogleIdOption
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
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    lastActiveAt = Clock.System.now().toEpochMilliseconds()
                )
            )
        }
    }

    override fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    override suspend fun login(email: String, password: String): Flow<Result<Unit>> = flow {
        try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                val now = Clock.System.now()
                val nowMillis = now.toEpochMilliseconds()
                val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
                
                // Загружаем существующие данные пользователя напрямую из Firestore
                val existingUser = userRepository.getUserFromFirestore(firebaseUser.uid)
                
                // Если пользователь существует в Firestore, используем его данные (включая аватар)
                // Если нет - создаём нового с данными из Firebase Auth
                val user = if (existingUser != null) {
                    existingUser.copy(
                        lastActiveAt = nowMillis
                    )
                } else {
                    User(
                        id = firebaseUser.uid,
                        name = firebaseUser.displayName ?: email.substringBefore("@"),
                        email = firebaseUser.email ?: email,
                        avatarUrl = firebaseUser.photoUrl?.toString(),
                        avatarThumbnailUrl = firebaseUser.photoUrl?.toString(),
                        createdAt = today,
                        lastActiveAt = nowMillis
                    )
                }
                
                val token = try { firebaseMessaging.token.await() } catch (_: Exception) { null }
                upsertSignedInUser(user, token)
            }
            
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
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    lastActiveAt = Clock.System.now().toEpochMilliseconds()
                )

                val token = try { firebaseMessaging.token.await() } catch (_: Exception) { null }
                upsertSignedInUser(user, token)
            }
            
            emit(Result.success(Unit))
        } catch (e: Exception) {
            Timber.tag("AuthRepository").e("❌ Registration failed: ${e.message}")
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
                            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                            val result = firebaseAuth.signInWithCredential(firebaseCredential).await()
                            val firebaseUser = result.user
                            if (firebaseUser != null) {
                                val now = Clock.System.now()
                                val nowMillis = now.toEpochMilliseconds()
                                val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
                                
                                // Загружаем существующие данные пользователя из Firestore
                                val existingUser = userRepository.getUserFromFirestore(firebaseUser.uid)
                                Timber.d("signInWithGoogle: existingUser from Firestore: level=${existingUser?.level}, points=${existingUser?.points}, streak=${existingUser?.streak}")
                                
                                // Если пользователь существует в Firestore — используем его данные (level, points и т.д.)
                                // Если нет — создаём нового с данными из Google аккаунта
                                val user = if (existingUser != null) {
                                    existingUser.copy(
                                        // Обновляем только те поля, которые могут измениться в Google аккаунте
                                        name = existingUser.name.ifBlank { firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "" },
                                        email = firebaseUser.email ?: existingUser.email,
                                        avatarUrl = firebaseUser.photoUrl?.toString() ?: existingUser.avatarUrl,
                                        avatarThumbnailUrl = firebaseUser.photoUrl?.toString() ?: existingUser.avatarThumbnailUrl,
                                        lastActiveAt = nowMillis
                                    )
                                } else {
                                    User(
                                        id = firebaseUser.uid,
                                        name = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "",
                                        email = firebaseUser.email ?: "",
                                        avatarUrl = firebaseUser.photoUrl?.toString(),
                                        avatarThumbnailUrl = firebaseUser.photoUrl?.toString(),
                                        createdAt = today,
                                        lastActiveAt = nowMillis
                                    )
                                }
                                
                                val token = try { firebaseMessaging.token.await() } catch (_: Exception) { null }
                                upsertSignedInUser(user, token)
                            }
                            emit(Result.success(null)) // No Intent needed with new API
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

    override suspend fun sendPasswordResetEmail(email: String): Flow<Result<Unit>> = flow {
        try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            emit(Result.success(Unit))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}
