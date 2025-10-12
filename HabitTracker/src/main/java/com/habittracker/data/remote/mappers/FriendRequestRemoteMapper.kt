package com.habittracker.data.remote.mappers

import android.util.Log
import com.habittracker.data.remote.model.FriendRequestRemote
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    return FriendRequest(
        id = id,
        fromUserId = from,
        toUserId = to,
        status = runCatching { FriendRequestStatus.valueOf(st) }.getOrElse { FriendRequestStatus.PENDING },
        message = message,
        createdAt = Instant.fromEpochMilliseconds(created).toLocalDateTime(TimeZone.currentSystemDefault()),
        respondedAt = respondedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) },
        updatedAt = Instant.fromEpochMilliseconds(updated).toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
}
