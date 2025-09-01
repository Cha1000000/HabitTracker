@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val authRepository: AuthRepository
) : ViewModel() {

    // Текущий пользователь ID
    private val _currentUserId = MutableStateFlow("")
    
    // Поисковый запрос
    private val _searchQuery = MutableStateFlow("")
    
    // Результаты поиска
    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    
    // Состояние поиска
    private val _isSearching = MutableStateFlow(false)

    // Идентификаторы друзей, для которых идёт операция удаления (для дизейбла UI)
    private val _removingIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        // Получаем текущего пользователя асинхронно
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { currentUser ->
                _currentUserId.value = currentUser?.id ?: ""
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

    val uiState: StateFlow<FriendsUiState> = combine(baseUiState, _removingIds) { state, removing ->
        state.copy(removingIds = removing)
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
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val users = socialRepository.searchUsers(query, _currentUserId.value)
                _searchResults.value = users
            } catch (error: Exception) {
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
                    // Успешно отправлено - обновляем поиск
                    val currentQuery = _searchQuery.value
                    if (currentQuery.isNotBlank()) {
                        searchUsers(currentQuery)
                    }
                },
                onFailure = { error ->
                    // TODO: Показать ошибку пользователю
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
                    // TODO: Показать ошибку пользователю
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
                    // TODO: Показать ошибку пользователю
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
                    // TODO: Показать ошибку пользователю
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
                    // TODO: Показать ошибку пользователю
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
                socialRepository.removeFriend(_currentUserId.value, friendId).fold(
                    onSuccess = {
                        // Опциональный выход из совместных привычек: реализуем отдельно в use case (todo)
                        if (leaveGroupHabits) {
                            // TODO: trigger bulk leave from shared habits with this friend
                        }
                    },
                    onFailure = { _ ->
                        // Ошибку можно отобразить через отдельный механизм (snackbar/диалог)
                    }
                )
            } finally {
                _removingIds.value = _removingIds.value - friendId
            }
        }
    }
}

/**
 * 📱 UI состояние экрана друзей
 */
data class FriendsUiState(
    val friends: List<User> = emptyList(),
    val incomingRequests: List<FriendRequestWithUsers> = emptyList(),
    val outgoingRequests: List<FriendRequestWithUsers> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val removingIds: Set<String> = emptySet()
)
