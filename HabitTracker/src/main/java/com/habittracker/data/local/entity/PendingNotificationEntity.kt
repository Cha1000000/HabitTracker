package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_notifications")
data class PendingNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipientUserId: String,
    val type: String,
    val title: String,
    val body: String,
    val dataJson: String, // serialized Map<String, String>
    val receivedAt: Long,
    val ttlSeconds: Long? = 3600, // по умолчанию 1 час
    val dedupKey: String? = null
)
