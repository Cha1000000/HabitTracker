package com.habittracker.domain.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Общие сериализаторы для payload'ов синхронизации (Outbox)
 */
object SyncPayloads {
    private val gson = GsonBuilder().serializeNulls().create()

    // Формируем компактный JSON без сложных сериализаторов LocalDate
    fun habitEntryToJson(entry: HabitEntry): String = gson.toJson(
        mapOf(
            "habitId" to entry.habitId,
            "userId" to entry.userId,
            "date" to entry.date.toString(),
            "completed" to entry.completed,
            "completedAt" to entry.completedAt?.toString(),
            "value" to entry.value,
            "note" to entry.note,
            "createdAt" to entry.createdAt.toString(),
            "updatedAt" to entry.updatedAt.toString()
        )
    )

    // Частичное обновление Habit (используется SetOptions.merge в SyncManager)
    fun habitPartialUpdateToJson(
        participants: List<String>? = null,
        type: HabitType? = null,
        createdBy: String? = null,
        isActive: Boolean? = null,
        updatedAt: LocalDate
    ): String = gson.toJson(
        buildMap<String, Any?> {
            put("updatedAt", updatedAt.toString())
            if (participants != null) put("participants", participants)
            if (type != null) put("type", type.name)
            if (createdBy != null) put("createdBy", createdBy)
            if (isActive != null) {
                // isActive-only стандарт
                put("isActive", isActive)
            }
        }
    )

    // Friend request payload
    fun friendRequestToJson(fr: FriendRequest): String = gson.toJson(
        mapOf(
            "fromUserId" to fr.fromUserId,
            "toUserId" to fr.toUserId,
            "status" to fr.status.name,
            "message" to fr.message,
            "createdAt" to fr.createdAt.toString(), // ISO 8601: "2025-10-13T15:07:00"
            "respondedAt" to fr.respondedAt?.toString(),
            "updatedAt" to fr.updatedAt.toString() // ISO 8601: "2025-10-13"
        )
    )

    // Friendship payload (userId1/userId2 в отсортированном порядке для консистентности с docId)
    fun friendshipToJson(fs: Friendship): String {
        val u1 = minOf(fs.userId1, fs.userId2)
        val u2 = maxOf(fs.userId1, fs.userId2)
        return gson.toJson(
            mapOf(
                "userId1" to u1,
                "userId2" to u2,
                "status" to fs.status.name,
                "requestedBy" to fs.requestedBy,
                "requestedAt" to fs.requestedAt.toString(), // ISO 8601: "2025-10-13T15:07:00"
                "acceptedAt" to fs.acceptedAt?.toString(),
                "blockedBy" to fs.blockedBy,
                "blockedAt" to fs.blockedAt?.toString(),
                "updatedAt" to fs.updatedAt.toString() // ISO 8601: "2025-10-13"
            )
        )
    }

    // Habit invitation payload (updatedAt передается явно для соответствия текущей логике вызовов)
    fun habitInvitationToJson(inv: HabitInvitation, updatedAt: Long): String = gson.toJson(
        mapOf(
            "habitId" to inv.habitId,
            "habitTitle" to inv.habitTitle,
            "fromUserId" to inv.fromUserId,
            "fromUserName" to inv.fromUserName,
            "toUserId" to inv.toUserId,
            "message" to inv.message,
            "status" to inv.status.name,
            "createdAt" to inv.createdAt,
            "respondedAt" to inv.respondedAt,
            "expiresAt" to inv.expiresAt,
            "updatedAt" to updatedAt
        )
    )
}
