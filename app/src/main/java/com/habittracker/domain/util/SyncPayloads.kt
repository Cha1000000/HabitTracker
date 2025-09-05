package com.habittracker.domain.util

import com.google.gson.GsonBuilder
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitType

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
            "completedAt" to entry.completedAt,
            "value" to entry.value,
            "note" to entry.note,
            "createdAt" to entry.createdAt,
            "updatedAt" to entry.updatedAt
        )
    )

    // Частичное обновление Habit (используется SetOptions.merge в SyncManager)
    fun habitPartialUpdateToJson(
        participants: List<String>? = null,
        type: HabitType? = null,
        createdBy: String? = null,
        isActive: Boolean? = null,
        updatedAt: Long
    ): String = gson.toJson(
        buildMap<String, Any?> {
            put("updatedAt", updatedAt)
            if (participants != null) put("participants", participants)
            if (type != null) put("type", type.name)
            if (createdBy != null) put("createdBy", createdBy)
            if (isActive != null) {
                // Пишем оба поля для обратной совместимости
                put("isActive", isActive)
                put("active", isActive)
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
            "createdAt" to fr.createdAt,
            "respondedAt" to fr.respondedAt,
            "updatedAt" to fr.updatedAt
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
                "requestedAt" to fs.requestedAt,
                "acceptedAt" to fs.acceptedAt,
                "blockedBy" to fs.blockedBy,
                "blockedAt" to fs.blockedAt,
                "updatedAt" to fs.updatedAt
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
