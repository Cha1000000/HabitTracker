package com.habittracker.domain.model

/**
 * Модель уведомления групповой привычки для отображения на экране
 */
data class GroupHabitNotificationModel(
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
    val createdAt: Long
)
