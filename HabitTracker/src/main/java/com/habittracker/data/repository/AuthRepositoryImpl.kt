package com.habittracker.data.repository

import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.habittracker.domain.model.AccountAlreadyDeletedException
import com.habittracker.domain.model.AuthCancelledException
import com.habittracker.domain.model.InvalidCredentialsException
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.ReauthRequiredException
import com.habittracker.domain.model.SignInMethod
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.auth.UpsertSignedInUserUseCase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging,
    private val upsertSignedInUser: UpsertSignedInUserUseCase,
    private val userRepository: UserRepository,
    private val getGoogleIdOption: GetGoogleIdOption
) : AuthRepository {

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

    override fun observeCurrentUserId(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

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

    override suspend fun signInWithGoogle(activityContext: Context): Flow<Result<Intent?>> = flow {
        try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(getGoogleIdOption)
                .build()
            
            val credMgr = CredentialManager.create(activityContext)
            val result = credMgr.getCredential(
                context = activityContext,
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

    override fun currentSignInMethod(): SignInMethod? {
        val user = firebaseAuth.currentUser ?: return null
        val providers = user.providerData.map { it.providerId }
        return when {
            GoogleAuthProvider.PROVIDER_ID in providers -> SignInMethod.GOOGLE
            EmailAuthProvider.PROVIDER_ID in providers -> SignInMethod.PASSWORD
            else -> SignInMethod.OTHER
        }
    }

    override suspend fun reauthenticate(request: ReauthRequest) {
        val user = checkNotNull(firebaseAuth.currentUser) { "No signed-in user" }
        val credential = when (request) {
            is ReauthRequest.Password -> {
                val email = checkNotNull(user.email) { "Signed-in user has no email" }
                EmailAuthProvider.getCredential(email, request.password)
            }
            is ReauthRequest.Google -> requestGoogleCredential(request.activityContext)
        }
        try {
            user.reauthenticate(credential).await()
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw InvalidCredentialsException(e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw when (e.errorCode) {
                "ERROR_USER_NOT_FOUND" -> AccountAlreadyDeletedException(e)
                // Пароль тут ни при чём — пусть уходит общей ошибкой, а не «неверный пароль»
                "ERROR_USER_DISABLED", "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN" -> e
                // ERROR_USER_MISMATCH — выбран аккаунт Google, отличный от текущего
                else -> InvalidCredentialsException(e)
            }
        }
    }

    override suspend fun deleteCurrentAuthUser() {
        val user = checkNotNull(firebaseAuth.currentUser) { "No signed-in user" }
        try {
            user.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            throw ReauthRequiredException(e)
        }
    }

    private suspend fun requestGoogleCredential(activityContext: Context): AuthCredential {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(getGoogleIdOption)
            .build()
        val response = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request)
        } catch (e: GetCredentialCancellationException) {
            throw AuthCancelledException(e)
        }
        val credential = response.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type: ${credential.type}"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }
}
