package com.habittracker.data.remote.model

// Firestore POJO для friend_requests
// Обязательно с no-arg для десериализации
// Даты хранятся как String в ISO 8601 формате для человекочитаемости

data class FriendRequestRemote(
    val fromUserId: String? = null,
    val toUserId: String? = null,
    val status: String? = null,
    val message: String? = null,
    val createdAt: String? = null,      // ISO 8601: "2025-10-13T15:07:00"
    val respondedAt: String? = null,    // ISO 8601: "2025-10-13T15:07:00"
    val updatedAt: String? = null       // ISO 8601: "2025-10-13"
) {
    // Доп. пустой конструктор на случай требований ProGuard/Firestore
    constructor() : this(null, null, null, null, null, null, null)
}
