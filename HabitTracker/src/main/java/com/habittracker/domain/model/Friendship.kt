package com.habittracker.domain.model

import androidx.room.Entity
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Entity(
    tableName = "friendships",
    primaryKeys = ["userId1", "userId2"]
)
data class Friendship(
    val userId1: String, // всегда меньший ID (для избежания дублирования)
    val userId2: String, // всегда больший ID
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val requestedBy: String, // кто отправил запрос
    val requestedAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val acceptedAt: LocalDateTime? = null,
    val blockedBy: String? = null, // кто заблокировал (если заблокировано)
    val blockedAt: LocalDateTime? = null,
    val updatedAt: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
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
        val now = Clock.System.now()
        val acceptedInstant = acceptedAt.toInstant(TimeZone.currentSystemDefault())
        val duration = now - acceptedInstant
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
    val now = Clock.System.now()
    val requestedInstant = requestedAt.toInstant(TimeZone.currentSystemDefault())
    val duration = now - requestedInstant
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
    val acceptedTime = acceptedAt ?: requestedAt
    val now = Clock.System.now()
    val acceptedInstant = acceptedTime.toInstant(TimeZone.currentSystemDefault())
    val duration = now - acceptedInstant
    
    return when {
        duration.inWholeDays > 0 -> "${duration.inWholeDays} дн. назад"
        duration.inWholeHours > 0 -> "${duration.inWholeHours} ч. назад"
        duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes} мин. назад"
        else -> "Только что"
    }
}

// Функции для создания дружбы (обеспечивают правильный порядок ID)
fun createFriendship(
    userId1: String,
    userId2: String,
    requestedBy: String
): Friendship {
    val (smallerId, largerId) = if (userId1 < userId2) userId1 to userId2 else userId2 to userId1
    
    return Friendship(
        userId1 = smallerId,
        userId2 = largerId,
        status = FriendshipStatus.PENDING,
        requestedBy = requestedBy
    )
}

fun Friendship.accept(): Friendship {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return copy(
        status = FriendshipStatus.ACCEPTED,
        acceptedAt = now,
        updatedAt = now.date
    )
}

fun Friendship.decline(): Friendship {
    return copy(
        status = FriendshipStatus.DECLINED,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
}

fun Friendship.block(
    blockedBy: String
): Friendship {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return copy(
        status = FriendshipStatus.BLOCKED,
        blockedBy = blockedBy,
        blockedAt = now,
        updatedAt = now.date
    )
}

fun Friendship.unblock(): Friendship {
    return copy(
        status = FriendshipStatus.ACCEPTED,
        blockedBy = null,
        blockedAt = null,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
}
