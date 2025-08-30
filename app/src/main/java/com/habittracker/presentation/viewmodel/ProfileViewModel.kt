package com.habittracker.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.auth.UpsertSignedInUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val socialRepository: SocialRepository,
    private val upsertUser: UpsertSignedInUserUseCase,
    private val storage: FirebaseStorage,
) : ViewModel() {

    data class UiState(
        val user: User? = null,
        val editableName: String = "",
        val isEditingName: Boolean = false,
        val pendingAvatarUri: Uri? = null,
        val isSaving: Boolean = false,
        val hasChanges: Boolean = false,
        val error: String? = null,
        val isOwnProfile: Boolean = true,
        val isFriend: Boolean = false,
        val isRequestSent: Boolean = false,
        val isActionInProgress: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val currentUserId: String? get() = authRepository.getCurrentUserId()

    private val viewedUserId: String? = savedStateHandle.get<String>("userId")

    init {
        observeUser()
        refreshFriendship()
    }

    private fun observeUser() {
        val selfId = currentUserId
        val uid = viewedUserId ?: selfId ?: return
        viewModelScope.launch {
            userRepository.observeUser(uid)
                .map { user ->
                    // при первом приходе проставим имя для редактирования
                    val curr = _state.value
                    val defaultName = user?.name?.ifBlank { user.email.substringBefore("@") } ?: ""
                    curr.copy(
                        user = user,
                        editableName = if (curr.editableName.isBlank()) defaultName else curr.editableName,
                        hasChanges = curr.pendingAvatarUri != null || (user != null && curr.editableName != user.name),
                        isOwnProfile = viewedUserId == null || (selfId != null && viewedUserId == selfId)
                    )
                }
                .collect { _state.value = it }
        }
    }

    private fun refreshFriendship() {
        val selfId = currentUserId
        val otherId = viewedUserId
        if (selfId == null || otherId == null || selfId == otherId) return
        viewModelScope.launch {
            try {
                val friends = socialRepository.areFriends(selfId, otherId)
                _state.value = _state.value.copy(isFriend = friends, isRequestSent = if (friends) false else _state.value.isRequestSent)
            } catch (_: Exception) {
            }
        }
    }

    fun onToggleEditName() {
        if (!_state.value.isOwnProfile) return
        _state.value = _state.value.copy(isEditingName = !_state.value.isEditingName)
    }

    fun onNameChange(newName: String) {
        if (!_state.value.isOwnProfile) return
        val user = _state.value.user
        _state.value = _state.value.copy(
            editableName = newName,
            hasChanges = user != null && newName != user.name || _state.value.pendingAvatarUri != null
        )
    }

    fun onAvatarPicked(uri: Uri?) {
        if (!_state.value.isOwnProfile) return
        _state.value = _state.value.copy(
            pendingAvatarUri = uri,
            hasChanges = uri != null || _state.value.editableName != _state.value.user?.name
        )
    }

    fun saveChanges() {
        if (!_state.value.isOwnProfile) return
        val user = _state.value.user ?: return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isSaving = true, error = null)

                var avatarUrl: String? = user.avatarUrl
                // Загрузка аватара если выбран
                val picked = _state.value.pendingAvatarUri
                if (picked != null) {
                    val ref = storage.reference.child("user_avatars/${user.id}/${System.currentTimeMillis()}.jpg")
                    ref.putFile(picked).await()
                    avatarUrl = ref.downloadUrl.await().toString()
                }

                val nameToSave = _state.value.editableName.ifBlank { user.email.substringBefore("@") }
                val updated = user.copy(
                    name = nameToSave,
                    avatarUrl = avatarUrl,
                    avatarThumbnailUrl = avatarUrl
                )
                // сохраняем без изменения FCM токена
                upsertUser(updated, null)

                _state.value = _state.value.copy(
                    isSaving = false,
                    isEditingName = false,
                    pendingAvatarUri = null,
                    hasChanges = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.message ?: "Unknown error")
            }
        }
    }

    // Friendship actions for viewing other user's profile
    fun addFriend() {
        val selfId = currentUserId
        val otherId = viewedUserId
        if (selfId == null || otherId == null || selfId == otherId) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isActionInProgress = true)
                val can = socialRepository.canSendFriendRequest(selfId, otherId)
                if (can) {
                    socialRepository.sendFriendRequest(selfId, otherId, null)
                    _state.value = _state.value.copy(isRequestSent = true)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isActionInProgress = false)
            }
        }
    }

    fun removeFriend() {
        val selfId = currentUserId
        val otherId = viewedUserId
        if (selfId == null || otherId == null || selfId == otherId) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isActionInProgress = true)
                val res = socialRepository.removeFriend(selfId, otherId)
                if (res.isSuccess) {
                    _state.value = _state.value.copy(isFriend = false, isRequestSent = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isActionInProgress = false)
            }
        }
    }

}
