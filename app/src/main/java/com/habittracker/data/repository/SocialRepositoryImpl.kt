package com.habittracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.FriendRequestDao
import com.habittracker.data.local.dao.FriendshipDao
import com.habittracker.data.local.dao.HabitInvitationDao
import com.habittracker.data.local.dao.UserDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.data.service.FCMApiClient
import com.habittracker.data.firestore.UserFirestoreConverter
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
    private val habitRepository: HabitRepository
) : SocialRepository {

    override suspend fun searchUsers(query: String, currentUserId: String): List<User> {
        return try {
            android.util.Log.d("SocialRepository", "🔍 Searching users: query='$query', currentUserId='$currentUserId'")
            
            // Поиск в Firestore по имени и email
            val nameResults = firestore.collection("users")
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .limit(20)
                .get()
                .await()
            
            android.util.Log.d("SocialRepository", "📝 Name results: ${nameResults.documents.size} documents")

            val emailResults = firestore.collection("users")
                .whereGreaterThanOrEqualTo("email", query)
                .whereLessThanOrEqualTo("email", query + "\uf8ff")
                .limit(20)
                .get()
                .await()
                
            android.util.Log.d("SocialRepository", "📧 Email results: ${emailResults.documents.size} documents")

            val allResults = (nameResults.documents + emailResults.documents)
                .distinctBy { it.id }
                .mapNotNull { doc ->
                    try {
                        android.util.Log.d("SocialRepository", "🔄 Processing document: id=${doc.id}")
                        // Используем профессиональный конвертер вместо стандартной десериализации
                        val user = UserFirestoreConverter.fromFirestore(doc.data ?: emptyMap(), doc.id)
                        if (user != null) {
                            android.util.Log.d("SocialRepository", "✅ Successfully converted user: id=${user.id}, name=${user.name}, email=${user.email}")
                        } else {
                            android.util.Log.w("SocialRepository", "⚠️ UserFirestoreConverter returned null for document: id=${doc.id}")
                        }
                        user
                    } catch (e: Exception) {
                        android.util.Log.e("SocialRepository", "❌ Error mapping user document: ${e.message}")
                        null
                    }
                }
                .filter { it.id != currentUserId } // Исключаем текущего пользователя
        
            android.util.Log.d("SocialRepository", "🎯 Before filtering: ${allResults.size + (if (allResults.any { it.id == currentUserId }) 1 else 0)} users")
            android.util.Log.d("SocialRepository", "🎯 After filtering (excluding currentUserId=$currentUserId): ${allResults.size} users")
            allResults
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "💥 Firestore search failed: ${e.message}")
            
            // Fallback на локальный поиск
            val localResults = userDao.searchUsers("%$query%")
                .map { it.toDomain() }
                .filter { it.id != currentUserId }
                
            android.util.Log.d("SocialRepository", "🗄️ Fallback local results: ${localResults.size} users")
            localResults
        }
    }

    override suspend fun getUserById(userId: String): User? {
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                UserFirestoreConverter.fromFirestore(doc.data ?: emptyMap(), doc.id)
            } else {
                // Fallback на локальные данные
                userDao.getUserById(userId)?.toDomain()
            }
        } catch (e: Exception) {
            userDao.getUserById(userId)?.toDomain()
        }
    }

    override suspend fun updateUserFcmToken(userId: String, token: String) {
        try {
            firestore.collection("users").document(userId)
                .update("fcmToken", token)
                .await()
        } catch (e: Exception) {
            // Обновляем локально
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
            val friendRequest = FriendRequest(
                id = UUID.randomUUID().toString(),
                fromUserId = fromUserId,
                toUserId = toUserId,
                status = FriendRequestStatus.PENDING,
                message = message,
                createdAt = Clock.System.now(),
                respondedAt = null,
                updatedAt = Clock.System.now()
            )
            
            // Сохраняем в Firestore
            firestore.collection("friend_requests")
                .document(friendRequest.id)
                .set(friendRequest)
                .await()

            // Сохраняем локально
            friendRequestDao.insertFriendRequest(friendRequest.toEntity())

            Result.success(friendRequest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptFriendRequest(requestId: String): Result<Friendship> {
        return try {
            val friendRequest = getFriendRequestById(requestId)
                ?: return Result.failure(IllegalArgumentException("Запрос не найден"))

            val acceptedRequest = friendRequest.copy(
                status = FriendRequestStatus.ACCEPTED,
                respondedAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            val friendship = createFriendship(
                userId1 = friendRequest.fromUserId,
                userId2 = friendRequest.toUserId,
                requestedBy = friendRequest.fromUserId,
                requestedAt = friendRequest.createdAt
            ).accept()

            // Обновляем в Firestore
            firestore.collection("friend_requests")
                .document(requestId)
                .set(acceptedRequest)
                .await()

            firestore.collection("friendships")
                .document(friendship.userId1 + "_" + friendship.userId2)
                .set(friendship)
                .await()

            // Сохраняем локально
            friendRequestDao.updateFriendRequest(acceptedRequest.toEntity())
            friendshipDao.insertFriendship(friendship.toEntity())

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
                respondedAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )

            // Обновляем в Firestore
            firestore.collection("friend_requests")
                .document(requestId)
                .set(declinedRequest)
                .await()

            // Обновляем локально
            friendRequestDao.updateFriendRequest(declinedRequest.toEntity())

            Result.success(declinedRequest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> {
        return try {
            // Удаляем из Firestore
            firestore.collection("friend_requests")
                .document(requestId)
                .delete()
                .await()

            // Удаляем локально
            friendRequestDao.deleteFriendRequestById(requestId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFriendRequestById(requestId: String): FriendRequest? {
        return try {
            val doc = firestore.collection("friend_requests").document(requestId).get().await()
            if (doc.exists()) {
                doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
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
                doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            friendRequestDao.getFriendRequestBetweenUsers(fromUserId, toUserId)?.toDomain()
        }
    }

    override fun getIncomingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return friendRequestDao.getIncomingFriendRequests(userId).map { requests ->
            requests.mapNotNull { requestEntity ->
                val request = requestEntity.toDomain()
                val fromUser = getUserById(request.fromUserId)
                val toUser = getUserById(request.toUserId)
                
                if (fromUser != null && toUser != null) {
                    FriendRequestWithUsers(request, fromUser, toUser)
                } else null
            }
        }
    }

    override fun getOutgoingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> {
        return friendRequestDao.getOutgoingFriendRequests(userId).map { requests ->
            requests.mapNotNull { requestEntity ->
                val request = requestEntity.toDomain()
                val fromUser = getUserById(request.fromUserId)
                val toUser = getUserById(request.toUserId)
                
                if (fromUser != null && toUser != null) {
                    FriendRequestWithUsers(request, fromUser, toUser)
                } else null
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
                doc.toObject(Friendship::class.java)
            } else {
                friendshipDao.getFriendshipBetweenUsers(userId1, userId2)?.toDomain()
            }
        } catch (e: Exception) {
            friendshipDao.getFriendshipBetweenUsers(userId1, userId2)?.toDomain()
        }
    }

    override suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit> {
        return try {
            val orderedIds = if (currentUserId < friendId) Pair(currentUserId, friendId) else Pair(friendId, currentUserId)
            val docId = "${orderedIds.first}_${orderedIds.second}"

            // Удаляем из Firestore
            firestore.collection("friendships").document(docId).delete().await()

            // Удаляем локально
            friendshipDao.deleteFriendshipBetweenUsers(currentUserId, friendId)

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
                blockedAt = Clock.System.now()
            )

            // Обновляем в Firestore
            val orderedIds = if (currentUserId < userIdToBlock) Pair(currentUserId, userIdToBlock) else Pair(userIdToBlock, currentUserId)
            firestore.collection("friendships")
                .document("${orderedIds.first}_${orderedIds.second}")
                .set(blockedFriendship)
                .await()

            // Обновляем локально
            friendshipDao.updateFriendship(blockedFriendship.toEntity())

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

            // Обновляем в Firestore
            val orderedIds = if (currentUserId < userIdToUnblock) Pair(currentUserId, userIdToUnblock) else Pair(userIdToUnblock, currentUserId)
            firestore.collection("friendships")
                .document("${orderedIds.first}_${orderedIds.second}")
                .set(unblockedFriendship)
                .await()

            // Обновляем локально
            friendshipDao.updateFriendship(unblockedFriendship.toEntity())

            Result.success(unblockedFriendship)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFriends(userId: String): Flow<List<FriendshipWithUsers>> {
        return friendshipDao.getFriendsForUser(userId).map { friendshipEntities ->
            friendshipEntities.mapNotNull { friendshipEntity ->
                val friendship = friendshipEntity.toDomain()
                try {
                    val user1 = runBlocking { getUserById(friendship.userId1) }
                    val user2 = runBlocking { getUserById(friendship.userId2) }
                    
                    if (user1 != null && user2 != null) {
                        FriendshipWithUsers(friendship, user1, user2)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }.filter { it.friendship.status == FriendshipStatus.ACCEPTED }
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
        val thirtyDaysAgo = Clock.System.now().epochSeconds - (30 * 24 * 60 * 60)
        try {
            friendRequestDao.expireOldRequests(thirtyDaysAgo)
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
    }

    override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) {
        try {
            val user = getUserById(toUserId)
            val fcmToken = user?.fcmToken
            
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken = fcmToken,
                    title = "Новый запрос дружбы",
                    body = "$fromUserName хочет добавить вас в друзья",
                    data = mapOf(
                        "type" to "friend_request",
                        "fromUserId" to fromUserName,
                        "action" to "open_friend_requests"
                    )
                )
            }
        } catch (e: Exception) {
            // Логируем ошибку, но не падаем - уведомления не критичны
            android.util.Log.w("SocialRepository", "Ошибка отправки уведомления о запросе дружбы", e)
        }
    }

    override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) {
        try {
            val user = getUserById(toUserId)
            val fcmToken = user?.fcmToken
            
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken = fcmToken,
                    title = "Запрос дружбы принят",
                    body = "$acceptedByName принял ваш запрос дружбы",
                    data = mapOf(
                        "type" to "friend_accepted",
                        "friendUserId" to acceptedByName,
                        "action" to "open_friends"
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("SocialRepository", "Ошибка отправки уведомления о принятии дружбы", e)
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
                createdAt = Clock.System.now(),
                respondedAt = null,
                expiresAt = Clock.System.now().plus(DateTimePeriod(days = 7), TimeZone.currentSystemDefault()) // Истекает через 7 дней
            )

            // Сохраняем локально
            habitInvitationDao.insert(invitation.toEntity())

            // Сохраняем в Firestore
            firestore.collection("habit_invitations")
                .document(invitation.id)
                .set(invitation)
                .await()

            // Отправляем FCM уведомление
            sendHabitInvitationNotification(toUserId, fromUserName, habitTitle)

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
                respondedAt = Clock.System.now()
            )

            habitInvitationDao.update(acceptedInvitation.toEntity())

            // Обновляем в Firestore
            firestore.collection("habit_invitations")
                .document(invitationId)
                .set(acceptedInvitation)
                .await()

            // Добавляем пользователя к участникам привычки
            habitRepository.addParticipantToHabit(invitation.habitId, invitation.toUserId)

            // Отправляем FCM уведомление создателю
            sendHabitInvitationAcceptedNotification(invitation.fromUserId, invitation.toUserId, invitation.habitTitle)

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
                respondedAt = Clock.System.now()
            )

            habitInvitationDao.update(declinedInvitation.toEntity())

            // Обновляем в Firestore
            firestore.collection("habit_invitations")
                .document(invitationId)
                .set(declinedInvitation)
                .await()

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
                respondedAt = Clock.System.now()
            )

            habitInvitationDao.update(cancelledInvitation.toEntity())

            // Обновляем в Firestore
            firestore.collection("habit_invitations")
                .document(invitationId)
                .set(cancelledInvitation)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getHabitInvitationById(invitationId: String): HabitInvitation? {
        return try {
            val doc = firestore.collection("habit_invitations").document(invitationId).get().await()
            if (doc.exists()) {
                doc.toObject(HabitInvitation::class.java)?.copy(id = doc.id)
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
                doc.toObject(HabitInvitation::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            habitInvitationDao.getPendingInvitation(habitId, userId)?.toDomain()
        }
    }

    override fun getIncomingHabitInvitations(userId: String): Flow<List<HabitInvitation>> {
        return habitInvitationDao.getIncomingInvitations(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getOutgoingHabitInvitations(userId: String): Flow<List<HabitInvitation>> {
        return habitInvitationDao.getOutgoingInvitations(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHabitInvitations(habitId: String): Flow<List<HabitInvitation>> {
        return habitInvitationDao.getHabitInvitations(habitId).map { entities ->
            entities.map { it.toDomain() }
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
        val currentTime = Clock.System.now().epochSeconds
        try {
            habitInvitationDao.expireOldInvitations(currentTime)
            habitInvitationDao.deleteOldInvitations(currentTime - (30 * 24 * 60 * 60)) // Удаляем старые через 30 дней
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
            val toUser = getUserById(toUserId)
            val fcmToken = toUser?.fcmToken
            
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken = fcmToken,
                    title = "Приглашение к привычке",
                    body = "$fromUserName приглашает вас к участию в привычке '$habitTitle'",
                    data = mapOf(
                        "type" to "habit_invitation",
                        "habitTitle" to habitTitle,
                        "fromUserName" to fromUserName,
                        "action" to "open_habit_invitations"
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("SocialRepository", "Ошибка отправки уведомления о приглашении в привычку", e)
        }
    }

    override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String) {
        try {
            val toUser = getUserById(toUserId)
            val fcmToken = toUser?.fcmToken
            
            if (fcmToken != null) {
                fcmApiClient.sendPushNotification(
                    fcmToken = fcmToken,
                    title = "Приглашение принято",
                    body = "$acceptedByName принял ваше приглашение к участию в привычке '$habitTitle'",
                    data = mapOf(
                        "type" to "habit_invitation_accepted",
                        "habitTitle" to habitTitle,
                        "acceptedByName" to acceptedByName,
                        "action" to "open_habit_details"
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("SocialRepository", "Ошибка отправки уведомления о принятии приглашения", e)
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
