package com.habittracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.habit.AcceptHabitInvitationUseCase
import com.habittracker.domain.usecase.habit.DeclineHabitInvitationUseCase
import com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase
import com.habittracker.domain.usecase.notification.GroupHabitNotification
import com.habittracker.domain.usecase.notification.GroupHabitNotificationUseCase
import com.habittracker.presentation.state.NotificationsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для экрана уведомлений групповых привычек
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val groupHabitNotificationUseCase: GroupHabitNotificationUseCase,
    private val authRepository: AuthRepository,
    private val getHabitInvitationsUseCase: GetHabitInvitationsUseCase,
    private val acceptHabitInvitationUseCase: AcceptHabitInvitationUseCase,
    private val declineHabitInvitationUseCase: DeclineHabitInvitationUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "NotificationsViewModel"
    }
    
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    
    // Количество ожидающих приглашений (Room+Firestore синхронизация обеспечена на уровне репозитория)
    private val pendingInvitationsCount = MutableStateFlow(0)

    // Итоговый бейдж: непрочитанные системные уведомления + приглашения к привычкам
    val badgeCount: StateFlow<Int> = combine(
        _uiState.map { it.unreadCount },
        pendingInvitationsCount
    ) { unread, invites -> unread + invites }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    init {
        loadNotifications()
        observeInvitationsCount()
        loadInvitations()
    }
    
    /**
     * Загружает уведомления пользователя
     */
    private fun loadNotifications() {
        Log.d(TAG, "loadNotifications called")
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                Log.d(TAG, "Loading notifications for user: $currentUserId")
                
                groupHabitNotificationUseCase.getGroupHabitNotifications(currentUserId)
                    .collect { notifications ->
                        Log.d(TAG, "Received ${notifications.size} notifications")
                        _uiState.update { 
                            it.copy(
                                notifications = notifications,
                                unreadCount = notifications.count { !it.isRead },
                                isLoading = false,
                                error = null
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading notifications", e)
                _uiState.update { it.copy(isLoading = false, errorResId = R.string.error_loading_notifications) }
            }
        }
    }

    /**
     * Наблюдает количество ожидающих приглашений к привычкам для бейджа
     */
    private fun observeInvitationsCount() {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                    ?: return@launch
                getHabitInvitationsUseCase.getPendingInvitationsCount(currentUserId)
                    .collect { count -> pendingInvitationsCount.value = count }
            } catch (e: Exception) {
                Log.d("NotificationsVM", "observeInvitationsCount failed (non-fatal): ${e.message}")
            }
        }
    }

    /**
     * Загружает поток входящих приглашений пользователя
     */
    private fun loadInvitations() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isInvitesLoading = true, invitesError = null) }
                val currentUserId = authRepository.getCurrentUserId() ?: return@launch
                getHabitInvitationsUseCase.getIncomingInvitations(currentUserId)
                    .collect { invitations ->
                        // Показываем только PENDING приглашения в списке
                        val pending = invitations.filter { it.status == HabitInvitationStatus.PENDING }
                        _uiState.update {
                            it.copy(
                                invitations = pending,
                                isInvitesLoading = false,
                                invitesError = null
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(isInvitesLoading = false, invitesErrorResId = R.string.error_loading_invitations) }
            }
        }
    }
    
    /**
     * Обрабатывает клик по уведомлению
     */
    fun onNotificationClick(notification: GroupHabitNotification) {
        viewModelScope.launch {
            try {
                // Отмечаем уведомление как прочитанное при клике
                if (!notification.isRead) {
                    markAsRead(notification.id)
                }
                
                // TODO: Добавить навигацию к соответствующей привычке
                // Например, открыть экран детали привычки или экран прогресса группы
                
            } catch (e: Exception) {
                _uiState.update { it.copy(errorResId = R.string.error_processing_notification) }
            }
        }
    }
    
    /**
     * Отмечает уведомление как прочитанное
     */
    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                val result = groupHabitNotificationUseCase.markNotificationAsRead(notificationId)
                result.onSuccess {
                    // Обновляем локальное состояние
                    _uiState.update { state ->
                        val updatedNotifications = state.notifications.map { notification ->
                            if (notification.id == notificationId) {
                                notification.copy(isRead = true)
                            } else {
                                notification
                            }
                        }
                        state.copy(
                            notifications = updatedNotifications,
                            unreadCount = updatedNotifications.count { !it.isRead }
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(errorResId = R.string.error_mark_notification) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorResId = R.string.error_mark_notification) }
            }
        }
    }
    
    /**
     * Отмечает все уведомления как прочитанные
     */
    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                val result = groupHabitNotificationUseCase.markAllNotificationsAsRead(currentUserId)
                result.onSuccess {
                    // Обновляем локальное состояние
                    _uiState.update { state ->
                        val updatedNotifications = state.notifications.map { notification ->
                            notification.copy(isRead = true)
                        }
                        state.copy(
                            notifications = updatedNotifications,
                            unreadCount = 0,
                            successResId = R.string.success_all_marked_read
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(errorResId = R.string.error_mark_all_notifications) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorResId = R.string.error_mark_all_notifications) }
            }
        }
    }
    
    /** Принять приглашение */
    fun acceptInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                // Добавляем в прогресс
                _uiState.update { it.copy(processingInvitationIds = it.processingInvitationIds + invitationId) }
                val result = acceptHabitInvitationUseCase(invitationId, userId)
                result.onSuccess {
                    _uiState.update { it.copy(successResId = R.string.success_invitation_accepted) }
                }.onFailure { e ->
                    _uiState.update { it.copy(invitesErrorResId = R.string.error_accept_invitation) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(invitesErrorResId = R.string.error_accept_invitation) }
            } finally {
                _uiState.update { it.copy(processingInvitationIds = it.processingInvitationIds - invitationId) }
            }
        }
    }

    /** Отклонить приглашение */
    fun declineInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                _uiState.update { it.copy(processingInvitationIds = it.processingInvitationIds + invitationId) }
                val result = declineHabitInvitationUseCase(invitationId, userId)
                result.onSuccess {
                    _uiState.update { it.copy(successResId = R.string.success_invitation_declined) }
                }.onFailure { e ->
                    _uiState.update { it.copy(invitesErrorResId = R.string.error_decline_invitation) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(invitesErrorResId = R.string.error_decline_invitation) }
            } finally {
                _uiState.update { it.copy(processingInvitationIds = it.processingInvitationIds - invitationId) }
            }
        }
    }
    
    /**
     * Обновляет уведомления (pull-to-refresh)
     */
    fun refreshNotifications() {
        Log.d(TAG, "refreshNotifications called")
        loadNotifications()
        loadInvitations()
    }
    
    /**
     * Очищает сообщение об ошибке
     */
    fun onErrorShown() {
        _uiState.update { it.copy(error = null, invitesError = null, errorResId = null, invitesErrorResId = null) }
    }
    
    /**
     * Очищает сообщение об успехе
     */
    fun onSuccessMessageShown() {
        _uiState.update { it.copy(successMessage = null, successResId = null) }
    }
}

