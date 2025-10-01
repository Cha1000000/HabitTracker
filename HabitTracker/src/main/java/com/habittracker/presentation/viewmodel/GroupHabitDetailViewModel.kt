package com.habittracker.presentation.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.model.User
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.habit.GenerateHabitInviteCodeUseCase
import com.habittracker.domain.usecase.habit.GetGroupHabitProgressUseCase
import com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase
import com.habittracker.presentation.state.GroupHabitDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

/**
 * ViewModel для экрана детальной информации о групповой привычке
 * 
 * Основные функции:
 * - Загрузка информации о привычке и участниках
 * - Управление приглашениями и кодами
 * - Отметка выполнения привычки
 * - Управление участниками группы
 */
@HiltViewModel
class GroupHabitDetailViewModel @Inject constructor(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val getHabitInvitationsUseCase: GetHabitInvitationsUseCase,
    private val generateHabitInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val getGroupHabitProgressUseCase: GetGroupHabitProgressUseCase,
    private val habitEntryRepository: HabitEntryRepository,
    private val markHabitCompletedUseCase: MarkHabitCompletedUseCase,
    private val markHabitIncompleteUseCase: MarkHabitIncompleteUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GroupHabitDetailUiState())
    val uiState: StateFlow<GroupHabitDetailUiState> = _uiState.asStateFlow()

    // Получаем текущий ID пользователя из AuthRepository
    private val currentUserId: String
        get() = authRepository.getCurrentUserId() ?: ""

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
                        // Получаем всех участников (создатель + участники из списка)
                        val allParticipantIds = listOf(habit.createdBy) + habit.participants
                        
                        // Загружаем информацию о каждом участнике через UserRepository
                        val participantUsers = allParticipantIds.mapNotNull { userId ->
                            try {
                                userRepository.getUserById(userId)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        participantUsers
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // Загружаем записи о выполнении привычки
                val habitEntries = if (habit.isGroupHabit()) {
                    try {
                        // Загружаем все записи привычки за последние 30 дней
                        habitEntryRepository.getEntriesForHabit(habitId).first()
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // Загружаем статистику групповой привычки
                val groupStreak = if (habit.isGroupHabit()) {
                    try {
                        val groupProgress = getGroupHabitProgressUseCase(habitId).first()
                        groupProgress?.groupStreak ?: 0
                    } catch (e: Exception) {
                        0
                    }
                } else {
                    0
                }

                // Проверяем, выполнена ли привычка сегодня текущим пользователем
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val isCompletedToday = habitEntries.any { entry ->
                    entry.userId == currentUserId && entry.date == today && entry.completed
                }

                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        habit = habit,
                        participants = participants,
                        habitEntries = habitEntries,
                        inviteCode = habit.inviteCode,
                        isOwner = isOwner,
                        groupStreak = groupStreak,
                        isCompletedToday = isCompletedToday
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
     * Переключение выполнения привычки за сегодня
     */
    fun toggleHabitCompletion() {
        val habit = _uiState.value.habit ?: return
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        //viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Переключаем статус выполнения
                val wasCompleted = _uiState.value.isCompletedToday
                viewModelScope.launch {
                    if (wasCompleted) {
                        markHabitIncompleteUseCase(habit.id, currentUserId, today)
                    } else {
                        markHabitCompletedUseCase(habit.id, currentUserId, today)
                    }
                }
                // Перезагружаем данные для обновления статуса
                loadHabitDetails(habit.id)
                
                val message = if (!_uiState.value.isCompletedToday) {
                    application.getString(R.string.habit_marked_complete)
                } else {
                    application.getString(R.string.habit_marked_incomplete)
                }
                
                _uiState.update { 
                    it.copy(
                        successMessage = message
                    )
                }
            } catch (_: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.error_updating_habit)
                    )
                }
            }
        //}
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
     * Поделиться invite-кодом через системный Share Intent
     */
    fun shareInviteCode(inviteCode: String) {
        try {
            val shareText = application.getString(R.string.invite_code_share_text, inviteCode)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val shareIntent = Intent.createChooser(sendIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            application.startActivity(shareIntent)
            
            _uiState.update { 
                it.copy(
                    successMessage = application.getString(R.string.invite_code_shared)
                )
            }
        } catch (_: Exception) {
            _uiState.update { 
                it.copy(
                    error = application.getString(R.string.error_sharing_invite_code)
                )
            }
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
            } catch (_: Exception) {
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
            } catch (_: Exception) {
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
     * Удаление участника из группы
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
            } catch (_: Exception) {
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
                        successMessage = application.getString(R.string.left_group_successfully),
                        shouldNavigateBack = true
                    )
                }

            } catch (_: Exception) {
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

    /**
     * Сброс флага навигации назад
     */
    fun onNavigatedBack() {
        _uiState.update { it.copy(shouldNavigateBack = false) }
    }
}
