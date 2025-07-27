package com.habittracker.domain.model

import androidx.room.Entity
import kotlinx.datetime.Instant

@Entity(
    tableName = "friendships",
    primaryKeys = ["userId1", "userId2"]
)
data class Friendship(
    val userId1: String, // всегда меньший ID (для избежания дублирования)
    val userId2: String, // всегда больший ID
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val requestedBy: String, // кто отправил запрос
    val requestedAt: Instant,
    val acceptedAt: Instant? = null,
    val blockedBy: String? = null, // кто заблокировал (если заблокировано)
    val blockedAt: Instant? = null,
    val updatedAt: Instant
)

enum class FriendshipStatus {
    PENDING,    // ожидает подтверждения
    ACCEPTED,   // принята
    BLOCKED,    // заблокирована
    DECLINED    // отклонена
}

// Модель для UI с информацией о пользователях
data class FriendshipWithUsers(
    val friendship: Friendship,
    val user1: User,
    val user2: User
) {
    fun getOtherUser(currentUserId: String): User? = when (currentUserId) {
        user1.id -> user2
        user2.id -> user1
        else -> null
    }
    
    fun getCurrentUserRole(currentUserId: String): FriendshipRole = when {
        friendship.requestedBy == currentUserId -> FriendshipRole.REQUESTER
        friendship.requestedBy != currentUserId -> FriendshipRole.RECEIVER
        else -> FriendshipRole.UNKNOWN
    }
}

enum class FriendshipRole {
    REQUESTER,  // отправил запрос
    RECEIVER,   // получил запрос
    UNKNOWN
}

// Статистика дружбы
data class FriendshipStats(
    val userId1: String,
    val userId2: String,
    val sharedHabits: Int,
    val completedChallenges: Int,
    val activeChallenges: Int,
    val friendshipDurationDays: Int,
    val mutualFriends: Int
)

// Extension functions для работы с дружбой
fun Friendship.isAccepted(): Boolean = status == FriendshipStatus.ACCEPTED

fun Friendship.isPending(): Boolean = status == FriendshipStatus.PENDING

fun Friendship.isBlocked(): Boolean = status == FriendshipStatus.BLOCKED

fun Friendship.isDeclined(): Boolean = status == FriendshipStatus.DECLINED

fun Friendship.getOtherUserId(currentUserId: String): String? = when (currentUserId) {
    userId1 -> userId2
    userId2 -> userId1
    else -> null
}

fun Friendship.canUserAccept(currentUserId: String): Boolean {
    return status == FriendshipStatus.PENDING && 
           requestedBy != currentUserId &&
           (currentUserId == userId1 || currentUserId == userId2)
}

fun Friendship.canUserDecline(currentUserId: String): Boolean {
    return status == FriendshipStatus.PENDING && 
           requestedBy != currentUserId &&
           (currentUserId == userId1 || currentUserId == userId2)
}

fun Friendship.canUserBlock(currentUserId: String): Boolean {
    return (currentUserId == userId1 || currentUserId == userId2) &&
           status != FriendshipStatus.BLOCKED
}

fun Friendship.canUserUnblock(currentUserId: String): Boolean {
    return status == FriendshipStatus.BLOCKED && 
           blockedBy == currentUserId
}

fun Friendship.isUserRequester(currentUserId: String): Boolean = requestedBy == currentUserId

fun Friendship.isUserReceiver(currentUserId: String): Boolean = 
    requestedBy != currentUserId && (currentUserId == userId1 || currentUserId == userId2)

fun Friendship.getFriendshipDurationDays(): Int {
    return if (acceptedAt != null) {
        val now = kotlinx.datetime.Clock.System.now()
        val duration = now - acceptedAt
        duration.inWholeDays.toInt()
    } else 0
}

fun Friendship.getStatusText(): String = when (status) {
    FriendshipStatus.PENDING -> "Ожидает подтверждения"
    FriendshipStatus.ACCEPTED -> "Друзья"
    FriendshipStatus.BLOCKED -> "Заблокировано"
    FriendshipStatus.DECLINED -> "Отклонено"
}

fun Friendship.getRequestTimeText(): String {
    val now = kotlinx.datetime.Clock.System.now()
    val duration = now - requestedAt
    val days = duration.inWholeDays
    val hours = duration.inWholeHours
    val minutes = duration.inWholeMinutes
    
    return when {
        days > 0 -> if (days == 1L) "1 день назад" else "$days дней назад"
        hours > 0 -> if (hours == 1L) "1 час назад" else "$hours часов назад"
        minutes > 0 -> if (minutes == 1L) "1 минуту назад" else "$minutes минут назад"
        else -> "Только что"
    }
}

fun Friendship.getAcceptedTimeText(): String {
    return if (acceptedAt != null) {
        val now = kotlinx.datetime.Clock.System.now()
        val duration = now - acceptedAt
        val days = duration.inWholeDays
        
        when {
            days == 0L -> "Сегодня"
            days == 1L -> "Вчера"
            days < 7 -> "$days дней назад"
            days < 30 -> "${days / 7} недель назад"
            days < 365 -> "${days / 30} месяцев назад"
            else -> "${days / 365} лет назад"
        }
    } else "Не принято"
}

// Функции для создания дружбы (обеспечивают правильный порядок ID)
fun createFriendship(
    userId1: String,
    userId2: String,
    requestedBy: String,
    requestedAt: Instant = kotlinx.datetime.Clock.System.now()
): Friendship {
    val (smallerId, largerId) = if (userId1 < userId2) userId1 to userId2 else userId2 to userId1
    
    return Friendship(
        userId1 = smallerId,
        userId2 = largerId,
        status = FriendshipStatus.PENDING,
        requestedBy = requestedBy,
        requestedAt = requestedAt,
        updatedAt = requestedAt
    )
}

fun Friendship.accept(acceptedAt: Instant = kotlinx.datetime.Clock.System.now()): Friendship {
    return copy(
        status = FriendshipStatus.ACCEPTED,
        acceptedAt = acceptedAt,
        updatedAt = acceptedAt
    )
}

fun Friendship.decline(updatedAt: Instant = kotlinx.datetime.Clock.System.now()): Friendship {
    return copy(
        status = FriendshipStatus.DECLINED,
        updatedAt = updatedAt
    )
}

fun Friendship.block(
    blockedBy: String,
    blockedAt: Instant = kotlinx.datetime.Clock.System.now()
): Friendship {
    return copy(
        status = FriendshipStatus.BLOCKED,
        blockedBy = blockedBy,
        blockedAt = blockedAt,
        updatedAt = blockedAt
    )
}

fun Friendship.unblock(updatedAt: Instant = kotlinx.datetime.Clock.System.now()): Friendship {
    return copy(
        status = FriendshipStatus.ACCEPTED,
        blockedBy = null,
        blockedAt = null,
        updatedAt = updatedAt
    )
}
