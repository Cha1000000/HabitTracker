package com.habittracker.presentation.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.model.User
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.habit.GenerateHabitInviteCodeUseCase
import com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase
import com.habittracker.presentation.state.GroupHabitDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import javax.inject.Inject

/**
 * ViewModel для экрана детальной информации о групповой привычке
 */
@HiltViewModel
class GroupHabitDetailViewModel @Inject constructor(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val getHabitInvitationsUseCase: GetHabitInvitationsUseCase,
    private val generateHabitInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GroupHabitDetailUiState())
    val uiState: StateFlow<GroupHabitDetailUiState> = _uiState.asStateFlow()

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
                        // Загружаем данные о каждом участнике
                        val participantUsers = mutableListOf<User>()
                        
                        // Загружаем создателя
                        userRepository.getUserById(habit.createdBy)?.let { creator ->
                            participantUsers.add(creator)
                        }
                        
                        // Загружаем остальных участников
                        for (participantId in habit.participants) {
                            userRepository.getUserById(participantId)?.let { participant ->
                                participantUsers.add(participant)
                            }
                        }
                        
                        participantUsers
                    } catch (_: Exception) {
                        // В случае ошибки возвращаем пустой список
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // Загружаем полную статистику групповой привычки
                val (habitEntries, groupStreak) = if (habit.isGroupHabit()) {
                    try {
                        // Загружаем записи о выполнении для всех участников
                        val allEntries = mutableListOf<HabitEntry>()
                        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                        
                        // Загружаем записи для каждого участника за последние 30 дней
                        for (participant in participants) {
                            val userHabitEntries = habitEntryRepository.getEntriesForUserAndPeriod(
                                habitId = habitId,
                                userId = participant.id,
                                startDate = today.minus(DatePeriod(days = 30)),
                                endDate = today
                            )
                            allEntries.addAll(userHabitEntries)
                        }
                        
                        // Рассчитываем групповую серию
                        val streak = calculateGroupStreak(allEntries, participants.map { it.id })
                        Pair(allEntries, streak)
                    } catch (_: Exception) {
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

            } catch (_: Exception) {
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
            } catch (_: Exception) {
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
        val habit = _uiState.value.habit ?: return
        
        // Создаем Intent для отправки текста
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, application.getString(
                R.string.share_invite_code_text,
                habit.title,
                inviteCode
            ))
            putExtra(Intent.EXTRA_SUBJECT, application.getString(R.string.share_invite_code_subject))
        }
        
        // Создаем chooser для выбора приложения
        val chooserIntent = Intent.createChooser(
            shareIntent,
            application.getString(R.string.share_invite_code_title)
        )
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        // Запускаем системный диалог
        application.startActivity(chooserIntent)
        
        _uiState.update { 
            it.copy(
                successMessage = application.getString(R.string.invite_code_shared)
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
    fun leaveGroup(onSuccess: () -> Unit) {
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
                
                // Вызываем callback для навигации назад
                onSuccess()
                
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
     * Рассчитывает групповую серию выполнения привычки
     * Групповая серия считается только для дней, когда ВСЕ участники выполнили привычку
     */
    private fun calculateGroupStreak(
        entries: List<HabitEntry>,
        participantIds: List<String>
    ): Int {
        if (entries.isEmpty() || participantIds.isEmpty()) return 0
        
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        var currentDate = today
        var streak = 0
        
        // Идем назад от сегодняшнего дня
        while (true) {
            // Проверяем, выполнили ли все участники привычку в этот день
            val entriesForDate = entries.filter { it.date == currentDate }
            val completedUserIds = entriesForDate.filter { it.completed }.map { it.userId }.toSet()
            
            // Если не все участники выполнили привычку, прерываем серию
            if (!participantIds.all { it in completedUserIds }) {
                // Если это сегодня и никто не выполнил, серия может продолжаться со вчерашнего дня
                if (currentDate == today && completedUserIds.isEmpty()) {
                    currentDate = currentDate.minus(DatePeriod(days = 1))
                    continue
                }
                break
            }
            
            streak++
            currentDate = currentDate.minus(DatePeriod(days = 1))
            
            // Ограничиваем проверку 365 днями
            if (streak > 365) break
        }
        
        return streak
    }
}