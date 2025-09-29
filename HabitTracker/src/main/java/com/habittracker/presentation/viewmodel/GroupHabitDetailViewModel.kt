package com.habittracker.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.model.User
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.habit.GenerateHabitInviteCodeUseCase
import com.habittracker.domain.usecase.habit.GetGroupHabitProgressUseCase
import com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase
import com.habittracker.domain.usecase.social.GetFriendsUseCase
import com.habittracker.presentation.state.GroupHabitDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для экрана детальной информации о групповой привычке
 */
@HiltViewModel
class GroupHabitDetailViewModel @Inject constructor(
    private val application: Application,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val getHabitInvitationsUseCase: GetHabitInvitationsUseCase,
    private val generateHabitInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val getGroupHabitProgressUseCase: GetGroupHabitProgressUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GroupHabitDetailUiState())
    val uiState: StateFlow<GroupHabitDetailUiState> = _uiState.asStateFlow()

    private val currentUserId = "DUMMY_USER_ID" // TODO: Заменить на реальный ID

    /**
     * Загрузка детальной информации о привычке
     */
    fun loadHabitDetails(habitId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                // Загружаем привычку
                val habit = getHabitByIdUseCase(habitId)
                if (habit == null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = application.getString(R.string.error_habit_not_found)
                        )
                    }
                    return@launch
                }

                // Проверяем, является ли пользователь владельцем
                val isOwner = habit.createdBy == currentUserId

                // Загружаем участников групповой привычки
                val participants: List<User> = if (habit.isGroupHabit()) {
                    try {
                        // Получаем всех друзей пользователя
                        val friends: List<FriendshipWithUsers> = getFriendsUseCase(currentUserId).first()
                        
                        // Получаем всех участников привычки (создатель + участники)
                        val allParticipantIds = listOf(habit.createdBy) + habit.participants
                        
                        // Фильтруем друзей, оставляя только участников привычки
                        val participantUsers = friends.mapNotNull { friendship: FriendshipWithUsers ->
                            val otherUser = friendship.getOtherUser(currentUserId)
                            if (otherUser != null && allParticipantIds.contains(otherUser.id)) {
                                otherUser
                            } else null
                        }
                        
                        participantUsers
                    } catch (e: Exception) {
                        // В случае ошибки возвращаем пустой список
                        emptyList<User>()
                    }
                } else {
                    emptyList<User>()
                }

                // Загружаем полную статистику групповой привычки
                val (habitEntries, groupStreak) = if (habit.isGroupHabit()) {
                    try {
                        val groupProgress = getGroupHabitProgressUseCase(habitId).first()
                        if (groupProgress != null) {
                            // Используем групповую серию из прогресса
                            Pair(emptyList<HabitEntry>(), groupProgress.groupStreak)
                        } else {
                            Pair(emptyList<HabitEntry>(), 0)
                        }
                    } catch (e: Exception) {
                        Pair(emptyList<HabitEntry>(), 0)
                    }
                } else {
                    Pair(emptyList<HabitEntry>(), 0)
                }

                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        habit = habit,
                        participants = participants,
                        habitEntries = habitEntries,
                        inviteCode = habit.inviteCode,
                        isOwner = isOwner,
                        groupStreak = groupStreak
                    )
                }

                // Загружаем приглашения (только для владельца)
                if (isOwner) {
                    getHabitInvitationsUseCase.getOutgoingInvitations(currentUserId).collect { invitations ->
                        val habitInvitations = invitations.filter { 
                            it.habitId == habitId && it.status == HabitInvitationStatus.PENDING 
                        }
                        _uiState.update { it.copy(pendingInvitations = habitInvitations) }
                    }
                }

            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.error_loading_habit_details)
                    )
                }
            }
        }
    }

    /**
     * Переключение режима управления группой
     */
    fun toggleManageMode() {
        _uiState.update { it.copy(isManageMode = !it.isManageMode) }
    }

    /**
     * Генерация invite-кода
     */
    fun generateInviteCode() {
        val habit = _uiState.value.habit ?: return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isGeneratingCode = true) }
                
                val result = generateHabitInviteCodeUseCase(habit.id, currentUserId)
                result.fold(
                    onSuccess = { inviteCode ->
                        _uiState.update { 
                            it.copy(
                                isGeneratingCode = false,
                                inviteCode = inviteCode,
                                successMessage = application.getString(R.string.invite_code_generated)
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { 
                            it.copy(
                                isGeneratingCode = false,
                                error = error.message ?: application.getString(R.string.error_generating_invite_code)
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isGeneratingCode = false,
                        error = application.getString(R.string.error_generating_invite_code)
                    )
                }
            }
        }
    }

    /**
     * Поделиться invite-кодом
     */
    fun shareInviteCode(inviteCode: String) {
        // TODO: Реализовать системный диалог поделиться
        _uiState.update { 
            it.copy(
                successMessage = application.getString(R.string.invite_code_copied)
            )
        }
    }

    /**
     * Отключение invite-кода
     */
    fun disableInviteCode() {
        val habit = _uiState.value.habit ?: return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Отключаем invite-код через обновление привычки
                val updatedHabit = habit.copy(inviteCode = null)
                updateHabitUseCase(updatedHabit)
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        habit = updatedHabit,
                        inviteCode = null,
                        successMessage = application.getString(R.string.invite_code_disabled)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.error_disabling_invite_code)
                    )
                }
            }
        }
    }

    /**
     * Переключение публичного доступа к привычке
     */
    fun togglePublicAccess(isPublic: Boolean) {
        val habit = _uiState.value.habit ?: return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val updatedHabit = habit.copy(isPublic = isPublic)
                updateHabitUseCase(updatedHabit)
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        habit = updatedHabit,
                        successMessage = if (isPublic) {
                            application.getString(R.string.habit_made_public)
                        } else {
                            application.getString(R.string.habit_made_private)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.error_updating_habit)
                    )
                }
            }
        }
    }

    /**
     * Удаление участника из группы (упрощенная версия)
     */
    fun removeParticipant(userId: String) {
        val habit = _uiState.value.habit ?: return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Удаляем участника через обновление привычки
                val updatedParticipants = habit.participants.filter { it != userId }
                val updatedHabit = habit.copy(participants = updatedParticipants)
                updateHabitUseCase(updatedHabit)
                
                // Перезагружаем данные
                loadHabitDetails(habit.id)
                _uiState.update { 
                    it.copy(
                        successMessage = application.getString(R.string.participant_removed)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.error_removing_participant)
                    )
                }
            }
        }
    }

    /**
     * Выход из групповой привычки
     */
    fun leaveGroup() {
        val habit = _uiState.value.habit ?: return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Удаляем текущего пользователя из списка участников
                val updatedParticipants = habit.participants.filter { it != currentUserId }
                val updatedHabit = habit.copy(participants = updatedParticipants)
                
                updateHabitUseCase(updatedHabit)
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        successMessage = application.getString(R.string.left_group_successfully)
                    )
                }
                // Можно добавить навигацию назад
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.error_leaving_group)
                    )
                }
            }
        }
    }

    /**
     * Очистка ошибки
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Очистка сообщения об успехе
     */
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}