package com.habittracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.firestore.UserFirestoreConverter
import com.habittracker.data.local.dao.UserDao
import com.habittracker.data.local.entity.UserEntity
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.util.SyncPayloads
import com.habittracker.domain.util.UserSearchKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import timber.log.Timber

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val firestore: FirebaseFirestore
) : UserRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun kickSync() {
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) {
        }
        repoScope.launch {
            try { syncManager.drainPending(maxBatch = 10) } catch (_: Exception) {
            }
        }
    }
    
    private suspend fun upsertLocal(entity: UserEntity) {
        val existing = userDao.getUserById(entity.id)
        if (existing == null) {
            userDao.insertUser(entity)
        } else {
            userDao.updateUser(entity)
        }
    }
    
    override suspend fun insertUser(user: User) {
        userDao.insertUser(user.userToEntity())
    }
    
    override suspend fun updateUser(user: User) {
        // Обновляем локальную БД
        userDao.updateUser(user.userToEntity())
        
        // Синхронизируем с Firestore через Outbox
        val now = System.currentTimeMillis()
        val json = SyncPayloads.userToJson(user, now)
        syncManager.enqueueUpsert(
            entityType = "user",
            entityId = user.id,
            payloadJson = json,
            updatedAt = now
        )

        kickSync()
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
    
    override suspend fun searchUsersByName(query: String): List<User> {
        // Используем getAllUsers и фильтруем локально, пока не добавим метод в DAO
        val allUsers = userDao.getAllUsers()
        return allUsers.first()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.userToDomain() }
    }
    
    override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {
        // Update local DAO
        userDao.updateUserPoints(userId, points, level)
        
        // Fetch updated user to sync full object
        val updatedUser = userDao.getUserById(userId)?.userToDomain()
        
        if (updatedUser != null) {
            val now = System.currentTimeMillis()
            val json = SyncPayloads.userToJson(updatedUser, now)
            try {
                syncManager.enqueueUpsert(
                    entityType = "user",
                    entityId = userId,
                    payloadJson = json,
                    updatedAt = now
                )
                kickSync()
            } catch (e: Exception) {
                Timber.tag("UserRepository").e(e, "Failed to enqueue sync for updateUserLevel")
            }
        } else {
            Timber.tag("UserRepository").w("updateUserLevel: User $userId not found locally after update")
        }
    }

    override suspend fun upsertUserRemoteAndLocal(user: User, fcmToken: String?) {
        // Room-first: чтобы UI мгновенно получил пользователя
        upsertLocal(user.userToEntity())

        val now = System.currentTimeMillis()
        
        // Use SyncManager for User data
        val json = SyncPayloads.userToJson(user, now)
        syncManager.enqueueUpsert(
            entityType = "user",
            entityId = user.id,
            payloadJson = json,
            updatedAt = now
        )

        // Handle FCM Token separately (Outbox ARRAY_UNION)
        if (!fcmToken.isNullOrBlank()) {
            try {
                syncManager.enqueueArrayUnion(
                    entityType = "user",
                    entityId = user.id,
                    fieldPath = "fcmTokens",
                    values = listOf(fcmToken),
                    updatedAt = now
                )
                val tokenPayload = org.json.JSONObject()
                    .put("fcmToken", fcmToken)
                    .put("updatedAt", now)
                    .toString()
                syncManager.enqueueUpsert(
                    entityType = "user",
                    entityId = user.id,
                    payloadJson = tokenPayload,
                    updatedAt = now
                )
            } catch (e: Exception) {
                Timber.tag("UserRepository").w("upsertUserRemoteAndLocal: fcmToken update failed (non-fatal): ${e.message}")
            }
        }

        kickSync()
    }

    override suspend fun getUserFromFirestore(userId: String): User? {
        return try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            val data = snapshot.data
            if (data != null) {
                Timber.tag("UserRepository").d("getUserFromFirestore raw data: level=${data["level"]}, points=${data["points"]}, streak=${data["streak"]}")
                val user = UserFirestoreConverter.fromFirestore(data, snapshot.id)
                Timber.tag("UserRepository").d("getUserFromFirestore parsed: level=${user?.level}, points=${user?.points}, streak=${user?.streak}")
                user
            } else {
                Timber.tag("UserRepository").d("getUserFromFirestore: no data for userId=$userId")
                null
            }
        } catch (e: Exception) {
            Timber.tag("UserRepository").w("getUserFromFirestore failed: ${e.message}")
            null
        }
    }
    
    override fun observeUser(userId: String): Flow<User?> = callbackFlow {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val registration = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // При PERMISSION_DENIED (sign out) просто игнорируем, не крашим
                    Timber.tag("UserRepository").d("observeUser listener error (userId=$userId): ${error.message}")
                    return@addSnapshotListener
                }
                val data = snapshot?.data
                val remote = if (data != null) UserFirestoreConverter.fromFirestore(data, snapshot.id) else null
                if (remote != null) {
                    scope.launch {
                        try {
                            val hasPending = try {
                                syncManager.hasPendingOperations("user", remote.id)
                            } catch (e: Exception) {
                                Timber.tag("UserRepository").w("observeUser: hasPendingOperations failed (userId=${remote.id}): ${e.message}")
                                false
                            }
                            if (!hasPending) {
                                upsertLocal(remote.userToEntity())
                            }
                        } catch (e: Exception) {
                            Timber.tag("UserRepository").d("observeUser: local insert failed (non-fatal): ${e.message}")
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
                        // Convert to domain object to ensure consistent updates
                        val user = UserFirestoreConverter.fromFirestore(data, id)
                        if (user != null) {
                            val updatedUser = user.copy(
                                avatarThumbnailUrl = avatarUrl
                                // we don't change other fields, but we must update 'updatedAt' for sync logic? 
                                // Actually, User model doesn't store 'updatedAt' explicitly in domain usually? 
                                // Repo handles it. But we need to update the local entity.
                            )
                            
                            // Local update
                            userDao.insertUser(updatedUser.userToEntity())
                            
                            // Enqueue Sync (Outbox)
                            val json = SyncPayloads.userToJson(updatedUser, now)
                            syncManager.enqueueUpsert(
                                entityType = "user",
                                entityId = id,
                                payloadJson = json,
                                updatedAt = now
                            )
                            updated++
                        }
                    } catch (e: Exception) {
                        Timber.tag("UserRepository").d("backfillAvatarThumbnails: skipping doc=${id}, reason=${e.message}")
                    }
                }
            }

            if (updated > 0) kickSync()
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateLastActive(userId: String) {
        val now = System.currentTimeMillis()
        try {
            // Обновляем локально
            userDao.updateLastActive(userId, now)
            
            // Обновляем в Firestore через Outbox. Заодно досылаем ключи поиска —
            // так профили, созданные до их появления, становятся находимыми.
            val payload = org.json.JSONObject().put("lastActiveAt", now)
            userDao.getUserById(userId)?.let { local ->
                UserSearchKeys.fields(local.name, local.email).forEach { (key, value) ->
                    payload.put(key, if (value is List<*>) org.json.JSONArray(value) else value)
                }
            }
            val json = payload.toString()
            syncManager.enqueueUpsert(
                entityType = "user",
                entityId = userId,
                payloadJson = json,
                updatedAt = now
            )

            kickSync()
        } catch (e: Exception) {
            Timber.tag("UserRepository").w(e, "updateLastActive failed")
        }
    }
}
