package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.habittracker.data.firestore.UserFirestoreConverter
import com.habittracker.data.local.dao.UserDao
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import com.habittracker.data.local.mappers.toDomain as userToDomain
import com.habittracker.data.local.mappers.toEntity as userToEntity

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) : UserRepository {
    
    override suspend fun insertUser(user: User) {
        userDao.insertUser(user.userToEntity())
    }
    
    override suspend fun updateUser(user: User) {
        userDao.updateUser(user.userToEntity())
    }
    
    override suspend fun deleteUser(userId: String) {
        val user = userDao.getUserById(userId)
        if (user != null) {
            userDao.deleteUser(user)
        }
    }
    
    override suspend fun getUserById(userId: String): User? {
        return userDao.getUserById(userId)?.userToDomain()
    }
    
    override suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)?.userToDomain()
    }
    
    override fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { entities ->
            entities.map { it.userToDomain() }
        }
    }
    
    override suspend fun getCurrentUser(): User? {
        // TODO: Implement current user logic (SharedPreferences or similar)
        return null
    }
    
    override suspend fun setCurrentUser(userId: String) {
        // TODO: Implement current user storage (SharedPreferences or similar)
    }
    
    override suspend fun searchUsersByName(query: String): List<User> {
        // Используем getAllUsers и фильтруем локально, пока не добавим метод в DAO
        val allUsers = userDao.getAllUsers()
        return allUsers.first()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.userToDomain() }
    }
    
    override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {
        userDao.updateUserPoints(userId, points, level)
    }

    override suspend fun upsertUserRemoteAndLocal(user: User, fcmToken: String?) {
        // Room-first: чтобы UI мгновенно получил пользователя
        userDao.insertUser(user.userToEntity())

        val now = System.currentTimeMillis()
        val userDoc = firestore.collection("users").document(user.id)

        val payload = hashMapOf(
            "id" to user.id,
            "name" to user.name,
            "email" to user.email,
            // nullable fields will be added below only if non-null
            "level" to user.level,
            "points" to user.points,
            "streak" to user.streak,
            "longestStreak" to user.longestStreak,
            "friendsCount" to user.friendsCount,
            "createdAt" to user.createdAt,
            "lastActiveAt" to now,
            "timezone" to user.timezone,
            "updatedAt" to now
        )
        // Only include optional fields if present to avoid overwriting with null
        user.avatarUrl?.let { payload["avatarUrl"] = it }
        user.avatarThumbnailUrl?.let { payload["avatarThumbnailUrl"] = it }

        try {
            // merge=true для UPSERT
            userDoc.set(payload, SetOptions.merge()).await()

            if (!fcmToken.isNullOrBlank()) {
                // обновляем массив токенов атомарно
                userDoc.update(mapOf(
                    "fcmTokens" to FieldValue.arrayUnion(fcmToken),
                    "updatedAt" to now
                )).await()
            }
        } catch (e: Exception) {
            Log.w("UserRepository", "upsertUserRemoteAndLocal failed (non-fatal): ${e.message}", e)
            // не падаем; WorkManager/повтор можно добавить отдельно при необходимости
        }
    }

    override fun observeUser(userId: String): Flow<User?> = callbackFlow {
        val scope = CoroutineScope(Dispatchers.IO)

        val registration = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val data = snapshot?.data
                val remote = if (data != null) UserFirestoreConverter.fromFirestore(data, snapshot.id) else null
                if (remote != null) {
                    scope.launch {
                        try { userDao.insertUser(remote.userToEntity()) } catch (e: Exception) {
                            Log.d("UserRepository", "observeUser: local insert failed (non-fatal): ${e.message}")
                        }
                    }
                }
            }

        val job = scope.launch {
            userDao.getUserByIdFlow(userId).collect { entity ->
                trySend(entity?.userToDomain())
            }
        }

        awaitClose {
            registration.remove()
            job.cancel()
            scope.cancel()
        }
    }

    override suspend fun backfillAvatarThumbnails(): Result<Int> {
        return try {
            val snapshot = firestore.collection("users").get().await()
            var updated = 0
            val now = System.currentTimeMillis()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val id = doc.id
                val avatarUrl = data["avatarUrl"] as? String
                val avatarThumb = data["avatarThumbnailUrl"] as? String
                if (!avatarUrl.isNullOrBlank() && avatarThumb.isNullOrBlank()) {
                    try {
                        // Remote update
                        doc.reference.update(mapOf(
                            "avatarThumbnailUrl" to avatarUrl,
                            "updatedAt" to now
                        )).await()
                        // Local update
                        userDao.updateUserAvatarThumbnail(id, avatarUrl)
                        updated++
                    } catch (e: Exception) {
                        Log.d("UserRepository", "backfillAvatarThumbnails: skipping doc=${id}, reason=${e.message}")
                    }
                }
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
