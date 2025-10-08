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
    private val declineHabitInvitationUseCase: DeclineHabitInvitationUseCase,
    private val groupHabitNotificationRepository: com.habittracker.domain.repository.GroupHabitNotificationRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "NotificationsViewModel"
    }
    
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    
    // Количество ожидающих приглашений (Room+Firestore синхронизация обеспечена на уровне репозитория)
    private val pendingInvitationsCount = MutableStateFlow(0)

    // Итоговый бейдж: unreadCount уже включает все уведомления (старые + сохраненные)
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
        // Загружаем уведомления ТОЛЬКО из базы данных (через Firestore синхронизацию)
        loadStoredNotifications()
        observeInvitationsCount()
        loadInvitations()
        cleanupOldNotifications() // Автоматически очищаем старые прочитанные уведомления
    }

    /**
     * Загружает уведомления из базы данных (синхронизированные через Firestore)
     */
    private fun loadStoredNotifications() {
        Log.d(TAG, "loadStoredNotifications called")
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                Log.d(TAG, "Loading notifications for user: $currentUserId")
                
                // Наблюдаем за уведомлениями из базы данных
                groupHabitNotificationRepository.getNotificationsForUser(currentUserId)
                    .collect { notifications ->
                        Log.d(TAG, "Received ${notifications.size} notifications from DB")
                        val unreadCount = notifications.count { !it.isRead }
                        
                        // Обновляем состояние
                        _uiState.update { state ->
                            state.copy(
                                storedNotifications = notifications,
                                unreadCount = unreadCount,
                                isLoading = false
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
            } catch (_: Exception) {
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
                
            } catch (_: Exception) {
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
            } catch (_: Exception) {
                _uiState.update { it.copy(errorResId = R.string.error_mark_notification) }
            }
        }
    }
    
    /**
     * Удаляет все прочитанные уведомления
     */
    fun deleteAllReadNotifications() {
        viewModelScope.launch {
            try {
                // Удаляем все прочитанные уведомления из базы
                val readNotifications = _uiState.value.storedNotifications.filter { it.isRead }
                readNotifications.forEach { notification ->
                    groupHabitNotificationRepository.deleteNotification(notification.id)
                }
                
                // Обновляем состояние
                _uiState.update { state ->
                    state.copy(
                        storedNotifications = state.storedNotifications.filter { !it.isRead },
                        successResId = R.string.success_all_read_deleted
                    )
                }
                
                Log.d(TAG, "All read notifications deleted: ${readNotifications.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting read notifications", e)
                _uiState.update { it.copy(errorResId = R.string.error_delete_notifications) }
            }
        }
    }
    
    /**
     * Отмечает все уведомления как прочитанные (включая сохраненные в базе)
     */
    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                // Отмечаем старые уведомления (из GroupHabitNotificationUseCase)
                val result = groupHabitNotificationUseCase.markAllNotificationsAsRead(currentUserId)
                result.onSuccess {
                    // Обновляем локальное состояние старых уведомлений
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
                
                // Отмечаем все сохраненные уведомления в базе данных
                val storedToUpdate = _uiState.value.storedNotifications.filter { !it.isRead }
                storedToUpdate.forEach { notification ->
                    groupHabitNotificationRepository.markAsRead(notification.id)
                }
                
                // Сразу обновляем локальное состояние для UI
                _uiState.update { state ->
                    val updatedStoredNotifications = state.storedNotifications.map { notification ->
                        notification.copy(isRead = true)
                    }
                    state.copy(
                        storedNotifications = updatedStoredNotifications,
                        unreadCount = 0
                    )
                }
                
                Log.d(TAG, "All notifications marked as read")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking all notifications as read", e)
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
            } catch (_: Exception) {
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
            } catch (_: Exception) {
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
        // Уведомления автоматически обновляются через Firestore listener
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
    
    /**
     * Отмечает уведомление как прочитанное
     */
    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                groupHabitNotificationRepository.markAsRead(notificationId)
                Log.d(TAG, "Notification marked as read: $notificationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notification as read", e)
            }
        }
    }
    
    /**
     * Удаляет уведомление
     */
    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            try {
                groupHabitNotificationRepository.deleteNotification(notificationId)
                Log.d(TAG, "Notification deleted: $notificationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting notification", e)
                _uiState.update { it.copy(errorResId = R.string.error_delete_notification) }
            }
        }
    }
    
    /**
     * Очищает старые прочитанные уведомления (старше 1 дня)
     */
    fun cleanupOldNotifications() {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId() ?: return@launch
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                groupHabitNotificationRepository.deleteReadNotificationsOlderThan(currentUserId, oneDayAgo)
                Log.d(TAG, "Old notifications cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old notifications", e)
            }
        }
    }
}

