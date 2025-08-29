package com.habittracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальная очередь синхронизации (Outbox) для офлайн-режима и ретраев
 */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationId: String, // UUID для идемпотентности
    val entityType: String,  // e.g. "user", "friendship", "friend_request", "habit", "habit_entry"
    val entityId: String,    // бизнес-идентификатор сущности
    val opType: String,      // UPSERT | DELETE
    val payloadJson: String, // JSON полезной нагрузки для Firestore
    val updatedAt: Long,     // millis for LWW
    val retryCount: Int = 0,
    val lastError: String? = null,
    val state: String = "PENDING" // PENDING | IN_PROGRESS | DONE | FAILED
)
