package com.habittracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.firestore.UserFirestoreConverter
import com.habittracker.data.local.dao.FriendRequestDao
import com.habittracker.data.local.dao.FriendshipDao
import com.habittracker.data.local.dao.HabitInvitationDao
import com.habittracker.data.local.dao.UserDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.data.remote.mappers.toDomain
import com.habittracker.data.remote.model.FriendRequestRemote
import com.habittracker.data.service.FCMApiClient
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestStatus
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipStatus
import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.model.User
import com.habittracker.domain.model.accept
import com.habittracker.domain.model.createFriendship
import com.habittracker.domain.model.isExpired
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация SocialRepository для работы с социальными функциями
 * Управляет друзьями, запросами дружбы, приглашениями в привычки и уведомлениями
 */
@Singleton
class SocialRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val friendshipDao: FriendshipDao,
    private val friendRequestDao: FriendRequestDao,
    private val habitInvitationDao: HabitInvitationDao,
    private val fcmApiClient: FCMApiClient,
    private val firestore: FirebaseFirestore,
    private val habitRepository: HabitRepository,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler
) : SocialRepository {

    // Repository-level scope for fire-and-forget background tasks (safe: singleton)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Helper to mask sensitive token values in logs
    private fun maskToken(token: String?): String {
        if (token.isNullOrBlank()) return "(null)"
        return if (token.length > 8) "${token.take(4)}****${token.takeLast(4)}" else "****"
    }

    override suspend fun searchUsers(query: String, currentUserId: String): List<User> {
        return try {
            Log.d("SocialRepository", "🔍 Searching users: query='$query', currentUserId='$currentUserId'")
            
            // Поиск в Firestore по имени и email
            val nameResults = firestore.collection("users")
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .limit(20)
                .get()
                .await()
            
            Log.d("SocialRepository", "📝 Name results: ${nameResults.documents.size} documents")

            val emailResults = firestore.collection("users")
                .whereGreaterThanOrEqualTo("email", query)
                .whereLessThanOrEqualTo("email", query + "\uf8ff")
                .limit(20)
                .get()
                .await()
                
            Log.d("SocialRepository", "📧 Email results: ${emailResults.documents.size} documents")

            val allResults = (nameResults.documents + emailResults.documents)
                .distinctBy { it.id }
                .mapNotNull { doc ->
                    try {
                        Log.d("SocialRepository", "🔄 Processing document: id=${doc.id}")
                        // Используем профессиональный конвертер вместо стандартной десериализации
                        val user = UserFirestoreConverter.fromFirestore(doc.data ?: emptyMap(), doc.id)
                        if (user != null) {
                            Log.d("SocialRepository", "✅ Successfully converted user: id=${user.id}, name=${user.name}, email=${user.email}")
                        } else {
                            Log.w("SocialRepository", "⚠️ UserFirestoreConverter returned null for document: id=${doc.id}")
                        }
                        user
                    } catch (e: Exception) {
                        Log.e("SocialRepository", "❌ Error mapping user document: ${e.message}")
                        null
                    }
                }
                .filter { it.id != currentUserId } // Исключаем текущего пользователя
        
            Log.d("SocialRepository", "🎯 Before filtering: ${allResults.size + (if (allResults.any { it.id == currentUserId }) 1 else 0)} users")
            Log.d("SocialRepository", "🎯 After filtering (excluding currentUserId=$currentUserId): ${allResults.size} users")
            allResults
        } catch (e: Exception) {
            Log.e("SocialRepository", "💥 Firestore search failed: ${e.message}")
            
            // Fallback на локальный поиск
            val localResults = userDao.searchUsers("%$query%")
                .map { it.toDomain() }
                .filter { it.id != currentUserId }
                
            Log.d("SocialRepository", "🗄️ Fallback local results: ${localResults.size} users")
            localResults
        }
    }

    override suspend fun getUserById(userId: String): User? {
        // Local-first: быстрее и устойчивее к отменам корутин
        val local = try { userDao.getUserById(userId)?.toDomain() } catch (_: Exception) { null }
        if (local != null) return local

        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                UserFirestoreConverter.fromFirestore(doc.data ?: emptyMap(), doc.id)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun updateUserFcmToken(userId: String, token: String) {
        try {
            // 1) Удаляем этот токен из всех других пользователей (перемещение токена между аккаунтами)
            try {
                // Поиск в массивах fcmTokens (array-contains)
                val tokensQuery = firestore.collection("users")
                    .whereArrayContains("fcmTokens", token)
                    .get().await()
                tokensQuery.documents.forEach { doc ->
                    if (doc.id != userId) {
                        firestore.collection("users").document(doc.id)
                            .update("fcmTokens", FieldValue.arrayRemove(token))
                            .await()
                    }
                }

                // Поиск в legacy поле fcmToken
                val legacyQuery = firestore.collection("users")
                    .whereEqualTo("fcmToken", token)
                    .get().await()
                legacyQuery.documents.forEach { doc ->
                    if (doc.id != userId) {
                        firestore.collection("users").document(doc.id)
                            .update("fcmToken", FieldValue.delete())
                            .await()
                    }
                }
            } catch (_: Exception) {
                // Не критично: в худшем случае токен временно будет подвязан к двум аккаунтам
            }

            // 2) Добавляем токен текущему пользователю (массив + legacy)
            firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "fcmTokens" to FieldValue.arrayUnion(token),
                        "fcmToken" to token
                    )
                )
                .await()
        } catch (e: Exception) {
            // Обновляем локально (сохраним единственный токен в legacy поле)
            val user = userDao.getUserById(userId)
            user?.let {
                userDao.updateUser(it.copy(fcmToken = token))
            }
        }
    }

    override suspend fun updateFriendsCount(userId: String, count: Int) {
        try {
            firestore.collection("users").document(userId)
                .update("friendsCount", count)
                .await()
        } catch (e: Exception) {
            // Обновляем локально
            val user = userDao.getUserById(userId)
            user?.let {
                userDao.updateUser(it.copy(friendsCount = count))
            }
        }
    }

    override suspend fun sendFriendRequest(
        fromUserId: String,
        toUserId: String,
        message: String?
    ): Result<FriendRequest> {
        return try {
            Log.d("SocialRepository", "📤 Sending friend request: from=$fromUserId, to=$toUserId")
            
            val currentTime = System.currentTimeMillis()
            val friendRequest = FriendRequest(
                id = UUID.randomUUID().toString(),
                fromUserId = fromUserId,
                toUserId = toUserId,
                status = FriendRequestStatus.PENDING,
                message = message,
                createdAt = currentTime,
                respondedAt = null,
                updatedAt = currentTime
            )
            // Room-first
            friendRequestDao.insertFriendRequest(friendRequest.toEntity())

            // Enqueue remote UPSERT
            val payload = JSONObject()
                .put("fromUserId", friendRequest.fromUserId)
                .put("toUserId", friendRequest.toUserId)
                .put("status", friendRequest.status.name)
                .put("message", friendRequest.message)
                .put("createdAt", friendRequest.createdAt)
                .put("respondedAt", friendRequest.respondedAt)
                .put("updatedAt", friendRequest.updatedAt)
                .toString()
            syncManager.enqueueUpsert(
                entityType = "friend_request",
                entityId = friendRequest.id,
                payloadJson = payload,
                updatedAt = friendRequest.updatedAt
            )
            // Форсируем немедленный дренаж outbox, чтобы запись быстрее попала в Firestore
            try {
                syncWorkScheduler.scheduleOneTimeSync()
            } catch (_: Exception) { /* ignore */ }

            Log.d("SocialRepository", "✅ Friend request enqueued successfully!")
            Result.success(friendRequest)
        } catch (e: Exception) {
            Log.e("SocialRepository", "❌ Failed to send friend request: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun acceptFriendRequest(requestId: String): Result<Friendship> {
        return try {
            val friendRequest = getFriendRequestById(requestId)
                ?: return Result.failure(IllegalArgumentException("Запрос не найден"))

            val acceptedRequest = friendRequest.copy(
                status = FriendRequestStatus.ACCEPTED,
                respondedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            val friendship = createFriendship(
                userId1 = friendRequest.fromUserId,
                userId2 = friendRequest.toUserId,
                requestedBy = friendRequest.fromUserId,
                requestedAt = friendRequest.createdAt
            ).accept()

            // Room-first
            friendRequestDao.updateFriendRequest(acceptedRequest.toEntity())
            
            // Важно: сначала сохраняем пользователей, чтобы избежать FOREIGN KEY constraint
            try {
                val fromUser = searchUsers("", "").find { it.id == friendRequest.fromUserId }
                val toUser = searchUsers("", "").find { it.id == friendRequest.toUserId }
                
                if (fromUser != null) {
                    userDao.insertUser(fromUser.toEntity())
                }
                
                if (toUser != null) {
                    userDao.insertUser(toUser.toEntity())
                }
            } catch (e: Exception) {
                // Продолжаем выполнение - пользователи могли уже существовать
            }
            
            friendshipDao.insertFriendship(friendship.toEntity())

            // Enqueue remote UPSERTs
            val frPayload = JSONObject()
                .put("fromUserId", acceptedRequest.fromUserId)
                .put("toUserId", acceptedRequest.toUserId)
                .put("status", acceptedRequest.status.name)
                .put("message", acceptedRequest.message)
                .put("createdAt", acceptedRequest.createdAt)
                .put("respondedAt", acceptedRequest.respondedAt)
                .put("updatedAt", acceptedRequest.updatedAt)
                .toString()
            syncManager.enqueueUpsert(
                entityType = "friend_request",
                entityId = acceptedRequest.id,
                payloadJson = frPayload,
                updatedAt = acceptedRequest.updatedAt
            )
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) { }

            val ordered = if (friendship.userId1 < friendship.userId2) Pair(friendship.userId1, friendship.userId2) else Pair(friendship.userId2, friendship.userId1)
            val fsPayload = JSONObject()
                .put("userId1", ordered.first)
                .put("userId2", ordered.second)
                .put("status", friendship.status.name)
                .put("requestedBy", friendship.requestedBy)
                .put("requestedAt", friendship.requestedAt)
                .put("acceptedAt", friendship.acceptedAt)
                .put("blockedBy", friendship.blockedBy)
                .put("blockedAt", friendship.blockedAt)
                .put("updatedAt", friendship.updatedAt)
                .toString()
            syncManager.enqueueUpsert(
                entityType = "friendship",
                entityId = "${ordered.first}_${ordered.second}",
                payloadJson = fsPayload,
                updatedAt = friendship.updatedAt
            )
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) { }

            // Обновляем счетчики друзей
            val user1FriendsCount = friendshipDao.getFriendsCount(friendship.userId1).first()
            val user2FriendsCount = friendshipDao.getFriendsCount(friendship.userId2).first()
            updateFriendsCount(friendship.userId1, user1FriendsCount + 1)
            updateFriendsCount(friendship.userId2, user2FriendsCount + 1)

            Result.success(friendship)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineFriendRequest(requestId: String): Result<FriendRequest> {
        return try {
            val friendRequest = getFriendRequestById(requestId)
                ?: return Result.failure(IllegalArgumentException("Запрос не найден"))

            val declinedRequest = friendRequest.copy(
                status = FriendRequestStatus.DECLINED,
                respondedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Room-first
            friendRequestDao.updateFriendRequest(declinedRequest.toEntity())

            // Enqueue remote UPSERT
            val payload = JSONObject()
                .put("fromUserId", declinedRequest.fromUserId)
                .put("toUserId", declinedRequest.toUserId)
                .put("status", declinedRequest.status.name)
                .put("message", declinedRequest.message)
                .put("createdAt", declinedRequest.createdAt)
                .put("respondedAt", declinedRequest.respondedAt)
                .put("updatedAt", declinedRequest.updatedAt)
                .toString()
            syncManager.enqueueUpsert(
                entityType = "friend_request",
                entityId = declinedRequest.id,
                payloadJson = payload,
                updatedAt = declinedRequest.updatedAt
            )
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) { }

            Result.success(declinedRequest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> {
        return try {
            // Room-first delete
            friendRequestDao.deleteFriendRequestById(requestId)

            // Enqueue remote DELETE
            syncManager.enqueueDelete(
                entityType = "friend_request",
                entityId = requestId,
                updatedAt = System.currentTimeMillis()
            )
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) { }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFriendRequestById(requestId: String): FriendRequest? {
        return try {
            val doc = firestore.collection("friend_requests").document(requestId).get().await()
            if (doc.exists()) {
                try {
                    val remote = doc.toObject(com.habittracker.data.remote.model.FriendRequestRemote::class.java)
                    remote?.toDomain(doc.id)
                } catch (_: Exception) {
                    // fallback к Room, если маппинг не удался
                    friendRequestDao.getFriendRequestById(requestId)?.toDomain()
                }
            } else {
                friendRequestDao.getFriendRequestById(requestId)?.toDomain()
            }
        } catch (e: Exception) {
            friendRequestDao.getFriendRequestById(requestId)?.toDomain()
        }
    }

    override suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String): FriendRequest? {
        return try {
            val results = firestore.collection("friend_requests")
                .whereEqualTo("fromUserId", fromUserId)
                .whereEqualTo("toUserId", toUserId)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .get()
                .await()

            results.documents.firstOrNull()?.let { doc ->
                try {
                    val remote = doc.toObject(com.habittracker.data.remote.model.FriendRequestRemote::class.java)
                    remote?.toDomain(doc.id)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            friendRequestDao.getFriendRequestBetweenUsers(fromUserId, toUserId)?.toDomain()
        }
    }

    override fun getIncomingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)
            // Слушаем входящие friend_requests для текущего пользователя
            val registration = firestore.collection("friend_requests")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("SocialRepository", "Incoming FR listener error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    Log.d("SocialRepository", "Incoming FR listener: toUserId=$userId, docs=${docs.size}")
                    scope.launch {
                        val remoteIds = docs.map { it.id }.toSet()
                        docs.forEach { doc ->
                            try {
                                val remote = doc.toObject(FriendRequestRemote::class.java)
                                val fr = remote?.toDomain(doc.id)
                                if (fr != null) {
                                    friendRequestDao.insertFriendRequest(fr.toEntity())
                                    // Prefetch users to avoid UI dropping item due to null users
                                    try {
                                        getUserById(fr.fromUserId)?.let { userDao.insertUser(it.toEntity()) }
                                        getUserById(fr.toUserId)?.let { userDao.insertUser(it.toEntity()) }
                                    } catch (_: Exception) { }
                                }
                            } catch (e: Exception) {
                                Log.w("SocialRepository", "Incoming FR map/insert error: ${e.message}")
                            }
                        }
                        // Reconcile: удаляем локальные PENDING входящие, которых нет в снапшоте
                        try {
                            val local = friendRequestDao.getIncomingFriendRequests(userId).first()
                            val toDelete = local.filter { it.id !in remoteIds }
                            if (toDelete.isNotEmpty()) {
                                Log.d("SocialRepository", "Incoming FR reconcile: deleting ${toDelete.size} stale records")
                                toDelete.forEach { fr -> friendRequestDao.deleteFriendRequestById(fr.id) }
                            }
                        } catch (_: Exception) { }
                    }
                }

            val job = scope.launch {
                friendRequestDao.getIncomingFriendRequests(userId).collect { requests ->
                    val list = requests.mapNotNull { requestEntity ->
                        val request = requestEntity.toDomain()
                        val fromUser = getUserById(request.fromUserId)
                        val toUser = getUserById(request.toUserId)
                        if (fromUser != null && toUser != null) FriendRequestWithUsers(request, fromUser, toUser) else null
                    }
                    trySend(list)
                }
            }

            awaitClose {
                registration?.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getOutgoingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)
            // Слушаем исходящие friend_requests от текущего пользователя
            val registration = firestore.collection("friend_requests")
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("SocialRepository", "Outgoing FR listener error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    Log.d("SocialRepository", "Outgoing FR listener: fromUserId=$userId, docs=${docs.size}")
                    scope.launch {
                        val remoteIds = docs.map { it.id }.toSet()
                        docs.forEach { doc ->
                            try {
                                val remote = doc.toObject(FriendRequestRemote::class.java)
                                val fr = remote?.toDomain(doc.id)
                                if (fr != null) {
                                    friendRequestDao.insertFriendRequest(fr.toEntity())
                                    // Prefetch users to avoid UI dropping item due to null users
                                    try {
                                        getUserById(fr.fromUserId)?.let { userDao.insertUser(it.toEntity()) }
                                        getUserById(fr.toUserId)?.let { userDao.insertUser(it.toEntity()) }
                                    } catch (_: Exception) { }
                                }
                            } catch (e: Exception) {
                                Log.w("SocialRepository", "Outgoing FR map/insert error: ${e.message}")
                            }
                        }
                        // Reconcile: удаляем локальные PENDING исходящие, которых нет в снапшоте
                        try {
                            val local = friendRequestDao.getOutgoingFriendRequests(userId).first()
                            val toDelete = local.filter { it.id !in remoteIds }
                            if (toDelete.isNotEmpty()) {
                                Log.d("SocialRepository", "Outgoing FR reconcile: deleting ${toDelete.size} stale records")
                                toDelete.forEach { fr -> friendRequestDao.deleteFriendRequestById(fr.id) }
                            }
                        } catch (_: Exception) { }
                    }
                }

            val job = scope.launch {
                friendRequestDao.getOutgoingFriendRequests(userId).collect { requests ->
                    val list = requests.mapNotNull { requestEntity ->
                        val request = requestEntity.toDomain()
                        val fromUser = getUserById(request.fromUserId)
                        val toUser = getUserById(request.toUserId)
                        if (fromUser != null && toUser != null) FriendRequestWithUsers(request, fromUser, toUser) else null
                    }
                    trySend(list)
                }
            }

            awaitClose {
                registration?.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getIncomingRequestsCount(userId: String): Flow<Int> {
        return friendRequestDao.getIncomingRequestsCount(userId)
    }

    override suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String): Friendship? {
        return try {
            val orderedIds = if (userId1 < userId2) Pair(userId1, userId2) else Pair(userId2, userId1)
            val doc = firestore.collection("friendships")
                .document("${orderedIds.first}_${orderedIds.second}")
                .get()
                .await()

            if (doc.exists()) {
                val data = doc.data ?: return null
                val u1 = data["userId1"] as? String ?: return null
                val u2 = data["userId2"] as? String ?: return null
                val status = (data["status"] as? String)?.let { runCatching { FriendshipStatus.valueOf(it) }.getOrNull() } ?: FriendshipStatus.PENDING
                val requestedBy = data["requestedBy"] as? String ?: u1
                val requestedAt = (data["requestedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val acceptedAt = (data["acceptedAt"] as? Number)?.toLong()
                val blockedBy = data["blockedBy"] as? String
                val blockedAt = (data["blockedAt"] as? Number)?.toLong()
                val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: requestedAt
                return Friendship(
                    userId1 = u1,
                    userId2 = u2,
                    status = status,
                    requestedBy = requestedBy,
                    requestedAt = requestedAt,
                    acceptedAt = acceptedAt,
                    blockedBy = blockedBy,
                    blockedAt = blockedAt,
                    updatedAt = updatedAt
                )
            } else {
                return friendshipDao.getFriendshipBetweenUsers(userId1, userId2)?.toDomain()
            }
        } catch (e: Exception) {
            friendshipDao.getFriendshipBetweenUsers(userId1, userId2)?.toDomain()
        }
    }

    override suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit> {
        return try {
            val orderedIds = if (currentUserId < friendId) Pair(currentUserId, friendId) else Pair(friendId, currentUserId)
            val docId = "${orderedIds.first}_${orderedIds.second}"

            // Room-first delete
            friendshipDao.deleteFriendshipBetweenUsers(currentUserId, friendId)

            // Enqueue remote DELETE
            syncManager.enqueueDelete(
                entityType = "friendship",
                entityId = docId,
                updatedAt = System.currentTimeMillis()
            )
            // Force outbox drain to propagate deletion to Firestore immediately
            try {
                Log.d("SocialRepository", "removeFriend: scheduled one-time sync for delete friendship doc=$docId")
                syncWorkScheduler.scheduleOneTimeSync()
            } catch (_: Exception) { /* ignore */ }

            // Обновляем счетчики друзей
            val user1FriendsCount = friendshipDao.getFriendsCount(currentUserId).first()
            val user2FriendsCount = friendshipDao.getFriendsCount(friendId).first()
            updateFriendsCount(currentUserId, user1FriendsCount - 1)
            updateFriendsCount(friendId, user2FriendsCount - 1)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockUser(currentUserId: String, userIdToBlock: String): Result<Friendship> {
        return try {
            val friendship = getFriendshipBetweenUsers(currentUserId, userIdToBlock)
                ?: return Result.failure(IllegalStateException("Дружба не найдена"))

            val blockedFriendship = friendship.copy(
                status = FriendshipStatus.BLOCKED,
                blockedBy = currentUserId,
                blockedAt = System.currentTimeMillis()
            )

            // Room-first
            friendshipDao.updateFriendship(blockedFriendship.toEntity())

            // Enqueue UPSERT
            val orderedIds = if (currentUserId < userIdToBlock) Pair(currentUserId, userIdToBlock) else Pair(userIdToBlock, currentUserId)
            val payload = JSONObject()
                .put("userId1", orderedIds.first)
                .put("userId2", orderedIds.second)
                .put("status", blockedFriendship.status.name)
                .put("requestedBy", blockedFriendship.requestedBy)
                .put("requestedAt", blockedFriendship.requestedAt)
                .put("acceptedAt", blockedFriendship.acceptedAt)
                .put("blockedBy", blockedFriendship.blockedBy)
                .put("blockedAt", blockedFriendship.blockedAt)
                .put("updatedAt", blockedFriendship.updatedAt)
                .toString()
            syncManager.enqueueUpsert(
                entityType = "friendship",
                entityId = "${orderedIds.first}_${orderedIds.second}",
                payloadJson = payload,
                updatedAt = blockedFriendship.updatedAt
            )

            Result.success(blockedFriendship)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String): Result<Friendship> {
        return try {
            val friendship = getFriendshipBetweenUsers(currentUserId, userIdToUnblock)
                ?: return Result.failure(IllegalStateException("Дружба не найдена"))

            val unblockedFriendship = friendship.copy(
                status = FriendshipStatus.ACCEPTED,
                blockedBy = null,
                blockedAt = null
            )

            // Room-first
            friendshipDao.updateFriendship(unblockedFriendship.toEntity())

            // Enqueue UPSERT
            val orderedIds = if (currentUserId < userIdToUnblock) Pair(currentUserId, userIdToUnblock) else Pair(userIdToUnblock, currentUserId)
            val payload = JSONObject()
                .put("userId1", orderedIds.first)
                .put("userId2", orderedIds.second)
                .put("status", unblockedFriendship.status.name)
                .put("requestedBy", unblockedFriendship.requestedBy)
                .put("requestedAt", unblockedFriendship.requestedAt)
                .put("acceptedAt", unblockedFriendship.acceptedAt)
                .put("blockedBy", unblockedFriendship.blockedBy)
                .put("blockedAt", unblockedFriendship.blockedAt)
                .put("updatedAt", unblockedFriendship.updatedAt)
                .toString()
            syncManager.enqueueUpsert(
                entityType = "friendship",
                entityId = "${orderedIds.first}_${orderedIds.second}",
                payloadJson = payload,
                updatedAt = unblockedFriendship.updatedAt
            )

            Result.success(unblockedFriendship)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFriends(userId: String): Flow<List<FriendshipWithUsers>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)
            // Накопление ключей из обоих слушателей для reconcile
            var lastKeys1: Set<String> = emptySet()
            var lastKeys2: Set<String> = emptySet()
            suspend fun reconcileFriends() {
                val combined = (lastKeys1 + lastKeys2).toSet()
                try {
                    val local = friendshipDao.getFriendsForUser(userId).first()
                    val toDelete = local.filter { entity ->
                        val key = "${entity.userId1}_${entity.userId2}"
                        key !in combined
                    }
                    if (toDelete.isNotEmpty()) {
                        Log.d("SocialRepository", "Friends reconcile: deleting ${toDelete.size} stale friendships for user=$userId")
                        toDelete.forEach { entity ->
                            friendshipDao.deleteFriendshipBetweenUsers(entity.userId1, entity.userId2)
                        }
                    }
                } catch (_: Exception) { }
            }
            // Два слушателя: userId как userId1 и как userId2
            val reg1 = firestore.collection("friendships")
                .whereEqualTo("userId1", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) { Log.w("SocialRepository", "Friends listener1 error: ${error.message}", error); return@addSnapshotListener }
                    val docs = snapshot?.documents ?: emptyList()
                    Log.d("SocialRepository", "Friends listener1: userId1=$userId, docs=${docs.size}")
                    scope.launch {
                        // Текущий набор ключей из этого снапшота
                        lastKeys1 = docs.mapNotNull { d ->
                            val data = d.data ?: return@mapNotNull null
                            val u1 = data["userId1"] as? String ?: return@mapNotNull null
                            val u2 = data["userId2"] as? String ?: return@mapNotNull null
                            "${u1}_${u2}"
                        }.toSet()
                        docs.forEach { doc ->
                            try {
                                val data = doc.data ?: emptyMap<String, Any?>()
                                val userId1 = data["userId1"] as? String ?: return@forEach
                                val userId2 = data["userId2"] as? String ?: return@forEach
                                val status = (data["status"] as? String)?.let { runCatching { FriendshipStatus.valueOf(it) }.getOrNull() } ?: FriendshipStatus.PENDING
                                val requestedBy = data["requestedBy"] as? String ?: userId1
                                val requestedAt = (data["requestedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                val acceptedAt = (data["acceptedAt"] as? Number)?.toLong()
                                val blockedBy = data["blockedBy"] as? String
                                val blockedAt = (data["blockedAt"] as? Number)?.toLong()
                                val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: requestedAt

                                val fs = Friendship(
                                    userId1 = userId1,
                                    userId2 = userId2,
                                    status = status,
                                    requestedBy = requestedBy,
                                    requestedAt = requestedAt,
                                    acceptedAt = acceptedAt,
                                    blockedBy = blockedBy,
                                    blockedAt = blockedAt,
                                    updatedAt = updatedAt
                                )
                                friendshipDao.insertFriendship(fs.toEntity())
                            } catch (_: Exception) { /* ignore single doc errors */ }
                        }
                        // Reconcile после обработки снапшота
                        reconcileFriends()
                    }
                }

            val reg2 = firestore.collection("friendships")
                .whereEqualTo("userId2", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) { Log.w("SocialRepository", "Friends listener2 error: ${error.message}", error); return@addSnapshotListener }
                    val docs = snapshot?.documents ?: emptyList()
                    Log.d("SocialRepository", "Friends listener2: userId2=$userId, docs=${docs.size}")
                    scope.launch {
                        // Текущий набор ключей из этого снапшота
                        lastKeys2 = docs.mapNotNull { d ->
                            val data = d.data ?: return@mapNotNull null
                            val u1 = data["userId1"] as? String ?: return@mapNotNull null
                            val u2 = data["userId2"] as? String ?: return@mapNotNull null
                            "${u1}_${u2}"
                        }.toSet()
                        docs.forEach { doc ->
                            try {
                                val data = doc.data ?: emptyMap<String, Any?>()
                                val userId1 = data["userId1"] as? String ?: return@forEach
                                val userId2 = data["userId2"] as? String ?: return@forEach
                                val status = (data["status"] as? String)?.let { runCatching { FriendshipStatus.valueOf(it) }.getOrNull() } ?: FriendshipStatus.PENDING
                                val requestedBy = data["requestedBy"] as? String ?: userId1
                                val requestedAt = (data["requestedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                val acceptedAt = (data["acceptedAt"] as? Number)?.toLong()
                                val blockedBy = data["blockedBy"] as? String
                                val blockedAt = (data["blockedAt"] as? Number)?.toLong()
                                val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: requestedAt

                                val fs = Friendship(
                                    userId1 = userId1,
                                    userId2 = userId2,
                                    status = status,
                                    requestedBy = requestedBy,
                                    requestedAt = requestedAt,
                                    acceptedAt = acceptedAt,
                                    blockedBy = blockedBy,
                                    blockedAt = blockedAt,
                                    updatedAt = updatedAt
                                )
                                friendshipDao.insertFriendship(fs.toEntity())
                            } catch (_: Exception) { /* ignore single doc errors */ }
                        }
                        // Reconcile после обработки снапшота
                        reconcileFriends()
                    }
                }

            val job = scope.launch {
                friendshipDao.getFriendsForUser(userId).collect { friendshipEntities ->
                    val list = friendshipEntities.mapNotNull { friendshipEntity ->
                        val friendship = friendshipEntity.toDomain()
                        try {
                            val user1 = runBlocking { getUserById(friendship.userId1) }
                            val user2 = runBlocking { getUserById(friendship.userId2) }
                            if (user1 != null && user2 != null) FriendshipWithUsers(friendship, user1, user2) else null
                        } catch (_: Exception) { null }
                    }.filter { it.friendship.status == FriendshipStatus.ACCEPTED }
                    trySend(list)
                }
            }

            awaitClose {
                reg1?.remove()
                reg2?.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getFriendsCount(userId: String): Flow<Int> {
        return friendshipDao.getFriendsCount(userId)
    }

    override suspend fun areFriends(userId1: String, userId2: String): Boolean {
        val friendship = getFriendshipBetweenUsers(userId1, userId2)
        return friendship?.status == FriendshipStatus.ACCEPTED
    }

    override suspend fun canSendFriendRequest(fromUserId: String, toUserId: String): Boolean {
        // Проверяем, что пользователи не друзья
        if (areFriends(fromUserId, toUserId)) {
            return false
        }

        // Проверяем, что нет активного запроса в любом направлении
        val existingRequest1 = getFriendRequestBetweenUsers(fromUserId, toUserId)
        val existingRequest2 = getFriendRequestBetweenUsers(toUserId, fromUserId)

        return existingRequest1 == null && existingRequest2 == null
    }

    override suspend fun expireOldFriendRequests() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
        try {
            friendRequestDao.expireOldRequests(thirtyDaysAgo)
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
    }

    override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) {
        try {
            // Получаем все актуальные токены получателя: массив fcmTokens + fallback fcmToken
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Log.w("SocialRepository", "FCM skip: friend_request -> to=$toUserId, tokens_absent")
                return
            }

            Log.d(
                "SocialRepository",
                "FCM send: friend_request -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}"
            )

            // Отправка на все токены и очистка невалидных (UNREGISTERED)
            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = "Новый запрос дружбы",
                body = "$fromUserName хочет добавить вас в друзья",
                data = mapOf(
                    "type" to "friend_request",
                    "fromUserName" to fromUserName,
                    "action" to "open_friend_requests",
                    // таргет для фильтрации на клиенте
                    "recipientUserId" to toUserId
                )
            )
            batchResult.onSuccess { result ->
                result.results.filter { !it.success && (it.error?.contains("UNREGISTERED", ignoreCase = true) == true) }
                    .forEach { bad ->
                        Log.w("SocialRepository", "FCM UNREGISTERED (friend_request) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Log.w("SocialRepository", "Ошибка отправки friend_request batch: ${e.message}")
            }
        } catch (e: Exception) {
            // Логируем ошибку, но не падаем - уведомления не критичны
            Log.w("SocialRepository", "Ошибка отправки уведомления о запросе дружбы", e)
        }
    }

    override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) {
        try {
            // Получаем все актуальные токены получателя: массив fcmTokens + fallback fcmToken
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Log.w("SocialRepository", "FCM skip: friend_accepted -> to=$toUserId, tokens_absent")
                return
            }

            Log.d(
                "SocialRepository",
                "FCM send: friend_accepted -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, acceptedBy=$acceptedByName"
            )

            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = "Запрос дружбы принят",
                body = "$acceptedByName принял ваш запрос дружбы",
                data = mapOf(
                    "type" to "friend_accepted",
                    "acceptedByName" to acceptedByName,
                    "action" to "open_friends",
                    // таргет для фильтрации на клиенте
                    "recipientUserId" to toUserId
                )
            )
            batchResult.onSuccess { result ->
                result.results.filter { !it.success && (it.error?.contains("UNREGISTERED", ignoreCase = true) == true) }
                    .forEach { bad ->
                        Log.w("SocialRepository", "FCM UNREGISTERED (friend_accepted) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Log.w("SocialRepository", "Ошибка отправки friend_accepted batch: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w("SocialRepository", "Ошибка отправки уведомления о принятии дружбы", e)
        }
    }

    // Habit invitations implementation
    override suspend fun sendHabitInvitation(
        habitId: String,
        habitTitle: String,
        fromUserId: String,
        fromUserName: String,
        toUserId: String,
        message: String?
    ): Result<HabitInvitation> {
        return try {
            // Проверяем, можно ли отправить приглашение
            if (!canSendHabitInvitation(fromUserId, toUserId, habitId)) {
                return Result.failure(Exception("Нельзя отправить приглашение этому пользователю"))
            }

            val invitation = HabitInvitation(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                habitTitle = habitTitle,
                fromUserId = fromUserId,
                fromUserName = fromUserName,
                toUserId = toUserId,
                message = message,
                status = HabitInvitationStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                respondedAt = null,
                expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000) // Истекает через 7 дней
            )

            // Room-first
            Log.d("SocialRepository", "[sendHabitInvitation] insert Room: invitationId=${invitation.id}, habitId=${invitation.habitId}, toUserId=${invitation.toUserId}")
            habitInvitationDao.insert(invitation.toEntity())
            Log.d("SocialRepository", "[sendHabitInvitation] insert Room: DONE invitationId=${invitation.id}")

            // Enqueue UPSERT to Firestore via Outbox
            val payload = JSONObject()
                .put("habitId", invitation.habitId)
                .put("habitTitle", invitation.habitTitle)
                .put("fromUserId", invitation.fromUserId)
                .put("fromUserName", invitation.fromUserName)
                .put("toUserId", invitation.toUserId)
                .put("message", invitation.message)
                .put("status", invitation.status.name)
                .put("createdAt", invitation.createdAt)
                .put("respondedAt", invitation.respondedAt)
                .put("expiresAt", invitation.expiresAt)
                .put("updatedAt", invitation.createdAt)
                .toString()
            Log.d(
                "SocialRepository",
                "[sendHabitInvitation] enqueueUpsert Outbox: type=habit_invitation, id=${invitation.id}, payloadSize=${payload.length}"
            )
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitation.id,
                payloadJson = payload,
                updatedAt = invitation.createdAt
            )
            // Форсируем немедленный дренаж outbox, чтобы приглашение быстрее появилось в Firestore
            try {
                Log.d("SocialRepository", "[sendHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Log.d("SocialRepository", "[sendHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (e: Exception) {
                Log.w("SocialRepository", "[sendHabitInvitation] scheduleOneTimeSync() failed: ${e.message}", e)
            }
            // Дополнительно: мгновенный дренаж в бекграунде (устойчив к задержкам WorkManager)
            repoScope.launch {
                try {
                    Log.d("SocialRepository", "[sendHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Log.d("SocialRepository", "[sendHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Log.w("SocialRepository", "[sendHabitInvitation] inline drainPending() failed: ${e.message}", e)
                }
            }

            // Отправляем FCM уведомление
            Log.d(
                "SocialRepository",
                "[sendHabitInvitation] sendHabitInvitationNotification -> to=$toUserId, from=$fromUserName, habitTitle=$habitTitle"
            )
            sendHabitInvitationNotification(toUserId, fromUserName, habitTitle)
            Log.d("SocialRepository", "[sendHabitInvitation] sendHabitInvitationNotification -> DONE")

            Result.success(invitation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptHabitInvitation(invitationId: String): Result<HabitInvitation> {
        return try {
            val invitation = getHabitInvitationById(invitationId)
                ?: return Result.failure(Exception("Приглашение не найдено"))

            if (invitation.status != HabitInvitationStatus.PENDING) {
                return Result.failure(Exception("Приглашение уже обработано"))
            }

            if (invitation.isExpired()) {
                return Result.failure(Exception("Приглашение истекло"))
            }

            val acceptedInvitation = invitation.copy(
                status = HabitInvitationStatus.ACCEPTED,
                respondedAt = System.currentTimeMillis()
            )

            // Room-first update
            Log.d("SocialRepository", "[acceptHabitInvitation] update Room -> ACCEPTED: invitationId=$invitationId")
            habitInvitationDao.update(acceptedInvitation.toEntity())
            Log.d("SocialRepository", "[acceptHabitInvitation] update Room DONE: invitationId=$invitationId")

            // Enqueue UPSERT for invitation status
            val payload = JSONObject()
                .put("habitId", acceptedInvitation.habitId)
                .put("habitTitle", acceptedInvitation.habitTitle)
                .put("fromUserId", acceptedInvitation.fromUserId)
                .put("fromUserName", acceptedInvitation.fromUserName)
                .put("toUserId", acceptedInvitation.toUserId)
                .put("message", acceptedInvitation.message)
                .put("status", acceptedInvitation.status.name)
                .put("createdAt", acceptedInvitation.createdAt)
                .put("respondedAt", acceptedInvitation.respondedAt)
                .put("expiresAt", acceptedInvitation.expiresAt)
                .put("updatedAt", System.currentTimeMillis())
                .toString()
            Log.d("SocialRepository", "[acceptHabitInvitation] enqueueUpsert Outbox: type=habit_invitation, id=$invitationId")
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitationId,
                payloadJson = payload,
                updatedAt = System.currentTimeMillis()
            )
            // Форсируем немедленный дренаж outbox для обновления статуса приглашения
            try {
                Log.d("SocialRepository", "[acceptHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Log.d("SocialRepository", "[acceptHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (e: Exception) {
                Log.w("SocialRepository", "[acceptHabitInvitation] scheduleOneTimeSync() failed: ${e.message}", e)
            }
            // Дополнительно: мгновенный дренаж в бекграунде
            repoScope.launch {
                try {
                    Log.d("SocialRepository", "[acceptHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Log.d("SocialRepository", "[acceptHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Log.w("SocialRepository", "[acceptHabitInvitation] inline drainPending() failed: ${e.message}", e)
                }
            }

            // Добавляем пользователя к участникам привычки локально
            habitRepository.addParticipantToHabit(invitation.habitId, invitation.toUserId)
            try {
                firestore.collection("habits")
                    .document(invitation.habitId)
                    .update(
                        mapOf(
                            "participants" to FieldValue.arrayUnion(invitation.toUserId),
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
                    .await()
            } catch (_: Exception) {
                // Не валим процесс принятия, но логично оставить локальный апдейт; Firestore подтянется при следующей синхронизации
            }
            // Отправляем FCM уведомление создателю привычки
            val acceptedUserName = try {
                getUserById(invitation.toUserId)?.name ?: invitation.toUserId
            }
            catch (_: Exception) {
                invitation.toUserId
            }
            Log.d(
                "SocialRepository",
                "[acceptHabitInvitation] sendHabitInvitationAcceptedNotification -> to=${invitation.fromUserId}, acceptedBy=$acceptedUserName, habit=${invitation.habitTitle}"
            )
            sendHabitInvitationAcceptedNotification(
                toUserId = invitation.fromUserId,
                acceptedByName = acceptedUserName,
                habitTitle = invitation.habitTitle
            )
            Log.d("SocialRepository", "[acceptHabitInvitation] sendHabitInvitationAcceptedNotification -> DONE")

            Result.success(acceptedInvitation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineHabitInvitation(invitationId: String): Result<HabitInvitation> {
        return try {
            val invitation = getHabitInvitationById(invitationId)
                ?: return Result.failure(Exception("Приглашение не найдено"))

            if (invitation.status != HabitInvitationStatus.PENDING) {
                return Result.failure(Exception("Приглашение уже обработано"))
            }

            val declinedInvitation = invitation.copy(
                status = HabitInvitationStatus.DECLINED,
                respondedAt = System.currentTimeMillis()
            )

            // Room-first update
            Log.d("SocialRepository", "[declineHabitInvitation] update Room -> DECLINED: invitationId=$invitationId")
            habitInvitationDao.update(declinedInvitation.toEntity())
            Log.d("SocialRepository", "[declineHabitInvitation] update Room DONE: invitationId=$invitationId")

            // Enqueue UPSERT for invitation status
            val payload = JSONObject()
                .put("habitId", declinedInvitation.habitId)
                .put("habitTitle", declinedInvitation.habitTitle)
                .put("fromUserId", declinedInvitation.fromUserId)
                .put("fromUserName", declinedInvitation.fromUserName)
                .put("toUserId", declinedInvitation.toUserId)
                .put("message", declinedInvitation.message)
                .put("status", declinedInvitation.status.name)
                .put("createdAt", declinedInvitation.createdAt)
                .put("respondedAt", declinedInvitation.respondedAt)
                .put("expiresAt", declinedInvitation.expiresAt)
                .put("updatedAt", System.currentTimeMillis())
                .toString()
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitationId,
                payloadJson = payload,
                updatedAt = System.currentTimeMillis()
            )
            // Форсируем немедленный дренаж outbox для обновления статуса приглашения
            try {
                Log.d("SocialRepository", "[declineHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Log.d("SocialRepository", "[declineHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (_: Exception) { }
            // Дополнительно: мгновенный дренаж в бекграунде
            repoScope.launch {
                try {
                    Log.d("SocialRepository", "[declineHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Log.d("SocialRepository", "[declineHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Log.w("SocialRepository", "[declineHabitInvitation] inline drainPending() failed: ${e.message}", e)
                }
            }

            Result.success(declinedInvitation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelHabitInvitation(invitationId: String): Result<Unit> {
        return try {
            val invitation = getHabitInvitationById(invitationId)
                ?: return Result.failure(Exception("Приглашение не найдено"))

            if (invitation.status != HabitInvitationStatus.PENDING) {
                return Result.failure(Exception("Можно отменить только ожидающие приглашения"))
            }

            val cancelledInvitation = invitation.copy(
                status = HabitInvitationStatus.CANCELLED,
                respondedAt = System.currentTimeMillis()
            )

            // Room-first update
            Log.d("SocialRepository", "[cancelHabitInvitation] update Room -> CANCELLED: invitationId=$invitationId")
            habitInvitationDao.update(cancelledInvitation.toEntity())
            Log.d("SocialRepository", "[cancelHabitInvitation] update Room DONE: invitationId=$invitationId")

            // Enqueue UPSERT for invitation status
            val payload = JSONObject()
                .put("habitId", cancelledInvitation.habitId)
                .put("habitTitle", cancelledInvitation.habitTitle)
                .put("fromUserId", cancelledInvitation.fromUserId)
                .put("fromUserName", cancelledInvitation.fromUserName)
                .put("toUserId", cancelledInvitation.toUserId)
                .put("message", cancelledInvitation.message)
                .put("status", cancelledInvitation.status.name)
                .put("createdAt", cancelledInvitation.createdAt)
                .put("respondedAt", cancelledInvitation.respondedAt)
                .put("expiresAt", cancelledInvitation.expiresAt)
                .put("updatedAt", System.currentTimeMillis())
                .toString()
            Log.d("SocialRepository", "[cancelHabitInvitation] enqueueUpsert Outbox: type=habit_invitation, id=$invitationId")
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitationId,
                payloadJson = payload,
                updatedAt = System.currentTimeMillis()
            )
            // Форсируем немедленный дренаж outbox для обновления статуса приглашения
            try {
                Log.d("SocialRepository", "[cancelHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Log.d("SocialRepository", "[cancelHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (e: Exception) {
                Log.w("SocialRepository", "[cancelHabitInvitation] scheduleOneTimeSync() failed: ${e.message}", e)
            }
            // Дополнительно: мгновенный дренаж в бекграунде
            repoScope.launch {
                try {
                    Log.d("SocialRepository", "[cancelHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Log.d("SocialRepository", "[cancelHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Log.w("SocialRepository", "[cancelHabitInvitation] inline drainPending() failed: ${e.message}", e)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getHabitInvitationById(invitationId: String): HabitInvitation? {
        return try {
            val doc = firestore.collection("habit_invitations").document(invitationId).get().await()
            if (doc.exists()) {
                doc.toObject(com.habittracker.data.remote.model.HabitInvitationRemote::class.java)
                    ?.toDomain(doc.id)
            } else {
                habitInvitationDao.getById(invitationId)?.toDomain()
            }
        } catch (e: Exception) {
            habitInvitationDao.getById(invitationId)?.toDomain()
        }
    }

    override suspend fun getPendingHabitInvitation(habitId: String, userId: String): HabitInvitation? {
        return try {
            val results = firestore.collection("habit_invitations")
                .whereEqualTo("habitId", habitId)
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", HabitInvitationStatus.PENDING.name)
                .get()
                .await()

            results.documents.firstOrNull()?.let { doc ->
                doc.toObject(com.habittracker.data.remote.model.HabitInvitationRemote::class.java)
                    ?.toDomain(doc.id)
            }
        } catch (e: Exception) {
            habitInvitationDao.getPendingInvitation(habitId, userId)?.toDomain()
        }
    }

    override fun getIncomingHabitInvitations(userId: String): Flow<List<HabitInvitation>> {
        // Реальное время: слушаем Firestore и мержим в локальный Room, отдаём Flow из Room
        return callbackFlow {
            // Отдельный scope для DAO-коллекции внутри callbackFlow
            val scope = CoroutineScope(Dispatchers.IO)

            // 1) Стартуем слушатель Firestore
            val registration = firestore.collection("habit_invitations")
                .whereEqualTo("toUserId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Игнорируем ошибку, оставляем локальные данные, но логируем
                        try {
                            android.util.Log.w(
                                "SocialRepository",
                                "[getIncomingHabitInvitations] Firestore listener error: ${error.message}",
                                error
                            )
                        } catch (_: Exception) {}
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    try {
                        android.util.Log.d(
                            "SocialRepository",
                            "[getIncomingHabitInvitations] snapshot size=${docs.size}, docIds=${docs.map { it.id }}"
                        )
                    } catch (_: Exception) {}

                    val list = docs.mapNotNull { doc ->
                        doc.toObject(com.habittracker.data.remote.model.HabitInvitationRemote::class.java)
                            ?.toDomain(doc.id)
                    } ?: emptyList()

                    // Апсертим локально для консистентности (и для счётчиков)
                    scope.launch {
                        try {
                            android.util.Log.d(
                                "SocialRepository",
                                "[getIncomingHabitInvitations] insertAll -> size=${list.size}"
                            )
                            habitInvitationDao.insertAll(list.map { it.toEntity() })
                            android.util.Log.d(
                                "SocialRepository",
                                "[getIncomingHabitInvitations] insertAll DONE"
                            )
                        } catch (e: Exception) {
                            try {
                                android.util.Log.w(
                                    "SocialRepository",
                                    "[getIncomingHabitInvitations] insertAll failed: ${e.message}",
                                    e
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }

            // 2) Ретранслируем локальный Flow из Room
            val job = scope.launch {
                habitInvitationDao.getIncomingInvitations(userId).collect { entities ->
                    trySend(entities.map { it.toDomain() })
                }
            }

            awaitClose {
                registration?.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getOutgoingHabitInvitations(userId: String): Flow<List<HabitInvitation>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)

            // Firestore listener: исходящие приглашения от пользователя
            val registration = firestore.collection("habit_invitations")
                .whereEqualTo("fromUserId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(com.habittracker.data.remote.model.HabitInvitationRemote::class.java)
                            ?.toDomain(doc.id)
                    } ?: emptyList()

                    scope.launch {
                        try {
                            habitInvitationDao.insertAll(list.map { it.toEntity() })
                        } catch (_: Exception) {}
                    }
                }

            val job = scope.launch {
                habitInvitationDao.getOutgoingInvitations(userId).collect { entities ->
                    trySend(entities.map { it.toDomain() })
                }
            }

            awaitClose {
                registration?.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getHabitInvitations(habitId: String): Flow<List<HabitInvitation>> {
        return callbackFlow {
            val scope = CoroutineScope(Dispatchers.IO)

            // Firestore listener: все приглашения для конкретной привычки
            val registration = firestore.collection("habit_invitations")
                .whereEqualTo("habitId", habitId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(com.habittracker.data.remote.model.HabitInvitationRemote::class.java)
                            ?.toDomain(doc.id)
                    } ?: emptyList()

                    scope.launch {
                        try {
                            habitInvitationDao.insertAll(list.map { it.toEntity() })
                        } catch (_: Exception) {}
                    }
                }

            val job = scope.launch {
                habitInvitationDao.getHabitInvitations(habitId).collect { entities ->
                    trySend(entities.map { it.toDomain() })
                }
            }

            awaitClose {
                registration?.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getPendingHabitInvitationsCount(userId: String): Flow<Int> {
        return habitInvitationDao.getPendingInvitationsCount(userId)
    }

    override suspend fun getActiveOutgoingInvitationsCount(userId: String): Int {
        return try {
            habitInvitationDao.getActiveOutgoingInvitationsCount(userId)
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun expireOldHabitInvitations() {
        val currentTime = System.currentTimeMillis()
        try {
            habitInvitationDao.expireOldInvitations(currentTime)
            habitInvitationDao.deleteOldInvitations(currentTime - (30 * 24 * 60 * 60 * 1000)) // Удаляем старые через 30 дней
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
    }

    override suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String): Boolean {
        // Проверяем, что пользователи друзья
        if (!areFriends(fromUserId, toUserId)) {
            return false
        }

        // Проверяем, что нет активного приглашения
        val existingInvitation = getPendingHabitInvitation(habitId, toUserId)
        return existingInvitation == null
    }

    override suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String) {
        try {
            // Отправляем асинхронно и неотменяемо, включая загрузку токенов
            repoScope.launch {
                withContext(NonCancellable + Dispatchers.IO) {
                    val userDoc = firestore.collection("users").document(toUserId).get().await()
                    val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val legacyToken = userDoc.getString("fcmToken")
                    val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet()

                    if (allTokens.isEmpty()) {
                        Log.w("SocialRepository", "FCM skip: habit_invitation -> to=$toUserId, tokens_absent")
                        return@withContext
                    }

                    Log.d(
                        "SocialRepository",
                        "FCM send: habit_invitation -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}"
                    )

                    allTokens.forEach { token ->
                        val result = fcmApiClient.sendPushNotification(
                            fcmToken = token,
                            title = "Приглашение к привычке",
                            body = "$fromUserName приглашает вас к участию в привычке '$habitTitle'",
                            data = mapOf(
                                "type" to "habit_invitation",
                                "habitTitle" to habitTitle,
                                "fromUserName" to fromUserName,
                                "action" to "open_habit_invitations",
                                "recipientUserId" to toUserId
                            )
                        )
                        result.onFailure { e ->
                            val msg = e.message ?: ""
                            if (msg.contains("UNREGISTERED", ignoreCase = true)) {
                                Log.w("SocialRepository", "FCM UNREGISTERED -> remove token ${maskToken(token)} for user=$toUserId")
                                safelyRemoveInvalidFcmToken(toUserId, token)
                            } else {
                                Log.w("SocialRepository", "FCM send failed for token ${maskToken(token)}: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SocialRepository", "Ошибка подготовки уведомления о приглашении в привычку (outer)", e)
        }
    }

    override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String) {
        try {
            repoScope.launch {
                withContext(NonCancellable + Dispatchers.IO) {
                    val userDoc = firestore.collection("users").document(toUserId).get().await()
                    val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val legacyToken = userDoc.getString("fcmToken")
                    val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet()

                    if (allTokens.isEmpty()) {
                        Log.w("SocialRepository", "FCM skip: habit_invitation_accepted -> to=$toUserId, tokens_absent")
                        return@withContext
                    }

                    Log.d(
                        "SocialRepository",
                        "FCM send: habit_invitation_accepted -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, recipientUserId=$toUserId, acceptedBy=$acceptedByName, habit='$habitTitle'"
                    )

                    allTokens.forEach { token ->
                        val result = fcmApiClient.sendPushNotification(
                            fcmToken = token,
                            title = "Приглашение принято",
                            body = "$acceptedByName принял ваше приглашение к участию в привычке '$habitTitle'",
                            data = mapOf(
                                "type" to "habit_invitation_accepted",
                                "habitTitle" to habitTitle,
                                "acceptedByName" to acceptedByName,
                                "action" to "open_habit_details",
                                "recipientUserId" to toUserId
                            )
                        )
                        result.onFailure { e ->
                            val msg = e.message ?: ""
                            if (msg.contains("UNREGISTERED", ignoreCase = true)) {
                                Log.w("SocialRepository", "FCM UNREGISTERED -> remove token ${maskToken(token)} for user=$toUserId")
                                safelyRemoveInvalidFcmToken(toUserId, token)
                            } else {
                                Log.w("SocialRepository", "FCM send failed for token ${maskToken(token)}: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SocialRepository", "Ошибка подготовки уведомления о принятии приглашения (outer)", e)
        }
    }

    // Удаляем невалидный FCM-токен из массива fcmTokens и, если совпадает, из legacy fcmToken
    private suspend fun safelyRemoveInvalidFcmToken(userId: String, badToken: String) {
        try {
            val userRef = firestore.collection("users").document(userId)
            firestore.runTransaction { tx ->
                val snap = tx.get(userRef)
                val tokens = (snap.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val beforeSize = tokens.size
                val newTokens = tokens.filter { it != badToken }

                val legacy = snap.getString("fcmToken")
                val legacyMatched = legacy == badToken

                Log.d(
                    "SocialRepository",
                    "FCM cleanup: start user=$userId, token=${maskToken(badToken)}, before_tokens=$beforeSize, legacy_match=$legacyMatched"
                )

                val updates = mutableMapOf<String, Any>("fcmTokens" to newTokens)
                if (legacyMatched) {
                    updates["fcmToken"] = FieldValue.delete()
                }
                tx.update(userRef, updates as Map<String, Any>)
            }.await()
            Log.d(
                "SocialRepository",
                "FCM cleanup: done user=$userId, removed token=${maskToken(badToken)}"
            )
        } catch (e: Exception) {
            Log.w(
                "SocialRepository",
                "FCM cleanup: failed user=$userId, token=${maskToken(badToken)} -> ${e.message}"
            )
        }
    }

    override suspend fun sendChallengeInvitationNotification(toUserId: String, fromUserName: String, challengeTitle: String, habitTitle: String) {
        val toUser = getUserById(toUserId)
        if (toUser != null) {
            val fcmToken = toUser.fcmToken
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken,
                    "Приглашение в челлендж",
                    "$fromUserName приглашает вас к участию в челлендже '$challengeTitle' по привычке '$habitTitle'",
                    mapOf(
                        "type" to "challenge_invitation",
                        "challengeTitle" to challengeTitle,
                        "habitTitle" to habitTitle
                    )
                )
            }
        }
    }
    
    override suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeTitle: String) {
        val toUser = getUserById(toUserId)
        if (toUser != null) {
            val fcmToken = toUser.fcmToken
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken,
                    "Новый участник челленджа",
                    "$joinedUserName присоединился к челленджу '$challengeTitle'",
                    mapOf(
                        "type" to "challenge_joined",
                        "challengeTitle" to challengeTitle
                    )
                )
            }
        }
    }
    
    override suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeTitle: String) {
        val toUser = getUserById(toUserId)
        if (toUser != null) {
            val fcmToken = toUser.fcmToken
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken,
                    "Участник покинул челлендж",
                    "$leftUserName покинул челлендж '$challengeTitle'",
                    mapOf(
                        "type" to "challenge_left",
                        "challengeTitle" to challengeTitle
                    )
                )
            }
        }
    }
    
    override suspend fun sendChallengeCompletedNotification(toUserId: String, challengeTitle: String, message: String, isWinner: Boolean) {
        val toUser = getUserById(toUserId)
        if (toUser != null) {
            val fcmToken = toUser.fcmToken
            if (fcmToken != null) {
                val title = if (isWinner) "Поздравляем с победой!" else "Челлендж завершен"
                fcmApiClient.sendPushNotification(
                    fcmToken,
                    title,
                    message,
                    mapOf(
                        "type" to "challenge_completed",
                        "challengeTitle" to challengeTitle,
                        "isWinner" to isWinner.toString()
                    )
                )
            }
        }
    }
    
    override suspend fun sendChallengeCancelledNotification(toUserId: String, challengeTitle: String, reason: String) {
        val toUser = getUserById(toUserId)
        if (toUser != null) {
            val fcmToken = toUser.fcmToken
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken,
                    "Челлендж отменен",
                    "Челлендж '$challengeTitle' был отменен. $reason",
                    mapOf(
                        "type" to "challenge_cancelled",
                        "challengeTitle" to challengeTitle,
                        "reason" to reason
                    )
                )
            }
        }
    }

    override suspend fun sendPushNotification(
        fcmToken: String, 
        title: String, 
        body: String, 
        data: Map<String, String>
    ) {
        try {
            // Используем FCMApiClient для реальной отправки
            val result = fcmApiClient.sendPushNotification(fcmToken, title, body, data)
            
            result.onSuccess {
                // Уведомление успешно отправлено
            }.onFailure { error ->
                // Логируем ошибку, но не прерываем выполнение
                // В production здесь можно добавить retry логику или отправку в аналитику
                println("Ошибка отправки push-уведомления: ${error.message}")
            }
            
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем выполнение
            println("Исключение при отправке push-уведомления: ${e.message}")
        }
    }
}
