package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * Entity для хранения статуса прочитанных уведомлений
 */
@Entity(
    tableName = "notification_read_status",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["habitId"]),
        Index(value = ["isRead"])
    ]
)
data class NotificationReadStatusEntity(
    @PrimaryKey
    val notificationId: String,
    val userId: String,
    val habitId: String,
    val isRead: Boolean,
    val readAt: Instant? = null,
    val createdAt: Instant
)
