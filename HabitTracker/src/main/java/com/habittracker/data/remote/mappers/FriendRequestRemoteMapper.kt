package com.habittracker.data.remote.mappers

import android.util.Log
import com.habittracker.data.remote.model.FriendRequestRemote
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

fun FriendRequestRemote.toDomain(id: String): FriendRequest? {
    val from = fromUserId
    val to = toUserId
    val st = status
    val created = createdAt
    val updated = updatedAt
    if (from == null || to == null || st == null || created == null || updated == null) {
        Log.w("SocialRepository", "Remote FR has nulls: id=$id from=$from to=$to status=$st createdAt=$created updatedAt=$updated")
        return null
    }
    
    // Парсим ISO 8601 строки
    val createdDateTime = runCatching { LocalDateTime.parse(created) }.getOrNull()
    val respondedDateTime = respondedAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    val updatedDate = runCatching { LocalDate.parse(updated) }.getOrNull()
    
    if (createdDateTime == null || updatedDate == null) {
        Log.w("SocialRepository", "Remote FR parse failed: id=$id createdAt=$created updatedAt=$updated")
        return null
    }
    
    return FriendRequest(
        id = id,
        fromUserId = from,
        toUserId = to,
        status = runCatching { FriendRequestStatus.valueOf(st) }.getOrElse { FriendRequestStatus.PENDING },
        message = message,
        createdAt = createdDateTime,
        respondedAt = respondedDateTime,
        updatedAt = updatedDate
    )
}
