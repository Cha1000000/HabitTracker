package com.habittracker.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "friend_requests")
data class FriendRequest(
    @PrimaryKey
    val id: String,
    val fromUserId: String, // кто отправил запрос
    val toUserId: String, // кому отправлен запрос
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val message: String? = null, // сообщение при отправке запроса
    val createdAt: Instant,
    val respondedAt: Instant? = null, // когда был дан ответ
    val updatedAt: Instant
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
fun FriendRequest.isPending(): Boolean = status == FriendRequestStatus.PENDING

fun FriendRequest.isAccepted(): Boolean = status == FriendRequestStatus.ACCEPTED

fun FriendRequest.isDeclined(): Boolean = status == FriendRequestStatus.DECLINED

fun FriendRequest.isExpired(): Boolean = status == FriendRequestStatus.EXPIRED

fun FriendRequest.canBeAccepted(currentUserId: String): Boolean = 
    isPending() && toUserId == currentUserId

fun FriendRequest.canBeDeclined(currentUserId: String): Boolean = 
    isPending() && toUserId == currentUserId

fun FriendRequest.canBeCancelled(currentUserId: String): Boolean = 
    isPending() && fromUserId == currentUserId

fun FriendRequest.getStatusText(): String = when (status) {
    FriendRequestStatus.PENDING -> "Ожидает ответа"
    FriendRequestStatus.ACCEPTED -> "Принят"
    FriendRequestStatus.DECLINED -> "Отклонен"
    FriendRequestStatus.EXPIRED -> "Истек"
}

fun FriendRequest.getTimeText(): String {
    val now = kotlinx.datetime.Clock.System.now()
    val timeDiff = now - createdAt

    return when {
        timeDiff.inWholeMinutes < 1 -> "только что"
        timeDiff.inWholeMinutes < 60 -> "${timeDiff.inWholeMinutes} мин назад"
        timeDiff.inWholeHours < 24 -> "${timeDiff.inWholeHours} ч назад"
        timeDiff.inWholeDays < 7 -> "${timeDiff.inWholeDays} дн назад"
        else -> "давно"
    }
}

fun FriendRequest.getCreatedTimeText(): String {
    val now = kotlinx.datetime.Clock.System.now()
    val duration = now - createdAt
    
    return when {
        duration.inWholeDays > 0 -> "${duration.inWholeDays} дн. назад"
        duration.inWholeHours > 0 -> "${duration.inWholeHours} ч. назад"
        duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes} мин. назад"
        else -> "Только что"
    }
}

// Функции для создания и обновления запросов
fun createFriendRequest(
    fromUserId: String,
    toUserId: String,
    message: String? = null,
    createdAt: Instant = kotlinx.datetime.Clock.System.now()
): FriendRequest = FriendRequest(
    id = "${fromUserId}_${toUserId}_${createdAt.epochSeconds}",
    fromUserId = fromUserId,
    toUserId = toUserId,
    message = message,
    createdAt = createdAt,
    updatedAt = createdAt
)

fun FriendRequest.accept(
    respondedAt: Instant = kotlinx.datetime.Clock.System.now()
): FriendRequest = copy(
    status = FriendRequestStatus.ACCEPTED,
    respondedAt = respondedAt,
    updatedAt = respondedAt
)

fun FriendRequest.decline(
    respondedAt: Instant = kotlinx.datetime.Clock.System.now()
): FriendRequest = copy(
    status = FriendRequestStatus.DECLINED,
    respondedAt = respondedAt,
    updatedAt = respondedAt
)

fun FriendRequest.expire(
    expiredAt: Instant = kotlinx.datetime.Clock.System.now()
): FriendRequest = copy(
    status = FriendRequestStatus.EXPIRED,
    respondedAt = expiredAt,
    updatedAt = expiredAt
)
