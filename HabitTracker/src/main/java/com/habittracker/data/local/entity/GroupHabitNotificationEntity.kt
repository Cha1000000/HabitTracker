package com.habittracker.data.local.entity

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity для хранения уведомлений групповых привычек
 */
@Entity(
    tableName = "group_habit_notifications",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["habitId"]),
        Index(value = ["isRead"]),
        Index(value = ["createdAt"])
    ]
)
data class GroupHabitNotificationEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val habitId: String,
    val type: String, // PARTICIPANT_COMPLETED, INVITATION_ACCEPTED, etc.
    val participantId: String,
    val participantName: String,
    val habitTitle: String,
    val message: String,
    val isRead: Boolean = false,
    val readAt: Long? = null,
    val createdAt: Long,
    @StringRes val titleResId: Int? = null,
    @StringRes val descriptionResId: Int? = null
)
