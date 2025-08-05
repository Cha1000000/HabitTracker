package com.habittracker.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.model.User
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.social.AcceptFriendRequestUseCase
import com.habittracker.domain.usecase.social.CancelFriendRequestUseCase
import com.habittracker.domain.usecase.social.DeclineFriendRequestUseCase
import com.habittracker.domain.usecase.social.GetFriendRequestsUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.domain.usecase.social.RemoveFriendUseCase
import com.habittracker.domain.usecase.social.SearchUsersUseCase
import com.habittracker.domain.usecase.social.SendFriendRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SocialUiState(
    val currentUser: User? = null,
    val friends: List<FriendshipWithUsers> = emptyList(),
    val incomingRequests: List<FriendRequestWithUsers> = emptyList(),
    val outgoingRequests: List<FriendRequestWithUsers> = emptyList(),
    val searchResults: List<User> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val incomingRequestsCount: Int = 0
)

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val application: Application,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val getFriendRequestsUseCase: GetFriendRequestsUseCase,
    private val searchUsersUseCase: SearchUsersUseCase,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val acceptFriendRequestUseCase: AcceptFriendRequestUseCase,
    private val declineFriendRequestUseCase: DeclineFriendRequestUseCase,
    private val cancelFriendRequestUseCase: CancelFriendRequestUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                getCurrentUserUseCase().collect { currentUser ->
                    if (currentUser != null) {
                        _uiState.value = _uiState.value.copy(currentUser = currentUser)
                        
                        // Загружаем друзей и запросы
                        loadFriends(currentUser.id)
                        loadFriendRequests(currentUser.id)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = application.getString(R.string.error_user_not_found),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(R.string.error_loading_data),
                    isLoading = false
                )
            }
        }
    }

    private fun loadFriends(userId: String) {
        viewModelScope.launch {
            getFriendsUseCase(userId).collect { friends ->
                _uiState.value = _uiState.value.copy(
                    friends = friends,
                    isLoading = false
                )
            }
        }
    }

    private fun loadFriendRequests(userId: String) {
        viewModelScope.launch {
            // Загружаем входящие запросы
            getFriendRequestsUseCase.getIncomingRequests(userId).collect { incoming ->
                _uiState.value = _uiState.value.copy(incomingRequests = incoming)
            }
        }
        
        viewModelScope.launch {
            // Загружаем исходящие запросы
            getFriendRequestsUseCase.getOutgoingRequests(userId).collect { outgoing ->
                _uiState.value = _uiState.value.copy(outgoingRequests = outgoing)
            }
        }
        
        viewModelScope.launch {
            // Загружаем счетчик входящих запросов
            getFriendRequestsUseCase.getIncomingRequestsCount(userId).collect { count ->
                _uiState.value = _uiState.value.copy(incomingRequestsCount = count)
            }
        }
    }

    fun searchUsers(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                isSearching = false
            )
            return
        }

        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            
            try {
                val results = searchUsersUseCase(query, currentUser.id)
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(R.string.error_search_failed),
                    isSearching = false
                )
            }
        }
    }

    fun sendFriendRequest(toUserId: String, message: String? = null) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val result = sendFriendRequestUseCase(currentUser.id, toUserId, message)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = application.getString(R.string.friend_request_sent),
                        isLoading = false
                    )
                    // Обновляем результаты поиска
                    searchUsers(_uiState.value.searchQuery)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message 
                            ?: application.getString(R.string.error_sending_request),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(R.string.error_sending_request),
                    isLoading = false
                )
            }
        }
    }

    fun acceptFriendRequest(requestId: String) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val result = acceptFriendRequestUseCase(requestId, currentUser.id)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = application.getString(R.string.friend_request_accepted),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message 
                            ?: application.getString(R.string.error_accepting_request),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(R.string.error_accepting_request),
                    isLoading = false
                )
            }
        }
    }

    fun declineFriendRequest(requestId: String) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val result = declineFriendRequestUseCase(requestId, currentUser.id)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = application.getString(R.string.friend_request_declined),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message 
                            ?: application.getString(R.string.error_declining_request),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(R.string.error_declining_request),
                    isLoading = false
                )
            }
        }
    }

    fun cancelFriendRequest(requestId: String) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val result = cancelFriendRequestUseCase(requestId, currentUser.id)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = application.getString(R.string.friend_request_canceled),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message 
                            ?: application.getString(R.string.error_canceling_request),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(R.string.error_canceling_request),
                    isLoading = false
                )
            }
        }
    }

    fun removeFriend(friendId: String) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val result = removeFriendUseCase(currentUser.id, friendId)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = application.getString(R.string.friend_removed),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message 
                            ?: application.getString(R.string.error_removing_friend),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = application.getString(R.string.error_removing_friend),
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun refresh() {
        loadData()
    }
}
