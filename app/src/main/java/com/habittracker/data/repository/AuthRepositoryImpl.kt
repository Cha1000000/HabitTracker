package com.habittracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
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
                    createdAt = kotlinx.datetime.Clock.System.now(), // Placeholder
                    lastActiveAt = kotlinx.datetime.Clock.System.now() // Placeholder
                )
            )
        }
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
            firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            // Здесь в будущем будет логика сохранения пользователя в Firestore
            emit(Result.success(Unit))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Flow<Result<Unit>> = flow {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            // Здесь также может понадобиться логика сохранения/обновления данных пользователя в Firestore
            emit(Result.success(Unit))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun logout() {
        firebaseAuth.signOut()
    }
}
