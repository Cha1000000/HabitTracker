package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.User

/**
 * 📱 UI состояние экрана друзей
 */
@Immutable
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