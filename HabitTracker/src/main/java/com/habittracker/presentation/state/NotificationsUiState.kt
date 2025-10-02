package com.habittracker.presentation.state

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.usecase.notification.GroupHabitNotification

/**
 * UI состояние экрана уведомлений
 */
data class NotificationsUiState(
    val notifications: List<GroupHabitNotification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val errorResId: Int? = null,
    val invitesErrorResId: Int? = null,
    val successResId: Int? = null,
    // Invitations section
    val invitations: List<HabitInvitation> = emptyList(),
    val isInvitesLoading: Boolean = false,
    val invitesError: String? = null,
    val processingInvitationIds: Set<String> = emptySet()
)