package com.habittracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.firestore.UserFirestoreConverter
import com.habittracker.data.local.dao.UserDao
import com.habittracker.data.local.entity.UserEntity
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.data.sync.keepRestarting
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.util.SyncPayloads
import com.habittracker.domain.util.UserSearchKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
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

        // Слушатель после ошибки (сеть, выход из аккаунта) замолкает навсегда — поднимаем заново,
        // а экран тем временем показывает Room
        scope.launch {
            keepRestarting("UserRepository", "user profile") {
                remoteUser(userId).collect { remote ->
                    if (remote != null) mergeRemoteUser(remote)
                }
            }
        }

        scope.launch {
            userDao.getUserByIdFlow(userId).collect { entity ->
                trySend(entity?.userToDomain())
            }
        }

        awaitClose { scope.cancel() }
    }

    private fun remoteUser(userId: String): Flow<User?> = callbackFlow {
        val registration = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val data = snapshot?.data
                trySend(if (data != null) UserFirestoreConverter.fromFirestore(data, snapshot.id) else null)
            }
        awaitClose { registration.remove() }
    }.conflate()

    /** Профиль с неотправленными правками не перетираем: облако о них ещё не знает. */
    private suspend fun mergeRemoteUser(remote: User) {
        try {
            val hasPending = try {
                syncManager.hasPendingOperations("user", remote.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("UserRepository").w("observeUser: hasPendingOperations failed (userId=${remote.id}): ${e.message}")
                false
            }
            if (!hasPending) upsertLocal(remote.userToEntity())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("UserRepository").w(e, "observeUser: local insert failed")
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
