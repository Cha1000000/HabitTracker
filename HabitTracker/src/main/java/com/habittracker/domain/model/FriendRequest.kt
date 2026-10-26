package com.habittracker.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Entity(tableName = "friend_requests")
data class FriendRequest(
    @PrimaryKey
    val id: String,
    val fromUserId: String, // кто отправил запрос
    val toUserId: String, // кому отправлен запрос
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val message: String? = null, // сообщение при отправке запроса
    val createdAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val respondedAt: LocalDateTime? = null, // когда был дан ответ
    val updatedAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
)

enum class FriendRequestStatus {
    PENDING,    // ожидает ответа
    ACCEPTED,   // принят
    DECLINED,   // отклонен
    EXPIRED     // истек (например, через 30 дней)
}

// Модель для UI с информацией о пользователях
data class FriendRequestWithUsers(
    val friendRequest: FriendRequest,
    val fromUser: User,
    val toUser: User
) {
    fun getOtherUser(currentUserId: String): User? = when (currentUserId) {
        fromUser.id -> toUser
        toUser.id -> fromUser
        else -> null
    }
    
    fun isIncoming(currentUserId: String): Boolean = friendRequest.toUserId == currentUserId
    
    fun isOutgoing(currentUserId: String): Boolean = friendRequest.fromUserId == currentUserId
}

// Extension functions для работы с запросами в друзья
fun FriendRequest.isPending() = status == FriendRequestStatus.PENDING

fun FriendRequest.isAccepted() = status == FriendRequestStatus.ACCEPTED

fun FriendRequest.isDeclined() = status == FriendRequestStatus.DECLINED

fun FriendRequest.isExpired() = status == FriendRequestStatus.EXPIRED

fun FriendRequest.canBeAccepted(currentUserId: String) = 
    isPending() && toUserId == currentUserId

fun FriendRequest.canBeDeclined(currentUserId: String) = 
    isPending() && toUserId == currentUserId

fun FriendRequest.canBeCancelled(currentUserId: String) = 
    isPending() && fromUserId == currentUserId

fun FriendRequest.getStatusText() = when (status) {
    FriendRequestStatus.PENDING -> "Ожидает ответа"
    FriendRequestStatus.ACCEPTED -> "Принят"
    FriendRequestStatus.DECLINED -> "Отклонен"
    FriendRequestStatus.EXPIRED -> "Истек"
}

fun FriendRequest.getTimeText(): String {
    val timestamp = respondedAt ?: createdAt
    val now = Clock.System.now()
    val timestampInstant = timestamp.toInstant(TimeZone.currentSystemDefault())
    val duration = now - timestampInstant
    val diffMinutes = duration.inWholeMinutes
    
    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "${diffMinutes}м назад"
        diffMinutes < 1440 -> "${diffMinutes / 60}ч назад" 
        else -> "${diffMinutes / 1440}д назад"
    }
}

fun FriendRequest.getCreatedTimeText(): String {
    val now = Clock.System.now()
    val createdInstant = createdAt.toInstant(TimeZone.currentSystemDefault())
    val duration = now - createdInstant
    val diffMinutes = duration.inWholeMinutes
    
    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "${diffMinutes}м назад"
        diffMinutes < 1440 -> "${diffMinutes / 60}ч назад"
        else -> "${diffMinutes / 1440}д назад"
    }
}

// Функции для создания и обновления запросов
fun createFriendRequest(
    fromUserId: String,
    toUserId: String,
    message: String? = null
) = FriendRequest(
    id = java.util.UUID.randomUUID().toString(),
    fromUserId = fromUserId,
    toUserId = toUserId,
    message = message
)

fun FriendRequest.accept() = copy(
    status = FriendRequestStatus.ACCEPTED,
    respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
)

fun FriendRequest.decline() = copy(
    status = FriendRequestStatus.DECLINED,
    respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
)

fun FriendRequest.expire() = copy(
    status = FriendRequestStatus.EXPIRED,
    respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
)
