package com.habittracker.domain.util

/**
 * Общие сериализаторы для payload'ов синхронизации (Outbox)
 */
import com.google.gson.GsonBuilder
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.User
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object SyncPayloads {
    private val gson = GsonBuilder().serializeNulls().create()

    fun habitParticipantSettingsToJson(settings: HabitParticipantSettings): String = gson.toJson(
        buildMap {
            put("reminderEnabled", settings.reminderEnabled)
            if (settings.reminderEnabled && settings.reminderTime != null) {
                put("reminderTime", mapOf("hour" to settings.reminderTime.hour, "minute" to settings.reminderTime.minute))
            }
            if (settings.reminderDays.isNotEmpty()) {
                put("reminderDays", settings.reminderDays.map { it.value })
            }
            put("notificationsMuted", settings.notificationsMuted)
            settings.timeZone?.let { put("timeZone", it) }
            put("updatedAt", settings.updatedAt)
        }
    )

    fun habitToJson(habit: Habit): String = gson.toJson(
        buildMap {
            put("id", habit.id)
            put("title", habit.title)
            put("description", habit.description)
            put("icon", habit.icon)
            put("color", habit.color)
            put("type", habit.type.name)
            put("frequency", habit.frequency.name)
            
            // Reminder logic matching FirestoreRepositoryImpl
            val hasReminder = habit.reminderTime != null
            put("reminderEnabled", hasReminder)
            if (hasReminder) {
                val rt = habit.reminderTime
                put("reminderTime", mapOf("hour" to rt.hour, "minute" to rt.minute))
            }
            if (habit.reminderDays.isNotEmpty()) {
                put("reminderDays", habit.reminderDays.map { it.value })
            }
            
            put("targetValue", habit.targetValue)
            put("unit", habit.unit)
            put("createdBy", habit.createdBy)
            put("participants", habit.participants)
            put("maxParticipants", habit.maxParticipants)
            put("inviteCode", habit.inviteCode)
            put("public", habit.isPublic)
            put("isActive", habit.isActive)
            put("createdAt", habit.createdAt.toString())
            put("updatedAt", habit.updatedAt.toString())
            put("updatedAtMillis", habit.updatedAtMillis)
            habit.deletedAtMillis?.let { put("deletedAtMillis", it) }
            habit.startDate?.let { put("startDate", it.toString()) }
            habit.endDate?.let { put("endDate", it.toString()) }
            put("timeOfDay", habit.timeOfDay.name)
        }
    )

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
            "isRestDay" to entry.isRestDay,
            "createdAt" to entry.createdAt.toString(),
            "updatedAt" to entry.updatedAt.toString()
        )
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

    // Habit invitation payload
    fun habitInvitationToJson(inv: HabitInvitation, updatedAt: Long): String = gson.toJson(
        mapOf(
            "habitId" to inv.habitId,
            "habitTitle" to inv.habitTitle,
            "fromUserId" to inv.fromUserId,
            "fromUserName" to inv.fromUserName,
            "toUserId" to inv.toUserId,
            "message" to inv.message,
            "status" to inv.status.name,
            "createdAt" to inv.createdAt.toString(), // ISO 8601: "2025-10-13T15:07:00"
            "respondedAt" to inv.respondedAt?.toString(),
            "expiresAt" to inv.expiresAt?.toString(),
            "updatedAt" to Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() // ISO 8601: "2025-10-13"
        )
    )

    fun challengeToJson(challenge: Challenge, updatedAtMillis: Long): String = gson.toJson(
        mapOf(
            "id" to challenge.id,
            "habitId" to challenge.habitId,
            "title" to challenge.title,
            "description" to challenge.description,
            "createdBy" to challenge.createdBy,
            "participants" to challenge.participants,
            "acceptedParticipants" to challenge.acceptedParticipants,
            "startDate" to challenge.startDate.toString(),
            "endDate" to challenge.endDate.toString(),
            "rules" to mapOf(
                "type" to challenge.rules.type.name,
                "targetValue" to challenge.rules.targetValue,
                "allowMissedDays" to challenge.rules.allowMissedDays,
                "requireConsecutive" to challenge.rules.requireConsecutive,
                "allowBackfillCompletions" to challenge.rules.allowBackfillCompletions
            ),
            "reward" to challenge.reward,
            "status" to challenge.status.name,
            "winnerId" to challenge.winnerId,
            "createdAt" to challenge.createdAt.toString(),
            "updatedAt" to challenge.updatedAt.toString(),
            "updatedAtMillis" to updatedAtMillis
        )
    )

    fun userToJson(user: User, updatedAtMillis: Long): String = gson.toJson(
        buildMap {
            put("id", user.id)
            put("name", user.name)
            put("email", user.email)
            put("level", user.level)
            put("points", user.points)
            put("streak", user.streak)
            put("longestStreak", user.longestStreak)
            put("friendsCount", user.friendsCount)
            put("createdAt", user.createdAt)
            put("timezone", user.timezone)
            put("updatedAt", updatedAtMillis)
            user.avatarUrl?.let { put("avatarUrl", it) }
            user.avatarThumbnailUrl?.let { put("avatarThumbnailUrl", it) }
            user.fcmToken?.let { put("fcmToken", it) }
        }
    )

    fun userAchievementToJson(ua: com.habittracker.domain.model.UserAchievement, updatedAtMillis: Long): String = gson.toJson(
        mapOf(
            "userId" to ua.userId,
            "achievementId" to ua.achievementId,
            "progress" to ua.progress,
            "isUnlocked" to ua.isUnlocked,
            "unlockedAtMillis" to ua.unlockedAt?.toEpochMilliseconds(),
            "createdAtMillis" to ua.createdAt.toEpochMilliseconds(),
            "tier" to ua.tier,
            "updatedAtMillis" to updatedAtMillis
        )
    )

    fun groupHabitNotificationToJson(notification: com.habittracker.domain.model.GroupHabitNotificationModel): String = gson.toJson(
        mapOf(
            "id" to notification.id,
            "userId" to notification.userId,
            "habitId" to notification.habitId,
            "type" to notification.type,
            "participantId" to notification.participantId,
            "participantName" to notification.participantName,
            "habitTitle" to notification.habitTitle,
            "message" to notification.message,
            "isRead" to notification.isRead,
            "readAt" to notification.readAt,
            "createdAt" to notification.createdAt,
            "updatedAt" to System.currentTimeMillis()
        )
    )
}
