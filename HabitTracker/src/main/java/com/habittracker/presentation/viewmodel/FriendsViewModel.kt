@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.habit.LeaveSharedHabitsWithFriendUseCase
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import com.habittracker.presentation.state.FriendsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 👥 ViewModel для экрана друзей
 * 
 * Управляет состоянием всех социальных взаимодействий:
 * - Список друзей
 * - Запросы дружбы (входящие/исходящие)
 * - Поиск пользователей
 * - Отправка и обработка запросов
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val authRepository: AuthRepository,
    private val leaveSharedHabitsWithFriendUseCase: LeaveSharedHabitsWithFriendUseCase,
    private val analyticsManager: AnalyticsManager,
    private val errorLogger: ErrorLogger
) : ViewModel() {

    // Текущий пользователь ID
    private val _currentUserId = MutableStateFlow("")
    
    // Поисковый запрос
    private val _searchQuery = MutableStateFlow("")
    
    // Результаты поиска
    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    
    // Состояние поиска
    private val _isSearching = MutableStateFlow(false)
    
    // Job для отмены предыдущего запроса поиска (предотвращает race condition)
    private var searchJob: Job? = null

    // Идентификаторы друзей, для которых идёт операция удаления (для дизейбла UI)
    private val _removingIds = MutableStateFlow<Set<String>>(emptySet())
    
    // Сообщение об ошибке для отображения пользователю
    private val _errorMessage = MutableStateFlow<String?>(null)

    init {
        // Получаем текущего пользователя асинхронно
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { currentUser ->
                val userId = currentUser?.id ?: ""
                _currentUserId.value = userId
                
                // Пересчитываем friendsCount при открытии экрана друзей
                // для исправления возможных некорректных значений в Firestore
                if (userId.isNotEmpty()) {
                    try {
                        socialRepository.recalculateFriendsCount(userId)
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Failed to recalculate friends count: ${e.message}")
                    }
                }
            }
        }
    }
    
    // Комбинируем поисковые данные отдельно
    private val searchState = combine(
        _searchQuery,
        _searchResults, 
        _isSearching
    ) { query, results, isSearching ->
        Triple(query, results, isSearching)
    }

    // UI состояние экрана - объединяем социальные данные и поиск
    private val baseUiState = combine(
        _currentUserId,
        _currentUserId.flatMapLatest { userId ->
            if (userId.isNotEmpty()) socialRepository.getFriends(userId) else flowOf(emptyList())
        },
        _currentUserId.flatMapLatest { userId ->
            if (userId.isNotEmpty()) socialRepository.getIncomingFriendRequests(userId) else flowOf(emptyList())
        },
        _currentUserId.flatMapLatest { userId ->
            if (userId.isNotEmpty()) socialRepository.getOutgoingFriendRequests(userId) else flowOf(emptyList())
        },
        searchState
    ) { currentUserId, friendships, incomingRequests, outgoingRequests, searchData ->
        FriendsUiState(
            friends = friendships.mapNotNull { friendship -> friendship.getOtherUser(currentUserId) },
            incomingRequests = incomingRequests,
            outgoingRequests = outgoingRequests,
            searchQuery = searchData.first,
            searchResults = searchData.second,
            isSearching = searchData.third
        )
    }

    val uiState: StateFlow<FriendsUiState> = combine(baseUiState, _removingIds, _errorMessage) { state, removing, error ->
        state.copy(removingIds = removing, error = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FriendsUiState()
    )

    /**
     * 🔍 Обновление поискового запроса
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            searchUsers(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    /**
     * 🔎 Поиск пользователей
     */
    private fun searchUsers(query: String) {
        // Отменяем предыдущий запрос поиска (предотвращает race condition)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            try {
                val users = socialRepository.searchUsers(query, _currentUserId.value)
                _searchResults.value = users
            } catch (_: Exception) {
                // Обработка ошибки поиска
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * 📤 Отправка запроса дружбы
     */
    fun sendFriendRequest(userId: String, message: String) {
        viewModelScope.launch {
            socialRepository.sendFriendRequest(_currentUserId.value, userId, message).fold(
                onSuccess = {
                    analyticsManager.logEvent(
                        AnalyticsEvents.FRIEND_REQUEST_SENT,
                        mapOf(AnalyticsEvents.Params.USER_ID to userId)
                    )
                    // Успешно отправлено - обновляем поиск
                    val currentQuery = _searchQuery.value
                    if (currentQuery.isNotBlank()) {
                        searchUsers(currentQuery)
                    }
                },
                onFailure = { error ->
                    errorLogger.logError(error, "Failed to send friend request to $userId")
                    Timber.tag(TAG).e(error, "Failed to send friend request")
                    _errorMessage.value = "Не удалось отправить запрос дружбы"
                }
            )
        }
    }

    /**
     * ✅ Принятие запроса дружбы
     */
    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            socialRepository.acceptFriendRequest(requestId).fold(
                onSuccess = {
                    // Запрос принят - состояние обновится через Flow
                },
                onFailure = { error ->
                    Timber.tag(TAG).e(error, "Failed to accept friend request")
                    _errorMessage.value = "Не удалось принять запрос дружбы"
                }
            )
        }
    }

    /**
     * ❌ Отклонение запроса дружбы
     */
    fun declineFriendRequest(requestId: String) {
        viewModelScope.launch {
            socialRepository.declineFriendRequest(requestId).fold(
                onSuccess = {
                    // Запрос отклонен - состояние обновится через Flow
                },
                onFailure = { error ->
                    Timber.tag(TAG).e(error, "Failed to decline friend request")
                    _errorMessage.value = "Не удалось отклонить запрос дружбы"
                }
            )
        }
    }

    /**
     * 🚫 Отмена отправленного запроса дружбы
     */
    fun cancelFriendRequest(requestId: String) {
        viewModelScope.launch {
            socialRepository.cancelFriendRequest(requestId).fold(
                onSuccess = {
                    // Запрос отменен - состояние обновится через Flow
                },
                onFailure = { error ->
                    Timber.tag(TAG).e(error, "Failed to cancel friend request")
                    _errorMessage.value = "Не удалось отменить запрос дружбы"
                }
            )
        }
    }

    /**
     * 🗑️ Удаление из друзей
     */
    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            socialRepository.removeFriend(_currentUserId.value, friendId).fold(
                onSuccess = {
                    // Друг удален - состояние обновится через Flow
                },
                onFailure = { error ->
                    Timber.tag(TAG).e(error, "Failed to remove friend")
                    _errorMessage.value = "Не удалось удалить друга"
                }
            )
        }
    }

    /**
     * 🗑️ Удаление друга с опцией выхода из совместных привычек
     */
    fun onRemoveFriendClicked(friendId: String, leaveGroupHabits: Boolean) {
        viewModelScope.launch {
            _removingIds.value = _removingIds.value + friendId
            try {
                // Сначала выходим из совместных привычек (если выбрана опция)
                if (leaveGroupHabits) {
                    leaveSharedHabitsWithFriendUseCase(_currentUserId.value, friendId).fold(
                        onSuccess = { leftCount ->
                            Timber.tag(TAG).d("Left $leftCount shared habits with friend $friendId")
                        },
                        onFailure = { error ->
                            Timber.tag(TAG).w("Failed to leave some shared habits: ${error.message}")
                        }
                    )
                }
                
                // Затем удаляем друга
                socialRepository.removeFriend(_currentUserId.value, friendId).fold(
                    onSuccess = {
                        Timber.tag(TAG).d("Friend $friendId removed successfully")
                    },
                    onFailure = { error ->
                        Timber.tag(TAG).e(error, "Failed to remove friend with options")
                        _errorMessage.value = "Не удалось удалить друга"
                    }
                )
            } finally {
                _removingIds.value -= friendId
            }
        }
    }

    /**
     * Очищает сообщение об ошибке после показа пользователю
     */
    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "FriendsViewModel"
    }
}
