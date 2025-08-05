package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.notification.GroupHabitNotification
import com.habittracker.domain.usecase.notification.GroupHabitNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    
    /**
     * Flow для количества непрочитанных уведомлений (для badge в UI)
     */
    val unreadNotificationsCount: StateFlow<Int> = _uiState
        .map { it.unreadCount }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    init {
        loadNotifications()
    }
    
    /**
     * Загружает уведомления пользователя
     */
    private fun loadNotifications() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Пользователь не авторизован")
                
                groupHabitNotificationUseCase.getGroupHabitNotifications(currentUserId)
                    .collect { notifications ->
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
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Не удалось загрузить уведомления: ${e.message}"
                    )
                }
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
                _uiState.update { 
                    it.copy(error = "Ошибка обработки уведомления: ${e.message}")
                }
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
                    _uiState.update { 
                        it.copy(error = "Не удалось отметить уведомление: ${error.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "Ошибка отметки уведомления: ${e.message}")
                }
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
                            successMessage = "Все уведомления отмечены как прочитанные"
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { 
                        it.copy(error = "Не удалось отметить все уведомления: ${error.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "Ошибка массовой отметки: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Обновляет уведомления (pull-to-refresh)
     */
    fun refreshNotifications() {
        loadNotifications()
    }
    
    /**
     * Очищает сообщение об ошибке
     */
    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Очищает сообщение об успехе
     */
    fun onSuccessMessageShown() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

/**
 * UI состояние экрана уведомлений
 */
data class NotificationsUiState(
    val notifications: List<GroupHabitNotification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
