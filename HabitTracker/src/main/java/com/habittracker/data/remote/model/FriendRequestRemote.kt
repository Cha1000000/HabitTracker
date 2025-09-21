package com.habittracker.data.remote.model

// Firestore POJO для friend_requests
// Обязательно с no-arg для десериализации

data class FriendRequestRemote(
    val fromUserId: String? = null,
    val toUserId: String? = null,
    val status: String? = null,
    val message: String? = null,
    val createdAt: Long? = null,
    val respondedAt: Long? = null,
    val updatedAt: Long? = null
) {
    // Доп. пустой конструктор на случай требований ProGuard/Firestore
    constructor() : this(null, null, null, null, null, null, null)
}
