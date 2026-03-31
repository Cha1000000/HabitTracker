package com.habittracker.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.habittracker.R
import com.habittracker.data.firestore.UserFirestoreConverter
import com.habittracker.data.local.dao.FriendRequestDao
import com.habittracker.data.local.dao.FriendshipDao
import com.habittracker.data.local.dao.HabitInvitationDao
import com.habittracker.data.local.dao.SyncOutboxDao
import com.habittracker.data.local.dao.UserDao
import com.habittracker.data.local.entity.UserEntity
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.data.remote.mappers.toDomain
import com.habittracker.data.remote.model.FriendRequestRemote
import com.habittracker.data.service.FCMApiClient
import com.habittracker.data.service.UnregisteredTokenException
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
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.util.SyncPayloads
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
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
    private val syncWorkScheduler: SyncWorkScheduler,
    private val notificationRepository: GroupHabitNotificationRepository,
    private val syncOutboxDao: SyncOutboxDao,
    private val stringProvider: StringProvider
) : SocialRepository {

    // Repository-level scope for fire-and-forget background tasks (safe: singleton)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun upsertLocalUser(entity: UserEntity) {
        val existing = try {
            userDao.getUserById(entity.id)
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w("upsertLocalUser: getUserById failed for id=${entity.id}: ${e.message}")
            null
        }
        if (existing == null) {
            userDao.insertUser(entity)
        } else {
            userDao.updateUser(entity)
        }
    }

    // Helper to mask sensitive token values in logs
    private fun maskToken(token: String?): String {
        if (token.isNullOrBlank()) return "(null)"
        return if (token.length > 8) "${token.take(4)}****${token.takeLast(4)}" else "****"
    }

    // Helper to parse timestamp from Firestore (supports both Number and ISO 8601 String)
    private fun parseTimestamp(value: Any?, fallback: Long = System.currentTimeMillis()): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> try {
                // Try parsing as ISO 8601 LocalDateTime (e.g., "2025-12-16T11:05:03")
                kotlinx.datetime.LocalDateTime.parse(value)
                    .toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()
            } catch (_: Exception) {
                try {
                    // Try parsing as ISO 8601 LocalDate (e.g., "2025-12-16")
                    kotlinx.datetime.LocalDate.parse(value)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                } catch (_: Exception) {
                    fallback
                }
            }
            else -> fallback
        }
    }

    override suspend fun searchUsers(query: String, currentUserId: String): List<User> {
        val queryLower = query.lowercase()
        
        // Получаем список ID друзей для исключения из результатов
        val friendIds = try {
            friendshipDao.getFriendIdsForUser(currentUserId)
        } catch (_: Exception) {
            emptyList()
        }
        
        return try {
            Timber.tag("SocialRepository").d("🔍 Searching users: query='$query', currentUserId='$currentUserId'")
            
            // Загружаем всех пользователей и фильтруем локально
            // (Firestore не поддерживает поиск подстроки в середине строки)
            val allUsersSnapshot = firestore.collection("users")
                .limit(200)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            val allResults = allUsersSnapshot.documents
                .mapNotNull { doc ->
                    try {
                        UserFirestoreConverter.fromFirestore(doc.data ?: emptyMap(), doc.id)
                    } catch (_: Exception) {
                        null
                    }
                }
                .filter { user ->
                    user.id != currentUserId &&
                    user.id !in friendIds &&
                    (user.name.lowercase().contains(queryLower) || user.email.lowercase().contains(queryLower))
                }
                .take(20)
        
            Timber.tag("SocialRepository").d("🎯 After filtering: ${allResults.size} users")
            allResults
        } catch (e: FirebaseFirestoreException) {
            // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Timber.tag("SocialRepository").d("searchUsers: device is offline, using local fallback")
            } else {
                Timber.tag("SocialRepository").w("Firestore search failed: ${e.code}")
            }
            
            // Fallback на локальный поиск
            val localResults = userDao.searchUsers("%$queryLower%")
                .map { it.toDomain() }
                .filter { user ->
                    user.id != currentUserId &&
                    user.id !in friendIds &&
                    (user.name.lowercase().contains(queryLower) || user.email.lowercase().contains(queryLower))
                }
                
            Timber.tag("SocialRepository").d("🗄️ Fallback local results: ${localResults.size} users")
            localResults
        }
    }

    override suspend fun getUserById(userId: String): User? {
        // Local-first: быстрее и устойчивее к отменам корутин
        val local = try { userDao.getUserById(userId)?.toDomain() } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Local getUserById failed: $userId")
            null
        }
        if (local != null) return local

        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                UserFirestoreConverter.fromFirestore(doc.data ?: emptyMap(), doc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "getUserById Firestore failed for userId=$userId: ${e.message}")
            null
        }
    }

    override suspend fun getUserByIdFromFirestore(userId: String): User? {
        return try {
            Timber.tag("SocialRepository").d("🔄 Fetching user from Firestore: $userId")
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                val data = doc.data ?: emptyMap()
                val hasToken = !(data["fcmToken"] as? String).isNullOrBlank()
                Timber.tag("SocialRepository").d("📋 Firestore data for $userId: fcmToken=${if (hasToken) "EXISTS" else "EMPTY"}")
                
                val user = UserFirestoreConverter.fromFirestore(data, doc.id)
                // Обновляем локальный кэш с актуальными данными
                user?.let {
                    try {
                        var hasPending = false
                        try {
                            hasPending = syncManager.hasPendingOperations("user", it.id)
                        } catch (e: Exception) {
                            Timber.tag("SocialRepository").w("getUserByIdFromFirestore: hasPendingOperations failed for userId=${it.id}: ${e.message}")
                        }
                        if (!hasPending) {
                            upsertLocalUser(it.toEntity())
                            Timber.tag("SocialRepository").d("✅ Local cache updated for user $userId")
                        }
                    } catch (e: Exception) {
                        Timber.tag("SocialRepository").w(e, "Failed to update local cache for user $userId")
                    }
                }
                Timber.tag("SocialRepository").d("✅ User fetched: ${user?.name}")
                user
            } else {
                Timber.tag("SocialRepository").w("❌ User not found in Firestore: $userId")
                null
            }
        } catch (e: FirebaseFirestoreException) {
            // Не логгируем offline-ошибки - это ожидаемое поведение для offline-first приложения
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Timber.tag("SocialRepository").d("getUserByIdFromFirestore: device is offline")
            } else {
                Timber.tag("SocialRepository").w(e, "getUserByIdFromFirestore failed for userId=$userId: ${e.code}")
            }
            null
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "getUserByIdFromFirestore failed for userId=$userId")
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
                        val now = System.currentTimeMillis()
                        syncManager.enqueueArrayRemove(
                            entityType = "user",
                            entityId = doc.id,
                            fieldPath = "fcmTokens",
                            values = listOf(token),
                            updatedAt = now
                        )
                    }
                }
            } catch (e: Exception) {
                // Не критично: в худшем случае токен временно будет подвязан к двум аккаунтами
                Timber.tag("SocialRepository").w(e, "updateUserFcmToken cleanup (remove token from other users) failed: ${e.message}")
            }

            // 2) Добавляем токен текущему пользователю (массив + legacy)
            val now = System.currentTimeMillis()
            syncManager.enqueueArrayUnion(
                entityType = "user",
                entityId = userId,
                fieldPath = "fcmTokens",
                values = listOf(token),
                updatedAt = now
            )
            val payload = org.json.JSONObject()
                .put("fcmToken", token)
                .put("updatedAt", now)
                .toString()
            syncManager.enqueueUpsert(
                entityType = "user",
                entityId = userId,
                payloadJson = payload,
                updatedAt = now
            )
        } catch (e: Exception) {
            // Обновляем локально (сохраним единственный токен в legacy поле)
            Timber.tag("SocialRepository").w(e, "updateUserFcmToken remote update failed for user=$userId: ${e.message}")
            val user = userDao.getUserById(userId)
            user?.let {
                userDao.updateUser(it.copy(fcmToken = token))
            }
        }
    }

    override suspend fun updateFriendsCount(userId: String, count: Int) {
        val user = userDao.getUserById(userId)
        if (user != null) {
            val updatedUser = user.copy(friendsCount = count)
            userDao.updateUser(updatedUser)
            
            // Enqueue sync via Outbox
            val now = System.currentTimeMillis()
            val payload = SyncPayloads.userToJson(updatedUser.toDomain(), now)
            syncManager.enqueueUpsert(
                entityType = "user",
                entityId = userId,
                payloadJson = payload,
                updatedAt = now
            )
        } else {
            Timber.tag("SocialRepository").w("updateFriendsCount: User $userId not found locally")
        }
    }

    override suspend fun recalculateFriendsCount(userId: String) {
        val actualCount = friendshipDao.getFriendsCount(userId).first()
        updateFriendsCount(userId, actualCount)
        Timber.tag("SocialRepository").d("recalculateFriendsCount: userId=$userId, actualCount=$actualCount")
    }

    override suspend fun sendFriendRequest(
        fromUserId: String,
        toUserId: String,
        message: String?
    ): Result<FriendRequest> {
        return try {
            Timber.tag("SocialRepository").d("📤 Sending friend request: from=$fromUserId, to=$toUserId")
            
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val friendRequest = FriendRequest(
                id = UUID.randomUUID().toString(),
                fromUserId = fromUserId,
                toUserId = toUserId,
                status = FriendRequestStatus.PENDING,
                message = message,
                createdAt = now,
                respondedAt = null,
                updatedAt = now.date
            )
            // Room-first
            friendRequestDao.insertFriendRequest(friendRequest.toEntity())

            // Enqueue remote UPSERT
            val payload = SyncPayloads.friendRequestToJson(friendRequest)
            syncManager.enqueueUpsert(
                entityType = "friend_request",
                entityId = friendRequest.id,
                payloadJson = payload,
                updatedAt = friendRequest.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            )
            // Форсируем немедленный дренаж outbox, чтобы запись быстрее попала в Firestore
            try {
                syncWorkScheduler.scheduleOneTimeSync()
            } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "scheduleOneTimeSync() failed after enqueueUpsert(friend_request): ${e.message}")
            }

            Timber.tag("SocialRepository").d("✅ Friend request enqueued successfully!")

            // Fire-and-forget push для получателя (не блокируем основной флоу)
            repoScope.launch {
                runCatching { sendFriendRequestPush(fromUserId = fromUserId, toUserId = toUserId, message = message) }
                    .onFailure { e -> Timber.tag("SocialRepository").w("Push(friend_request) failed: ${e.message}") }
            }
            Result.success(friendRequest)
        } catch (e: Exception) {
            Timber.tag("SocialRepository").e(e, "❌ Failed to send friend request: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun sendFriendRequestPush(fromUserId: String, toUserId: String, message: String?) {
        // 1) Получим токены получателя из Firestore
        val userDoc = firestore.collection("users").document(toUserId).get().await()
        val tokens = mutableSetOf<String>()
        (userDoc.get("fcmTokens") as? List<*>)
            ?.filterIsInstance<String>()
            ?.filter { it.isNotBlank() }
            ?.let { tokens.addAll(it) }
        (userDoc.get("fcmToken") as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { tokens.add(it) }

        if (tokens.isEmpty()) {
            Timber.tag("SocialRepository").d("sendFriendRequestPush: no tokens for user=$toUserId — skip push")
            return
        }

        // 2) Попробуем достать имя отправителя для текста
        val fromName: String = try {
            val local = userDao.getUserById(fromUserId)?.toDomain()?.name
            if (!local.isNullOrBlank()) local else {
                val fromDoc = firestore.collection("users").document(fromUserId).get().await()
                (fromDoc.get("name") as? String) ?: stringProvider.getString(R.string.default_user_name)
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").d("sendFriendRequestPush: resolve fromName failed for fromUserId=$fromUserId: ${e.message}")
            stringProvider.getString(R.string.default_user_name)
        }

        val title = stringProvider.getString(R.string.notification_friend_request_title)
        val body = stringProvider.getString(R.string.notification_friend_request_body, fromName)

        // 3) Сформируем data payload
        val data = buildMap {
            put("type", "friend_request")
            put("recipientUserId", toUserId)
            put("fromUserId", fromUserId)
            put("fromUserName", fromName)
            message?.takeIf { it.isNotBlank() }?.let { put("message", it) }
        }

        // 4) Отправим батчем
        val result = fcmApiClient.sendToMultipleTokens(tokens.toList(), title, body, data)
        if (result.isSuccess) {
            val br = result.getOrNull()
            Timber.tag("SocialRepository").d("sendFriendRequestPush: sent=${br?.successCount ?: 0}/${br?.totalSent ?: tokens.size}")
        } else {
            Timber.tag("SocialRepository").w("sendFriendRequestPush: failed ${result.exceptionOrNull()?.message}")
        }
    }

    override suspend fun acceptFriendRequest(requestId: String): Result<Friendship> {
        return try {
            val friendRequest = getFriendRequestById(requestId)
                ?: return Result.failure(IllegalArgumentException(stringProvider.getString(R.string.error_request_not_found)))

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val acceptedRequest = friendRequest.copy(
                status = FriendRequestStatus.ACCEPTED,
                respondedAt = now,
                updatedAt = now.date
            )
            
            val friendship = createFriendship(
                userId1 = friendRequest.fromUserId,
                userId2 = friendRequest.toUserId,
                requestedBy = friendRequest.fromUserId
            ).copy(requestedAt = friendRequest.createdAt).accept()

            // Room-first
            friendRequestDao.updateFriendRequest(acceptedRequest.toEntity())
            
            // Важно: сначала сохраняем пользователей, чтобы избежать FOREIGN KEY constraint
            try {
                val fromUser = getUserById(friendRequest.fromUserId)
                val toUser = getUserById(friendRequest.toUserId)
                
                if (fromUser != null) {
                    upsertLocalUser(fromUser.toEntity())
                } else {
                    Timber.tag("SocialRepository").w("acceptFriendRequest: fromUser not found for id=${friendRequest.fromUserId}")
                }
                
                if (toUser != null) {
                    upsertLocalUser(toUser.toEntity())
                } else {
                    Timber.tag("SocialRepository").w("acceptFriendRequest: toUser not found for id=${friendRequest.toUserId}")
                }
            } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "acceptFriendRequest: user upsert failed: ${e.message}")
            }
            
            friendshipDao.insertFriendship(friendship.toEntity())

            // Enqueue remote UPSERTs
            val frPayload = SyncPayloads.friendRequestToJson(acceptedRequest)
            syncManager.enqueueUpsert(
                entityType = "friend_request",
                entityId = acceptedRequest.id,
                payloadJson = frPayload,
                updatedAt = acceptedRequest.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            )
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "scheduleOneTimeSync() failed after enqueueUpsert(friend_request accepted): ${e.message}")
            }

            val ordered = if (friendship.userId1 < friendship.userId2) Pair(friendship.userId1, friendship.userId2) else Pair(friendship.userId2, friendship.userId1)
            val fsPayload = SyncPayloads.friendshipToJson(friendship)
            syncManager.enqueueUpsert(
                entityType = "friendship",
                entityId = "${ordered.first}_${ordered.second}",
                payloadJson = fsPayload,
                updatedAt = friendship.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            )
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "scheduleOneTimeSync() failed after enqueueUpsert(friendship in acceptFriendRequest): ${e.message}")
            }

            // Обновляем счетчики друзей (getFriendsCount уже возвращает актуальное значение после вставки)
            val user1FriendsCount = friendshipDao.getFriendsCount(friendship.userId1).first()
            val user2FriendsCount = friendshipDao.getFriendsCount(friendship.userId2).first()
            updateFriendsCount(friendship.userId1, user1FriendsCount)
            updateFriendsCount(friendship.userId2, user2FriendsCount)

            Result.success(friendship)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineFriendRequest(requestId: String): Result<FriendRequest> {
        return try {
            val friendRequest = getFriendRequestById(requestId)
                ?: return Result.failure(IllegalArgumentException(stringProvider.getString(R.string.error_request_not_found)))

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val declinedRequest = friendRequest.copy(
                status = FriendRequestStatus.DECLINED,
                respondedAt = now,
                updatedAt = now.date
            )

            // Room-first
            friendRequestDao.updateFriendRequest(declinedRequest.toEntity())

            // Enqueue remote UPSERT
            val payload = SyncPayloads.friendRequestToJson(declinedRequest)
            syncManager.enqueueUpsert(
                entityType = "friend_request",
                entityId = declinedRequest.id,
                payloadJson = payload,
                updatedAt = declinedRequest.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            )
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "scheduleOneTimeSync() failed after declineFriendRequest: ${e.message}")
            }

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
            try { syncWorkScheduler.scheduleOneTimeSync() } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "scheduleOneTimeSync() failed after cancelFriendRequest: ${e.message}")
            }

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
                    val remote = doc.toObject(FriendRequestRemote::class.java)
                    remote?.toDomain(doc.id)
                } catch (e: Exception) {
                    Timber.tag("SocialRepository").d("getFriendRequestById: mapping failed for doc=${doc.id}: ${e.message}")
                    // fallback к Room, если маппинг не удался
                    friendRequestDao.getFriendRequestById(requestId)?.toDomain()
                }
            } else {
                friendRequestDao.getFriendRequestById(requestId)?.toDomain()
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "getFriendRequestById Firestore failed: ${e.message}")
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
                    val remote = doc.toObject(FriendRequestRemote::class.java)
                    remote?.toDomain(doc.id)
                } catch (e: Exception) {
                    Timber.tag("SocialRepository").d("getFriendRequestBetweenUsers: mapping failed for doc=${doc.id}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "getFriendRequestBetweenUsers Firestore failed: ${e.message}")
            friendRequestDao.getFriendRequestBetweenUsers(fromUserId, toUserId)?.toDomain()
        }
    }

    override fun getIncomingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return callbackFlow {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // Слушаем входящие friend_requests для текущего пользователя
            val registration = firestore.collection("friend_requests")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.tag("SocialRepository").w(error, "Incoming FR listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    Timber.tag("SocialRepository").d("Incoming FR listener: toUserId=$userId, docs=${docs.size}")
                    scope.launch {
                        val remoteIds = docs.map { it.id }.toSet()
                        docs.forEach { doc ->
                            try {
                                val remote = doc.toObject(FriendRequestRemote::class.java)
                                val fr = remote?.toDomain(doc.id)
                                if (fr != null) {
                                    val hasPending = try {
                                        syncManager.hasPendingOperations("friend_request", fr.id)
                                    } catch (e: Exception) {
                                        Timber.tag("SocialRepository").w("Incoming FR hasPendingOperations failed (id=${fr.id}): ${e.message}")
                                        false
                                    }
                                    if (!hasPending) {
                                        friendRequestDao.insertFriendRequest(fr.toEntity())
                                    }
                                    // Prefetch users to avoid UI dropping item due to null users
                                    try {
                                        getUserById(fr.fromUserId)?.let { upsertLocalUser(it.toEntity()) }
                                        getUserById(fr.toUserId)?.let { upsertLocalUser(it.toEntity()) }
                                    } catch (e: Exception) {
                                        Timber.tag("SocialRepository").d("Incoming FR prefetch users failed (non-fatal): ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.tag("SocialRepository").w("Incoming FR map/insert error: ${e.message}")
                            }
                        }
                        // Reconcile: удаляем локальные PENDING входящие, которых нет в снапшоте
                        try {
                            val local = friendRequestDao.getIncomingFriendRequests(userId).first()
                            val toDelete = local.filter { entity ->
                                if (entity.id in remoteIds) return@filter false
                                val hasPending = try {
                                    syncOutboxDao.hasPendingForEntity("friend_request", entity.id)
                                } catch (_: Exception) {
                                    false
                                }
                                !hasPending
                            }
                            if (toDelete.isNotEmpty()) {
                                Timber.tag("SocialRepository").d("Incoming FR reconcile: deleting ${toDelete.size} stale records")
                                toDelete.forEach { fr -> friendRequestDao.deleteFriendRequestById(fr.id) }
                            }
                        } catch (e: Exception) {
                            Timber.tag("SocialRepository").w(e, "Incoming FR reconcile failed: ${e.message}")
                        }
                    }
                }

            val job = scope.launch {
                friendRequestDao.getIncomingFriendRequests(userId).collect { requests ->
                    Timber.tag("SocialRepository").d("Incoming FR Room collect: userId=$userId, count=${requests.size}")
                    val list = requests.mapNotNull { requestEntity ->
                        val request = requestEntity.toDomain()
                        val fromUser = getUserById(request.fromUserId)
                        val toUser = getUserById(request.toUserId)
                        if (fromUser == null) Timber.tag("SocialRepository").w("Incoming FR: fromUser is null for ${request.fromUserId}")
                        if (toUser == null) Timber.tag("SocialRepository").w("Incoming FR: toUser is null for ${request.toUserId}")
                        if (fromUser != null && toUser != null) FriendRequestWithUsers(request, fromUser, toUser) else null
                    }
                    Timber.tag("SocialRepository").d("Incoming FR mapped: userId=$userId, count=${list.size}")
                    trySend(list)
                }
            }

            awaitClose {
                registration.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getOutgoingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return callbackFlow {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // Слушаем исходящие friend_requests от текущего пользователя
            val registration = firestore.collection("friend_requests")
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.tag("SocialRepository").w(error, "Outgoing FR listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    Timber.tag("SocialRepository").d("Outgoing FR listener: fromUserId=$userId, docs=${docs.size}")
                    scope.launch {
                        val remoteIds = docs.map { it.id }.toSet()
                        docs.forEach { doc ->
                            try {
                                val remote = doc.toObject(FriendRequestRemote::class.java)
                                val fr = remote?.toDomain(doc.id)
                                if (fr != null) {
                                    val hasPending = try {
                                        syncManager.hasPendingOperations("friend_request", fr.id)
                                    } catch (e: Exception) {
                                        Timber.tag("SocialRepository").w("Outgoing FR hasPendingOperations failed (id=${fr.id}): ${e.message}")
                                        false
                                    }
                                    if (!hasPending) {
                                        friendRequestDao.insertFriendRequest(fr.toEntity())
                                    }
                                    // Prefetch users to avoid UI dropping item due to null users
                                    try {
                                        getUserById(fr.fromUserId)?.let { upsertLocalUser(it.toEntity()) }
                                        getUserById(fr.toUserId)?.let { upsertLocalUser(it.toEntity()) }
                                    } catch (e: Exception) {
                                        Timber.tag("SocialRepository").d("Outgoing FR prefetch users failed (non-fatal): ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.tag("SocialRepository").w("Outgoing FR map/insert error: ${e.message}")
                            }
                        }
                        // Reconcile: удаляем локальные PENDING исходящие, которых нет в снапшоте
                        try {
                            val local = friendRequestDao.getOutgoingFriendRequests(userId).first()
                            val toDelete = local.filter { entity ->
                                if (entity.id in remoteIds) return@filter false
                                val hasPending = try {
                                    syncOutboxDao.hasPendingForEntity("friend_request", entity.id)
                                } catch (_: Exception) {
                                    false
                                }
                                !hasPending
                            }
                            if (toDelete.isNotEmpty()) {
                                Timber.tag("SocialRepository").d("Outgoing FR reconcile: deleting ${toDelete.size} stale records")
                                toDelete.forEach { fr -> friendRequestDao.deleteFriendRequestById(fr.id) }
                            }
                        } catch (e: Exception) {
                            Timber.tag("SocialRepository").w(e, "Outgoing FR reconcile failed: ${e.message}")
                        }
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
                registration.remove()
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
                    requestedAt = Instant.fromEpochMilliseconds(requestedAt).toLocalDateTime(TimeZone.currentSystemDefault()),
                    acceptedAt = acceptedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) },
                    blockedBy = blockedBy,
                    blockedAt = blockedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) },
                    updatedAt = Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
                )
            } else {
                return friendshipDao.getFriendshipBetweenUsers(userId1, userId2)?.toDomain()
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "getFriendshipBetweenUsers Firestore failed: ${e.message}")
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
                Timber.tag("SocialRepository").d("removeFriend: scheduled one-time sync for delete friendship doc=$docId")
                syncWorkScheduler.scheduleOneTimeSync()
            } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "scheduleOneTimeSync() failed after enqueueDelete(friendship): ${e.message}")
            }

            // Обновляем счетчики друзей (getFriendsCount уже возвращает актуальное значение после удаления)
            val user1FriendsCount = friendshipDao.getFriendsCount(currentUserId).first()
            val user2FriendsCount = friendshipDao.getFriendsCount(friendId).first()
            updateFriendsCount(currentUserId, user1FriendsCount)
            updateFriendsCount(friendId, user2FriendsCount)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockUser(currentUserId: String, userIdToBlock: String): Result<Friendship> {
        return try {
            val friendship = getFriendshipBetweenUsers(currentUserId, userIdToBlock)
                ?: return Result.failure(IllegalStateException("Дружба не найдена"))

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val blockedFriendship = friendship.copy(
                status = FriendshipStatus.BLOCKED,
                blockedBy = currentUserId,
                blockedAt = now
            )

            // Room-first
            friendshipDao.updateFriendship(blockedFriendship.toEntity())

            // Enqueue UPSERT
            val orderedIds = if (currentUserId < userIdToBlock) Pair(currentUserId, userIdToBlock) else Pair(userIdToBlock, currentUserId)
            val payload = SyncPayloads.friendshipToJson(blockedFriendship)
            syncManager.enqueueUpsert(
                entityType = "friendship",
                entityId = "${orderedIds.first}_${orderedIds.second}",
                payloadJson = payload,
                updatedAt = blockedFriendship.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            )

            Result.success(blockedFriendship)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String): Result<Friendship> {
        return try {
            val friendship = getFriendshipBetweenUsers(currentUserId, userIdToUnblock)
                ?: return Result.failure(IllegalStateException(stringProvider.getString(R.string.error_friendship_not_found)))

            val unblockedFriendship = friendship.copy(
                status = FriendshipStatus.ACCEPTED,
                blockedBy = null,
                blockedAt = null
            )

            // Room-first
            friendshipDao.updateFriendship(unblockedFriendship.toEntity())

            // Enqueue UPSERT
            val orderedIds = if (currentUserId < userIdToUnblock) Pair(currentUserId, userIdToUnblock) else Pair(userIdToUnblock, currentUserId)
            val payload = SyncPayloads.friendshipToJson(unblockedFriendship)
            syncManager.enqueueUpsert(
                entityType = "friendship",
                entityId = "${orderedIds.first}_${orderedIds.second}",
                payloadJson = payload,
                updatedAt = unblockedFriendship.updatedAt.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            )

            Result.success(unblockedFriendship)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFriends(userId: String): Flow<List<FriendshipWithUsers>> {
        return callbackFlow {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // Накопление ключей из обоих слушателей для reconcile
            var lastKeys1: Set<String> = emptySet()
            var lastKeys2: Set<String> = emptySet()
            var listener1Received = false
            var listener2Received = false
            suspend fun reconcileFriends() {
                // Ждём пока оба listener'а получат хотя бы один snapshot
                if (!listener1Received || !listener2Received) return
                val combined = (lastKeys1 + lastKeys2).toSet()
                try {
                    val local = friendshipDao.getFriendsForUser(userId).first()
                    val toDelete = local.filter { entity ->
                        val key = "${entity.userId1}_${entity.userId2}"
                        if (key in combined) return@filter false
                        // Не удаляем, если есть pending операция синхронизации
                        val hasPending = syncOutboxDao.hasPendingOperation("friendship", key) > 0
                        !hasPending
                    }
                    toDelete.forEach { entity ->
                        friendshipDao.deleteFriendshipBetweenUsers(entity.userId1, entity.userId2)
                    }
                } catch (_: Exception) { }
            }
            // Два слушателя: userId как userId1 и как userId2
            val reg1 = firestore.collection("friendships")
                .whereEqualTo("userId1", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.tag("SocialRepository").d("Friends listener1 error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    scope.launch {
                        listener1Received = true
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
                                // Гарантируем наличие пользователей в Room перед вставкой Friendship (FK constraints)
                                try {
                                    val u1 = getUserById(userId1)
                                    if (u1 != null) {
                                        upsertLocalUser(u1.toEntity())
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("SocialRepository").d("Prefetch user failed (userId1=$userId1): ${e.message}")
                                }
                                try {
                                    val u2 = getUserById(userId2)
                                    if (u2 != null) {
                                        upsertLocalUser(u2.toEntity())
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("SocialRepository").d("Prefetch user failed (userId2=$userId2): ${e.message}")
                                }
                                val status = (data["status"] as? String)?.let { runCatching { FriendshipStatus.valueOf(it.uppercase()) }.getOrNull() } ?: FriendshipStatus.PENDING
                                val requestedBy = data["requestedBy"] as? String ?: userId1
                                val requestedAt = parseTimestamp(data["requestedAt"])
                                val acceptedAt = data["acceptedAt"]?.let { parseTimestamp(it) }
                                val blockedBy = data["blockedBy"] as? String
                                val blockedAt = data["blockedAt"]?.let { parseTimestamp(it) }
                                val updatedAt = parseTimestamp(data["updatedAt"], requestedAt)

                                val fs = Friendship(
                                    userId1 = userId1,
                                    userId2 = userId2,
                                    status = status,
                                    requestedBy = requestedBy,
                                    requestedAt = Instant.fromEpochMilliseconds(requestedAt).toLocalDateTime(TimeZone.currentSystemDefault()),
                                    acceptedAt = acceptedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) },
                                    blockedBy = blockedBy,
                                    blockedAt = blockedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) },
                                    updatedAt = Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                )
                                val key = if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
                                val hasPending = try {
                                    syncOutboxDao.hasPendingForEntity("friendship", key)
                                } catch (_: Exception) {
                                    false
                                }
                                if (!hasPending) {
                                    friendshipDao.insertFriendship(fs.toEntity())
                                }
                            } catch (e: Exception) {
                                Timber.tag("SocialRepository").w("Friends listener1 mapping error: ${e.message}")
                            }
                        }
                        // Reconcile после обработки снапшота
                        reconcileFriends()
                    }
                }

            val reg2 = firestore.collection("friendships")
                .whereEqualTo("userId2", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.tag("SocialRepository").d("Friends listener2 error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    scope.launch {
                        listener2Received = true
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
                                // Гарантируем наличие пользователей в Room перед вставкой Friendship (FK constraints)
                                try {
                                    val u1 = getUserById(userId1)
                                    if (u1 != null) {
                                        upsertLocalUser(u1.toEntity())
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("SocialRepository").d("Friends listener2 prefetch user failed (userId1=$userId1): ${e.message}")
                                }
                                try {
                                    val u2 = getUserById(userId2)
                                    if (u2 != null) {
                                        upsertLocalUser(u2.toEntity())
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("SocialRepository").d("Friends listener2 prefetch user failed (userId2=$userId2): ${e.message}")
                                }
                                val status = (data["status"] as? String)?.let { runCatching { FriendshipStatus.valueOf(it.uppercase()) }.getOrNull() } ?: FriendshipStatus.PENDING
                                val requestedBy = data["requestedBy"] as? String ?: userId1
                                val requestedAt = parseTimestamp(data["requestedAt"])
                                val acceptedAt = data["acceptedAt"]?.let { parseTimestamp(it) }
                                val blockedBy = data["blockedBy"] as? String
                                val blockedAt = data["blockedAt"]?.let { parseTimestamp(it) }
                                val updatedAt = parseTimestamp(data["updatedAt"], requestedAt)

                                val fs = Friendship(
                                    userId1 = userId1,
                                    userId2 = userId2,
                                    status = status,
                                    requestedBy = requestedBy,
                                    requestedAt = Instant.fromEpochMilliseconds(requestedAt).toLocalDateTime(TimeZone.currentSystemDefault()),
                                    acceptedAt = acceptedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) },
                                    blockedBy = blockedBy,
                                    blockedAt = blockedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) },
                                    updatedAt = Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                )
                                val key = if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
                                val hasPending = try {
                                    syncOutboxDao.hasPendingForEntity("friendship", key)
                                } catch (_: Exception) {
                                    false
                                }
                                if (!hasPending) {
                                    friendshipDao.insertFriendship(fs.toEntity())
                                }
                            } catch (e: Exception) {
                                Timber.tag("SocialRepository").w("Friends listener2 mapping error: ${e.message}")
                            }
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
                            // Используем Firestore для получения актуальных данных (включая lastActiveAt)
                            val friendId = if (friendship.userId1 == userId) friendship.userId2 else friendship.userId1
                            val currentUser = runBlocking { getUserById(userId) }
                            val friendUser = runBlocking { getUserByIdFromFirestore(friendId) }
                            if (currentUser != null && friendUser != null) {
                                // Сохраняем порядок user1/user2 как в friendship
                                if (friendship.userId1 == userId) {
                                    FriendshipWithUsers(friendship, currentUser, friendUser)
                                } else {
                                    FriendshipWithUsers(friendship, friendUser, currentUser)
                                }
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }.filter { it.friendship.status == FriendshipStatus.ACCEPTED }
                    trySend(list)
                }
            }

            awaitClose {
                reg1.remove()
                reg2.remove()
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
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        try {
            friendRequestDao.expireOldRequests(thirtyDaysAgo)
        } catch (e: Exception) {
            Timber.tag("SocialRepository").d("expireOldFriendRequests failed (non-fatal): ${e.message}")
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
                Timber.tag("SocialRepository").w("FCM skip: friend_request -> to=$toUserId, tokens_absent")
                return
            }

            Timber.tag("SocialRepository").d(
                "FCM send: friend_request -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}"
            )

            // Отправка на все токены и очистка невалидных (UNREGISTERED)
            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = stringProvider.getString(R.string.notification_friend_request_title),
                body = stringProvider.getString(R.string.notification_friend_request_body, fromUserName),
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
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED (friend_request) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Timber.tag("SocialRepository").w("Ошибка отправки friend_request batch: ${e.message}")
            }
        } catch (e: Exception) {
            // Логируем ошибку, но не падаем - уведомления не критичны
            Timber.tag("SocialRepository").w(e, "Ошибка отправки уведомления о запросе дружбы")
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
                Timber.tag("SocialRepository").w("FCM skip: friend_accepted -> to=$toUserId, tokens_absent")
                return
            }

            Timber.tag("SocialRepository").d(
                "FCM send: friend_accepted -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, acceptedBy=$acceptedByName"
            )

            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = stringProvider.getString(R.string.notification_friend_request_title),
                body = stringProvider.getString(R.string.notification_friend_accepted_body, acceptedByName),
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
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED (friend_accepted) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Timber.tag("SocialRepository").w("Ошибка отправки friend_accepted batch: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка отправки уведомления о принятии дружбы")
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
                return Result.failure(Exception(stringProvider.getString(R.string.error_cannot_send_invite)))
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
                createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                respondedAt = null,
                expiresAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { 
                    val instant = it.toInstant(TimeZone.currentSystemDefault())
                    instant.plus(kotlin.time.Duration.parse("P7D")).toLocalDateTime(TimeZone.currentSystemDefault())
                } // Истекает через 7 дней
            )

            // Room-first
            Timber.tag("SocialRepository").d("[sendHabitInvitation] insert Room: invitationId=${invitation.id}, habitId=${invitation.habitId}, toUserId=${invitation.toUserId}")
            habitInvitationDao.insert(invitation.toEntity())
            Timber.tag("SocialRepository").d("[sendHabitInvitation] insert Room: DONE invitationId=${invitation.id}")

            // Enqueue UPSERT to Firestore via Outbox
            val createdAtMillis = invitation.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val payload = SyncPayloads.habitInvitationToJson(invitation, createdAtMillis)
            Timber.tag("SocialRepository").d(
                "[sendHabitInvitation] enqueueUpsert Outbox: type=habit_invitation, id=${invitation.id}, payloadSize=${payload.length}"
            )
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitation.id,
                payloadJson = payload,
                updatedAt = createdAtMillis
            )
            // Форсируем немедленный дренаж outbox, чтобы приглашение быстрее появилось в Firestore
            try {
                Timber.tag("SocialRepository").d("[sendHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Timber.tag("SocialRepository").d("[sendHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "[sendHabitInvitation] scheduleOneTimeSync() failed: ${e.message}")
            }
            // Дополнительно: мгновенный дренаж в бекграунде (устойчив к задержкам WorkManager)
            repoScope.launch {
                try {
                    Timber.tag("SocialRepository").d("[sendHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Timber.tag("SocialRepository").d("[sendHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Timber.tag("SocialRepository").w(e, "[sendHabitInvitation] inline drainPending() failed: ${e.message}")
                }
            }

            // Отправляем FCM уведомление
            Timber.tag("SocialRepository").d(
                "[sendHabitInvitation] sendHabitInvitationNotification -> to=$toUserId, from=$fromUserName, habitTitle=$habitTitle"
            )
            sendHabitInvitationNotification(toUserId, fromUserName, habitTitle)
            Timber.tag("SocialRepository").d("[sendHabitInvitation] sendHabitInvitationNotification -> DONE")

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
                respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            )

            // Room-first update
            Timber.tag("SocialRepository").d("[acceptHabitInvitation] update Room -> ACCEPTED: invitationId=$invitationId")
            habitInvitationDao.update(acceptedInvitation.toEntity())
            Timber.tag("SocialRepository").d("[acceptHabitInvitation] update Room DONE: invitationId=$invitationId")

            // Enqueue UPSERT for invitation status
            val nowAccepted = acceptedInvitation.respondedAt?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds()
            val payload = SyncPayloads.habitInvitationToJson(acceptedInvitation, nowAccepted)
            Timber.tag("SocialRepository").d("[acceptHabitInvitation] enqueueUpsert Outbox: type=habit_invitation, id=$invitationId")
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitationId,
                payloadJson = payload,
                updatedAt = nowAccepted
            )
            // Форсируем немедленный дренаж outbox для обновления статуса приглашения
            try {
                Timber.tag("SocialRepository").d("[acceptHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Timber.tag("SocialRepository").d("[acceptHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "[acceptHabitInvitation] scheduleOneTimeSync() failed: ${e.message}")
            }
            // Дополнительно: мгновенный дренаж в бекграунде
            repoScope.launch {
                try {
                    Timber.tag("SocialRepository").d("[acceptHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Timber.tag("SocialRepository").d("[acceptHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Timber.tag("SocialRepository").w(e, "[acceptHabitInvitation] inline drainPending() failed: ${e.message}")
                }
            }

            // Добавляем пользователя к участникам привычки локально
            // HabitRepositoryImpl handles this via Local + Outbox sync
            habitRepository.addParticipantToHabit(invitation.habitId, invitation.toUserId)
            
            // Отправляем FCM уведомление создателю привычки
            val acceptedUserName = try {
                getUserById(invitation.toUserId)?.name ?: invitation.toUserId
            }
            catch (e: Exception) {
                Timber.tag("SocialRepository").d("acceptHabitInvitation: resolve acceptedUserName failed (non-fatal): ${e.message}")
                invitation.toUserId
            }
            Timber.tag("SocialRepository").d(
                "[acceptHabitInvitation] sendHabitInvitationAcceptedNotification -> to=${invitation.fromUserId}, acceptedBy=$acceptedUserName, habit=${invitation.habitTitle}"
            )
            sendHabitInvitationAcceptedNotification(
                toUserId = invitation.fromUserId,
                acceptedByName = acceptedUserName,
                habitTitle = invitation.habitTitle,
                habitId = invitation.habitId
            )
            Timber.tag("SocialRepository").d("[acceptHabitInvitation] sendHabitInvitationAcceptedNotification -> DONE")

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
                respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            )

            // Room-first update
            Timber.tag("SocialRepository").d("[declineHabitInvitation] update Room -> DECLINED: invitationId=$invitationId")
            habitInvitationDao.update(declinedInvitation.toEntity())
            Timber.tag("SocialRepository").d("[declineHabitInvitation] update Room DONE: invitationId=$invitationId")

            // Enqueue UPSERT for invitation status
            val nowDeclined = declinedInvitation.respondedAt?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds()
            val payload = SyncPayloads.habitInvitationToJson(declinedInvitation, nowDeclined)
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitationId,
                payloadJson = payload,
                updatedAt = nowDeclined
            )
            // Форсируем немедленный дренаж outbox для обновления статуса приглашения
            try {
                Timber.tag("SocialRepository").d("[declineHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Timber.tag("SocialRepository").d("[declineHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "[declineHabitInvitation] scheduleOneTimeSync() failed: ${e.message}")
            }
            // Дополнительно: мгновенный дренаж в бекграунде
            repoScope.launch {
                try {
                    Timber.tag("SocialRepository").d("[declineHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Timber.tag("SocialRepository").d("[declineHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Timber.tag("SocialRepository").w(e, "[declineHabitInvitation] inline drainPending() failed: ${e.message}")
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
                respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            )

            // Room-first update
            Timber.tag("SocialRepository").d("[cancelHabitInvitation] update Room -> CANCELLED: invitationId=$invitationId")
            habitInvitationDao.update(cancelledInvitation.toEntity())
            Timber.tag("SocialRepository").d("[cancelHabitInvitation] update Room DONE: invitationId=$invitationId")

            // Enqueue UPSERT for invitation status
            val nowCancelled = cancelledInvitation.respondedAt?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds()
            val payload = SyncPayloads.habitInvitationToJson(cancelledInvitation, nowCancelled)
            Timber.tag("SocialRepository").d("[cancelHabitInvitation] enqueueUpsert Outbox: type=habit_invitation, id=$invitationId")
            syncManager.enqueueUpsert(
                entityType = "habit_invitation",
                entityId = invitationId,
                payloadJson = payload,
                updatedAt = nowCancelled
            )
            // Форсируем немедленный дренаж outbox для обновления статуса приглашения
            try {
                Timber.tag("SocialRepository").d("[cancelHabitInvitation] scheduleOneTimeSync() -> start")
                syncWorkScheduler.scheduleOneTimeSync()
                Timber.tag("SocialRepository").d("[cancelHabitInvitation] scheduleOneTimeSync() -> enqueued")
            } catch (e: Exception) {
                Timber.tag("SocialRepository").w(e, "[cancelHabitInvitation] scheduleOneTimeSync() failed: ${e.message}")
            }
            // Дополнительно: мгновенный дренаж в бекграунде
            repoScope.launch {
                try {
                    Timber.tag("SocialRepository").d("[cancelHabitInvitation] inline drainPending() -> start")
                    syncManager.drainPending(maxBatch = 10)
                    Timber.tag("SocialRepository").d("[cancelHabitInvitation] inline drainPending() -> done")
                } catch (e: Exception) {
                    Timber.tag("SocialRepository").w(e, "[cancelHabitInvitation] inline drainPending() failed: ${e.message}")
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
            Timber.tag("SocialRepository").w(e, "getHabitInvitationById Firestore failed: ${e.message}")
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
            Timber.tag("SocialRepository").w(e, "getPendingHabitInvitation Firestore failed: ${e.message}")
            habitInvitationDao.getPendingInvitation(habitId, userId)?.toDomain()
        }
    }

    override fun getIncomingHabitInvitations(userId: String): Flow<List<HabitInvitation>> {
        // Реальное время: слушаем Firestore и мержим в локальный Room, отдаём Flow из Room
        return callbackFlow {
            // Отдельный scope для DAO-коллекции внутри callbackFlow
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            // 1) Стартуем слушатель Firestore
            val registration = firestore.collection("habit_invitations")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", HabitInvitationStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Логируем и выходим, локальные данные остаются
                        Timber.tag("SocialRepository").w(error, "[getIncomingHabitInvitations] listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    val remoteIds = docs.map { it.id }.toSet()
                    Timber.tag("SocialRepository").d("[getIncomingHabitInvitations] snapshot size=${docs.size}")

                    val list = docs.mapNotNull { doc ->
                        doc.toObject(com.habittracker.data.remote.model.HabitInvitationRemote::class.java)
                            ?.toDomain(doc.id)
                    }

                    // Апсертим локально для консистентности (и для счётчиков)
                    scope.launch {
                        try {
                            val filtered = mutableListOf<com.habittracker.data.local.entity.HabitInvitationEntity>()
                            for (inv in list) {
                                val hasPending = try {
                                    syncManager.hasPendingOperations("habit_invitation", inv.id)
                                } catch (_: Exception) {
                                    false
                                }
                                if (!hasPending) filtered.add(inv.toEntity())
                            }
                            if (filtered.isNotEmpty()) {
                                habitInvitationDao.insertAll(filtered)
                            }
                            Timber.tag("SocialRepository").d("[getIncomingHabitInvitations] upsert=${list.size}")
                            // Reconcile: удаляем локальные PENDING входящие, которых нет в снапшоте
                            try {
                                val local = habitInvitationDao.getIncomingInvitations(userId).first()
                                val toDelete = local.filter { entity ->
                                    if (entity.id in remoteIds) return@filter false
                                    val hasPending = try {
                                        syncOutboxDao.hasPendingForEntity("habit_invitation", entity.id)
                                    } catch (_: Exception) {
                                        false
                                    }
                                    !hasPending
                                }
                                if (toDelete.isNotEmpty()) {
                                    Timber.tag("SocialRepository").d("[getIncomingHabitInvitations] reconcile delete=${toDelete.size}")
                                    toDelete.forEach { inv -> habitInvitationDao.deleteById(inv.id) }
                                }
                            } catch (e: Exception) {
                                Timber.tag("SocialRepository").w(e, "[getIncomingHabitInvitations] reconcile failed: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Timber.tag("SocialRepository").w(e, "[getIncomingHabitInvitations] insertAll failed: ${e.message}")
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
                registration.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getOutgoingHabitInvitations(userId: String): Flow<List<HabitInvitation>> {
        return callbackFlow {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            // Firestore listener: исходящие приглашения от пользователя
            val registration = firestore.collection("habit_invitations")
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("status", HabitInvitationStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    val remoteIds = docs.map { it.id }.toSet()
                    val list = docs.mapNotNull { doc ->
                        doc.toObject(com.habittracker.data.remote.model.HabitInvitationRemote::class.java)
                            ?.toDomain(doc.id)
                    }

                    scope.launch {
                        try {
                            val filtered = mutableListOf<com.habittracker.data.local.entity.HabitInvitationEntity>()
                            for (inv in list) {
                                val hasPending = try {
                                    syncManager.hasPendingOperations("habit_invitation", inv.id)
                                } catch (_: Exception) {
                                    false
                                }
                                if (!hasPending) filtered.add(inv.toEntity())
                            }
                            if (filtered.isNotEmpty()) {
                                habitInvitationDao.insertAll(filtered)
                            }
                            Timber.tag("SocialRepository").d("[getOutgoingHabitInvitations] upsert=${list.size}")
                            // Reconcile: удаляем локальные PENDING исходящие, которых нет в снапшоте
                            try {
                                val local = habitInvitationDao.getOutgoingInvitations(userId).first()
                                val toDelete = local.filter { entity ->
                                    if (entity.id in remoteIds) return@filter false
                                    val hasPending = try {
                                        syncOutboxDao.hasPendingForEntity("habit_invitation", entity.id)
                                    } catch (_: Exception) {
                                        false
                                    }
                                    !hasPending
                                }
                                if (toDelete.isNotEmpty()) {
                                    Timber.tag("SocialRepository").d("[getOutgoingHabitInvitations] reconcile delete=${toDelete.size}")
                                    toDelete.forEach { inv -> habitInvitationDao.deleteById(inv.id) }
                                }
                            } catch (e: Exception) {
                                Timber.tag("SocialRepository").w(e, "[getOutgoingHabitInvitations] reconcile failed: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Timber.tag("SocialRepository").w(e, "[getOutgoingHabitInvitations] insertAll failed: ${e.message}")
                        }
                    }
                }

            val job = scope.launch {
                habitInvitationDao.getOutgoingInvitations(userId).collect { entities ->
                    trySend(entities.map { it.toDomain() })
                }
            }

            awaitClose {
                registration.remove()
                job.cancel()
                scope.cancel()
            }
        }
    }

    override fun getHabitInvitations(habitId: String): Flow<List<HabitInvitation>> {
        return callbackFlow {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                            val filtered = mutableListOf<com.habittracker.data.local.entity.HabitInvitationEntity>()
                            for (inv in list) {
                                val hasPending = try {
                                    syncManager.hasPendingOperations("habit_invitation", inv.id)
                                } catch (_: Exception) {
                                    false
                                }
                                if (!hasPending) filtered.add(inv.toEntity())
                            }
                            if (filtered.isNotEmpty()) {
                                habitInvitationDao.insertAll(filtered)
                            }
                        } catch (e: Exception) {
                            Timber.tag("SocialRepository").w(e, "[observeHabitInvitationsByHabit] insertAll failed: ${e.message}")
                        }
                    }
                }

            val job = scope.launch {
                habitInvitationDao.getHabitInvitations(habitId).collect { entities ->
                    trySend(entities.map { it.toDomain() })
                }
            }

            awaitClose {
                registration.remove()
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
            Timber.tag("SocialRepository").w(e, "getActiveOutgoingInvitationsCount failed: ${e.message}")
            0
        }
    }

    override suspend fun expireOldHabitInvitations() {
        val currentTime = System.currentTimeMillis()
        try {
            habitInvitationDao.expireOldInvitations(currentTime)
            habitInvitationDao.deleteOldInvitations(currentTime - (30L * 24 * 60 * 60 * 1000)) // Удаляем старые через 30 дней
        } catch (e: Exception) {
            Timber.tag("SocialRepository").d("expireOldHabitInvitations failed (non-fatal): ${e.message}")
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
            // Получаем все актуальные токены получателя: массив fcmTokens + fallback fcmToken
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Timber.tag("SocialRepository").w("FCM skip: habit_invitation -> to=$toUserId, tokens_absent")
                return
            }

            Timber.tag("SocialRepository").d(
                "FCM send: habit_invitation -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}"
            )

            allTokens.forEach { token ->
                val result = fcmApiClient.sendPushNotification(
                    fcmToken = token,
                    title = stringProvider.getString(R.string.notification_habit_invitation_title),
                    body = stringProvider.getString(R.string.notification_habit_invitation_body, fromUserName, habitTitle),
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
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED -> remove token ${maskToken(token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, token)
                    } else {
                        Timber.tag("SocialRepository").w("FCM send failed for token ${maskToken(token)}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка подготовки уведомления о приглашении в привычку (outer)")
        }
    }

    override suspend fun sendHabitInvitationAcceptedNotification(
        toUserId: String, 
        acceptedByName: String, 
        habitTitle: String,
        habitId: String
    ) {
        try {
            // Сохраняем уведомление в БД для отображения на NotificationsScreen
            val message = stringProvider.getString(R.string.notification_habit_invitation_accepted, acceptedByName, habitTitle)
            val notificationEntity = com.habittracker.data.local.entity.GroupHabitNotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = toUserId,
                habitId = habitId,
                type = "INVITATION_ACCEPTED",
                participantId = "", // ID принявшего пользователя неизвестен здесь
                participantName = acceptedByName,
                habitTitle = habitTitle,
                message = message,
                isRead = false,
                readAt = null,
                createdAt = System.currentTimeMillis()
            )
            
            notificationRepository.saveNotification(notificationEntity)
            Timber.tag("SocialRepository").d("Notification saved to DB: INVITATION_ACCEPTED for user $toUserId")
            
            // Отправляем FCM push
            repoScope.launch {
                withContext(NonCancellable + Dispatchers.IO) {
                    val userDoc = firestore.collection("users").document(toUserId).get().await()
                    val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val legacyToken = userDoc.getString("fcmToken")
                    val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet()

                    if (allTokens.isEmpty()) {
                        Timber.tag("SocialRepository").w("FCM skip: habit_invitation_accepted -> to=$toUserId, tokens_absent")
                        return@withContext
                    }

                    Timber.tag("SocialRepository").d(
                        "FCM send: habit_invitation_accepted -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, recipientUserId=$toUserId, acceptedBy=$acceptedByName, habit='$habitTitle'"
                    )

                    allTokens.forEach { token ->
                        val result = fcmApiClient.sendPushNotification(
                            fcmToken = token,
                            title = stringProvider.getString(R.string.notification_habit_invitation_accepted_title),
                            body = message,
                            data = mapOf(
                                "type" to "habit_invitation_accepted",
                                "habitTitle" to habitTitle,
                                "habit_id" to habitId,
                                "acceptedByName" to acceptedByName,
                                "action" to "open_habit_details",
                                "recipientUserId" to toUserId
                            )
                        )
                        result.onFailure { e ->
                            val msg = e.message ?: ""
                            if (msg.contains("UNREGISTERED", ignoreCase = true)) {
                                Timber.tag("SocialRepository").w("FCM UNREGISTERED -> remove token ${maskToken(token)} for user=$toUserId")
                                safelyRemoveInvalidFcmToken(toUserId, token)
                            } else {
                                Timber.tag("SocialRepository").w("FCM send failed for token ${maskToken(token)}: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка подготовки уведомления о принятии приглашения (outer)")
        }
    }

    override suspend fun sendParticipantJoinedNotification(
        toUserId: String,
        joinedUserName: String,
        habitTitle: String,
        habitId: String
    ) {
        try {
            // Сохраняем уведомление в БД для отображения на NotificationsScreen
            val message = stringProvider.getString(R.string.notification_participant_joined, joinedUserName, habitTitle)
            val notificationEntity = com.habittracker.data.local.entity.GroupHabitNotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = toUserId,
                habitId = habitId,
                type = "FRIEND_JOINED",
                participantId = "", // ID присоединившегося пользователя неизвестен здесь
                participantName = joinedUserName,
                habitTitle = habitTitle,
                message = message,
                isRead = false,
                readAt = null,
                createdAt = System.currentTimeMillis()
            )
            
            notificationRepository.saveNotification(notificationEntity)
            Timber.tag("SocialRepository").d("Notification saved to DB: FRIEND_JOINED for user $toUserId")
            
            // Отправляем FCM push
            repoScope.launch {
                withContext(NonCancellable + Dispatchers.IO) {
                    val userDoc = firestore.collection("users").document(toUserId).get().await()
                    val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val legacyToken = userDoc.getString("fcmToken")
                    val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

                    if (allTokens.isEmpty()) {
                        Timber.tag("SocialRepository").w("FCM skip: participant_joined -> to=$toUserId, tokens_absent")
                        return@withContext
                    }

                    Timber.tag("SocialRepository").d(
                        "FCM send: participant_joined -> to=$toUserId, tokens_count=${allTokens.size}, joinedUser=$joinedUserName, habit='$habitTitle'"
                    )

                    allTokens.forEach { token ->
                        val result = fcmApiClient.sendPushNotification(
                            fcmToken = token,
                            title = stringProvider.getString(R.string.notification_participant_joined_title),
                            body = message,
                            data = mapOf(
                                "type" to "new_participant",
                                "habitTitle" to habitTitle,
                                "habit_id" to habitId,
                                "participant_name" to joinedUserName,
                                "action" to "open_habit_details",
                                "recipientUserId" to toUserId
                            )
                        )
                        result.onFailure { e ->
                            val msg = e.message ?: ""
                            if (msg.contains("UNREGISTERED", ignoreCase = true)) {
                                Timber.tag("SocialRepository").w("FCM UNREGISTERED -> remove token ${maskToken(token)} for user=$toUserId")
                                safelyRemoveInvalidFcmToken(toUserId, token)
                            } else {
                                Timber.tag("SocialRepository").w("FCM send failed for token ${maskToken(token)}: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка подготовки уведомления о присоединении участника (outer)")
        }
    }

    override suspend fun sendNudgeNotification(
        toUserId: String,
        fromUserName: String,
        habitTitle: String,
        habitId: String
    ) {
        Timber.tag("SocialRepository").d("sendNudgeNotification called: toUserId=$toUserId, from=$fromUserName, habit=$habitTitle")
        try {
            // Отправляем FCM push
            repoScope.launch {
                withContext(NonCancellable + Dispatchers.IO) {
                    val userDoc = firestore.collection("users").document(toUserId).get().await()
                    val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val legacyToken = userDoc.getString("fcmToken")
                    val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

                    if (allTokens.isEmpty()) {
                        Timber.tag("SocialRepository").w("FCM skip: nudge -> to=$toUserId, tokens_absent")
                        return@withContext
                    }

                    Timber.tag("SocialRepository").d(
                        "FCM send: nudge -> to=$toUserId, tokens_count=${allTokens.size}, from=$fromUserName, habit='$habitTitle'"
                    )

                    val title = stringProvider.getString(R.string.nudge_notification_title, fromUserName)
                    val body = stringProvider.getString(R.string.nudge_notification_body, habitTitle)

                    allTokens.forEach { token ->
                        Timber.tag("SocialRepository").d("Sending nudge FCM to token: ${maskToken(token)}")
                        val result = fcmApiClient.sendPushNotification(
                            fcmToken = token,
                            title = title,
                            body = body,
                            data = mapOf(
                                "type" to "nudge",
                                "habitTitle" to habitTitle,
                                "habit_id" to habitId,
                                "fromUserName" to fromUserName,
                                "action" to "open_habit_details",
                                "recipientUserId" to toUserId
                            )
                        )
                        result.onSuccess {
                            Timber.tag("SocialRepository").d("✅ Nudge FCM sent successfully to ${maskToken(token)}")
                        }
                        result.onFailure { e ->
                            val msg = e.message ?: ""
                            if (msg.contains("UNREGISTERED", ignoreCase = true)) {
                                Timber.tag("SocialRepository").w("FCM UNREGISTERED -> remove token ${maskToken(token)} for user=$toUserId")
                                safelyRemoveInvalidFcmToken(toUserId, token)
                            } else {
                                Timber.tag("SocialRepository").w("FCM send failed for token ${maskToken(token)}: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка отправки nudge уведомления (outer)")
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

                Timber.tag("SocialRepository").d(
                    "FCM cleanup: start user=$userId, token=${maskToken(badToken)}, before_tokens=$beforeSize, legacy_match=$legacyMatched"
                )

                val updates = mutableMapOf<String, Any>("fcmTokens" to newTokens)
                if (legacyMatched) {
                    updates["fcmToken"] = FieldValue.delete()
                }
                tx.update(userRef, updates as Map<String, Any>)
            }.await()
            Timber.tag("SocialRepository").d(
                "FCM cleanup: done user=$userId, removed token=${maskToken(badToken)}"
            )
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "FCM cleanup: failed user=$userId, token=${maskToken(badToken)} -> ${e.message}")
        }
    }

    override suspend fun sendChallengeInvitationNotification(toUserId: String, fromUserName: String, challengeId: String, challengeTitle: String, habitTitle: String) {
        try {
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Timber.tag("SocialRepository").w("FCM skip: challenge_invitation -> to=$toUserId, tokens_absent")
                return
            }

            Timber.tag("SocialRepository").d(
                "FCM send: challenge_invitation -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, challenge='$challengeTitle', habit='$habitTitle'"
            )

            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = stringProvider.getString(R.string.notification_challenge_invitation_title),
                body = stringProvider.getString(R.string.notification_challenge_invitation_body, fromUserName, challengeTitle),
                data = mapOf(
                    "type" to "challenge_invitation",
                    "challengeId" to challengeId,
                    "challenge_id" to challengeId,
                    "challengeTitle" to challengeTitle,
                    "habitTitle" to habitTitle,
                    "recipientUserId" to toUserId
                )
            )
            batchResult.onSuccess { result ->
                result.results.filter { !it.success && (it.error?.contains("UNREGISTERED", ignoreCase = true) == true) }
                    .forEach { bad ->
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED (challenge_invitation) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Timber.tag("SocialRepository").w("Ошибка отправки challenge_invitation batch: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка отправки challenge_invitation")
        }
    }
    
    override suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeId: String, challengeTitle: String) {
        try {
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Timber.tag("SocialRepository").w("FCM skip: challenge_joined -> to=$toUserId, tokens_absent")
                return
            }

            Timber.tag("SocialRepository").d(
                "FCM send: challenge_joined -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, challenge='$challengeTitle', joined='$joinedUserName'"
            )

            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = stringProvider.getString(R.string.notification_challenge_joined_title),
                body = stringProvider.getString(R.string.notification_challenge_joined_body, joinedUserName, challengeTitle),
                data = mapOf(
                    "type" to "challenge_joined",
                    "challengeId" to challengeId,
                    "challenge_id" to challengeId,
                    "challengeTitle" to challengeTitle,
                    "recipientUserId" to toUserId
                )
            )
            batchResult.onSuccess { result ->
                result.results.filter { !it.success && (it.error?.contains("UNREGISTERED", ignoreCase = true) == true) }
                    .forEach { bad ->
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED (challenge_joined) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Timber.tag("SocialRepository").w("Ошибка отправки challenge_joined batch: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка отправки challenge_joined")
        }
    }
    
    override suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeId: String, challengeTitle: String) {
        try {
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Timber.tag("SocialRepository").w("FCM skip: challenge_left -> to=$toUserId, tokens_absent")
                return
            }

            Timber.tag("SocialRepository").d(
                "FCM send: challenge_left -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, challenge='$challengeTitle', left='$leftUserName'"
            )

            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = stringProvider.getString(R.string.notification_participant_left_title),
                body = stringProvider.getString(R.string.notification_participant_left_body, leftUserName, challengeTitle),
                data = mapOf(
                    "type" to "challenge_left",
                    "challengeId" to challengeId,
                    "challenge_id" to challengeId,
                    "challengeTitle" to challengeTitle,
                    "recipientUserId" to toUserId
                )
            )
            batchResult.onSuccess { result ->
                result.results.filter { !it.success && (it.error?.contains("UNREGISTERED", ignoreCase = true) == true) }
                    .forEach { bad ->
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED (challenge_left) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Timber.tag("SocialRepository").w("Ошибка отправки challenge_left batch: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка отправки challenge_left")
        }
    }
    
    override suspend fun sendChallengeCompletedNotification(toUserId: String, challengeId: String, challengeTitle: String, message: String, isWinner: Boolean) {
        try {
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Timber.tag("SocialRepository").w("FCM skip: challenge_completed -> to=$toUserId, tokens_absent")
                return
            }

            val title = if (isWinner) {
                stringProvider.getString(R.string.notification_congrats_title)
            } else {
                stringProvider.getString(R.string.notification_challenge_completed_title)
            }
            Timber.tag("SocialRepository").d(
                "FCM send: challenge_completed -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, challenge='$challengeTitle', isWinner=$isWinner"
            )

            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = title,
                body = message,
                data = mapOf(
                    "type" to "challenge_completed",
                    "challengeId" to challengeId,
                    "challenge_id" to challengeId,
                    "challengeTitle" to challengeTitle,
                    "isWinner" to isWinner.toString(),
                    "recipientUserId" to toUserId
                )
            )
            batchResult.onSuccess { result ->
                result.results.filter { !it.success && (it.error?.contains("UNREGISTERED", ignoreCase = true) == true) }
                    .forEach { bad ->
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED (challenge_completed) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Timber.tag("SocialRepository").w("Ошибка отправки challenge_completed batch: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка отправки challenge_completed")
        }
    }
    
    override suspend fun sendChallengeCancelledNotification(toUserId: String, challengeId: String, challengeTitle: String, reason: String) {
        try {
            val userDoc = firestore.collection("users").document(toUserId).get().await()
            val tokensFromArray = (userDoc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val legacyToken = userDoc.getString("fcmToken")
            val allTokens = (tokensFromArray + listOfNotNull(legacyToken)).toSet().toList()

            if (allTokens.isEmpty()) {
                Timber.tag("SocialRepository").w("FCM skip: challenge_cancelled -> to=$toUserId, tokens_absent")
                return
            }

            Timber.tag("SocialRepository").d(
                "FCM send: challenge_cancelled -> to=$toUserId, tokens_count=${allTokens.size}, tokens=${allTokens.map { maskToken(it) }}, challenge='$challengeTitle'"
            )

            val batchResult = fcmApiClient.sendToMultipleTokens(
                tokens = allTokens,
                title = stringProvider.getString(R.string.notification_challenge_cancelled_title),
                body = stringProvider.getString(R.string.notification_challenge_cancelled_body, challengeTitle, reason),
                data = mapOf(
                    "type" to "challenge_cancelled",
                    "challengeId" to challengeId,
                    "challenge_id" to challengeId,
                    "challengeTitle" to challengeTitle,
                    "reason" to reason,
                    "recipientUserId" to toUserId
                )
            )
            batchResult.onSuccess { result ->
                result.results.filter { !it.success && (it.error?.contains("UNREGISTERED", ignoreCase = true) == true) }
                    .forEach { bad ->
                        Timber.tag("SocialRepository").w("FCM UNREGISTERED (challenge_cancelled) -> remove token ${maskToken(bad.token)} for user=$toUserId")
                        safelyRemoveInvalidFcmToken(toUserId, bad.token)
                    }
            }.onFailure { e ->
                Timber.tag("SocialRepository").w("Ошибка отправки challenge_cancelled batch: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.tag("SocialRepository").w(e, "Ошибка отправки challenge_cancelled")
        }
    }

    override suspend fun sendPushNotification(
        fcmToken: String, 
        title: String, 
        body: String, 
        data: Map<String, String>
    ) {
        try {
            Timber.tag(TAG).d("sendPushNotification: Sending to FCM, title=$title, body=$body")
            // Используем FCMApiClient для реальной отправки
            val result = fcmApiClient.sendPushNotification(fcmToken, title, body, data)
            
            result.onSuccess {
                Timber.tag(TAG).d("Push notification sent successfully: $title")
            }.onFailure { error ->
                when (error) {
                    is UnregisteredTokenException -> {
                        // Токен невалиден - удаляем его из базы
                        Timber.tag(TAG).w("FCM token is UNREGISTERED (${maskToken(fcmToken)}). Removing from database...")
                        // Находим пользователя по токену и удаляем невалидный токен
                        repoScope.launch {
                            try {
                                // Ищем пользователя с этим токеном
                                val usersQuery = firestore.collection("users")
                                    .whereEqualTo("fcmToken", fcmToken)
                                    .get().await()
                                
                                usersQuery.documents.forEach { doc ->
                                    Timber.tag(TAG).d("Found user ${doc.id} with UNREGISTERED token, removing...")
                                    safelyRemoveInvalidFcmToken(doc.id, fcmToken)
                                }
                                
                                // Также проверяем массив fcmTokens
                                val tokensQuery = firestore.collection("users")
                                    .whereArrayContains("fcmTokens", fcmToken)
                                    .get().await()
                                
                                tokensQuery.documents.forEach { doc ->
                                    Timber.tag(TAG).d("Found user ${doc.id} with UNREGISTERED token in array, removing...")
                                    safelyRemoveInvalidFcmToken(doc.id, fcmToken)
                                }
                            } catch (e: Exception) {
                                Timber.tag(TAG).w("Failed to remove UNREGISTERED token: ${e.message}")
                            }
                        }
                    }
                    else -> {
                        // Логируем другие ошибки
                        Timber.tag(TAG).e(error, "Error sending push notification: ${error.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем выполнение
            Timber.tag(TAG).e(e, "Exception while sending push notification: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "SocialRepository"
    }
}
