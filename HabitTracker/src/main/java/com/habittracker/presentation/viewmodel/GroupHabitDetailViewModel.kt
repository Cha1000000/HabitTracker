package com.habittracker.presentation.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitInvitationStatus
import com.habittracker.domain.model.User
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.NudgeRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.MarkHabitCompletedUseCase
import com.habittracker.domain.usecase.MarkHabitIncompleteUseCase
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.habit.AttachPhotoToEntryUseCase
import com.habittracker.domain.usecase.habit.GenerateHabitInviteCodeUseCase
import com.habittracker.domain.usecase.habit.GetHabitInvitationsUseCase
import com.habittracker.domain.usecase.reset.ResetProgressUseCase
import com.habittracker.domain.usecase.social.SendNudgeUseCase
import com.habittracker.presentation.state.GroupHabitDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import timber.log.Timber
import javax.inject.Inject
import com.habittracker.presentation.widget.TodayHabitsWidgetScheduler

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
    private val markHabitCompletedUseCase: MarkHabitCompletedUseCase,
    private val markHabitIncompleteUseCase: MarkHabitIncompleteUseCase,
    private val getHabitInvitationsUseCase: GetHabitInvitationsUseCase,
    private val generateHabitInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val deleteHabitUseCase: DeleteHabitUseCase,
    private val getLeaderboardUseCase: com.habittracker.domain.usecase.gamification.GetLeaderboardUseCase,
    private val attachPhotoToEntryUseCase: AttachPhotoToEntryUseCase,
    private val sendNudgeUseCase: SendNudgeUseCase,
    private val nudgeRepository: NudgeRepository,
    private val resetProgressUseCase: ResetProgressUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GroupHabitDetailUiState())
    val uiState: StateFlow<GroupHabitDetailUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = authRepository.getCurrentUserId() ?: ""
    
    // Job для отслеживания подписок на Flow
    private var entriesCollectionJob: Job? = null
    private var invitationsCollectionJob: Job? = null
    private var leaderboardJob: Job? = null
    private var nudgesCollectionJob: Job? = null

    /**
     * Загрузка детальной информации о привычке
     */
    fun loadHabitDetails(habitId: String) {
        // Отменяем предыдущие подписки
        entriesCollectionJob?.cancel()
        invitationsCollectionJob?.cancel()
        leaderboardJob?.cancel()
        nudgesCollectionJob?.cancel()
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, currentUserId = currentUserId) }

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
                        
                        participantUsers.distinct()
                    } catch (_: Exception) {
                        // В случае ошибки возвращаем пустой список
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // Обновляем базовую информацию о привычке
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        habit = habit,
                        participants = participants,
                        inviteCode = habit.inviteCode,
                        isOwner = isOwner
                    )
                }

                // Подписываемся на изменения записей о выполнении привычки
                if (habit.isGroupHabit()) {
                    collectHabitEntries(habitId, participants.map { it.id })
                    // Загружаем лидерборд участников
                    loadParticipantLeaderboard(habitId)
                    // Подписываемся на nudges (кого уже "пнули")
                    collectNudgedUserIds(habitId)
                }

                // Загружаем приглашения (только для владельца)
                if (isOwner) {
                    invitationsCollectionJob = viewModelScope.launch {
                        getHabitInvitationsUseCase.getOutgoingInvitations(currentUserId)
                            .catch { /* Игнорируем ошибки */ }
                            .collect { invitations ->
                                val habitInvitations = invitations.filter { 
                                    it.habitId == habitId && it.status == HabitInvitationStatus.PENDING 
                                }
                                _uiState.update { it.copy(pendingInvitations = habitInvitations) }
                            }
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
     * Подписка на изменения записей о выполнении привычки для всех участников
     */
    private fun collectHabitEntries(habitId: String, participantIds: List<String>) {
        entriesCollectionJob?.cancel()
        entriesCollectionJob = viewModelScope.launch {
            habitEntryRepository.getEntriesForHabit(habitId)
                .catch { e ->
                    // Логируем ошибку, но не прерываем поток
                    Timber.tag("GroupHabitDetailVM").e(e, "Error collecting entries")
                }
                .collect { allEntries ->
                    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    
                    // Фильтруем записи только для участников группы
                    val relevantEntries = allEntries.filter { entry ->
                        entry.userId in participantIds
                    }
                    
                    // Проверяем статус выполнения для текущего пользователя
                    val isCompletedToday = relevantEntries.any { entry ->
                        entry.userId == currentUserId && 
                        entry.date == today && 
                        entry.completed
                    }

                    val todayPhotoUrl = relevantEntries.firstOrNull { entry ->
                        entry.userId == currentUserId && entry.date == today
                    }?.photoUrl
                    
                    // Рассчитываем групповую серию
                    val groupStreak = calculateGroupStreak(relevantEntries, participantIds)
                    
                    // Обновляем UI состояние
                    _uiState.update { state ->
                        state.copy(
                            habitEntries = relevantEntries,
                            groupStreak = groupStreak,
                            isCompletedToday = isCompletedToday,
                            currentUserPhotoUrl = if (state.isUploadingPhoto) state.currentUserPhotoUrl else todayPhotoUrl
                        )
                    }
                }
        }
    }

    /**
     * Загрузка лидерборда участников привычки
     */
    private fun loadParticipantLeaderboard(habitId: String) {
        leaderboardJob?.cancel()
        leaderboardJob = viewModelScope.launch {
            getLeaderboardUseCase.getHabitLeaderboard(habitId)
                .catch { e ->
                    Timber.tag("GroupHabitDetailVM").e(e, "Error loading leaderboard")
                }
                .collect { leaderboard ->
                    _uiState.update { it.copy(participantLeaderboard = leaderboard) }
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
     * Использует DeleteHabitUseCase для корректной обработки выхода и отправки уведомлений
     */
    fun leaveGroup(onSuccess: () -> Unit) {
        val habit = _uiState.value.habit ?: return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                deleteHabitUseCase(habit.id, currentUserId)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        successMessage = application.getString(R.string.left_group_successfully)
                    )
                }
                // Вызываем callback для навигации назад
                onSuccess()
            } catch (e: Exception) {
                Timber.tag("GroupHabitDetailVM").e(e, "Error leaving group")
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
     * Переключение состояния выполнения привычки
     */
    fun toggleHabitCompletion() {
        val habit = _uiState.value.habit ?: return
        val isCompleted = _uiState.value.isCompletedToday
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isMarkingCompletion = true) }
                
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                
                if (isCompleted) {
                    // Снимаем отметку о выполнении
                    markHabitIncompleteUseCase(habit.id, currentUserId, today)
                } else {
                    // Отмечаем как выполненное
                    markHabitCompletedUseCase(habit.id, currentUserId, today)
                }
                
                _uiState.update { 
                    it.copy(
                        isMarkingCompletion = false,
                        isCompletedToday = !isCompleted,
                        showPhotoProofPrompt = !isCompleted, // Показываем предложение добавить фото при выполнении
                        successMessage = if (!isCompleted) {
                            application.getString(R.string.habit_marked_complete)
                        } else {
                            application.getString(R.string.habit_marked_incomplete)
                        }
                    )
                }
                
                // Перезагружаем данные для обновления статистики
                loadHabitDetails(habit.id)

                TodayHabitsWidgetScheduler.enqueueRefresh(application)
                
            } catch (_: Exception) {
                _uiState.update { 
                    it.copy(
                        isMarkingCompletion = false,
                        error = application.getString(R.string.error_updating_habit_status)
                    )
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Отменяем все активные подписки при уничтожении ViewModel
        entriesCollectionJob?.cancel()
        invitationsCollectionJob?.cancel()
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
    
    /**
     * Показывает BottomSheet для добавления фото
     */
    fun showPhotoSheet() {
        _uiState.update { it.copy(showPhotoSheet = true) }
    }
    
    /**
     * Скрывает BottomSheet для добавления фото
     */
    fun hidePhotoSheet() {
        _uiState.update { it.copy(showPhotoSheet = false) }
    }
    
    /**
     * Скрывает предложение добавить фото
     */
    fun dismissPhotoProofPrompt() {
        _uiState.update { it.copy(showPhotoProofPrompt = false) }
    }
    
    /**
     * Прикрепляет фото к записи привычки
     */
    fun attachPhoto(uri: Uri) {
        val habit = _uiState.value.habit ?: return
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true) }
            try {
                val result = attachPhotoToEntryUseCase(
                    sourceUri = uri,
                    habitId = habit.id,
                    userId = currentUserId,
                    date = today
                )
                
                if (result.isSuccess) {
                    val cloudUrl = result.getOrNull()
                    _uiState.update { 
                        it.copy(
                            isUploadingPhoto = false,
                            currentUserPhotoUrl = cloudUrl,
                            photoUploadSuccessMessage = application.getString(R.string.photo_proof_upload_success),
                            successMessage = application.getString(R.string.photo_proof_upload_success)
                        ) 
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isUploadingPhoto = false,
                            error = application.getString(R.string.photo_proof_upload_error)
                        ) 
                    }
                }
            } catch (e: Exception) {
                Timber.tag("GroupHabitDetailVM").e(e, "Failed to attach photo")
                _uiState.update { 
                    it.copy(
                        isUploadingPhoto = false,
                        error = application.getString(R.string.photo_proof_upload_error)
                    ) 
                }
            }
        }
    }

    fun clearPhotoUploadSuccessMessage() {
        _uiState.update { it.copy(photoUploadSuccessMessage = null) }
    }
    
    /**
     * Удаляет фото из записи привычки
     */
    fun deletePhoto() {
        val habit = _uiState.value.habit ?: return
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true) }
            try {
                habitEntryRepository.updatePhotoUrl(habit.id, currentUserId, today, null)
                _uiState.update { 
                    it.copy(
                        isUploadingPhoto = false,
                        currentUserPhotoUrl = null
                    ) 
                }
            } catch (e: Exception) {
                Timber.tag("GroupHabitDetailVM").e(e, "Failed to delete photo")
                _uiState.update { 
                    it.copy(
                        isUploadingPhoto = false,
                        error = application.getString(R.string.photo_proof_delete_error)
                    ) 
                }
            }
        }
    }
    
    /**
     * Подписка на изменения nudgedUserIds (кого уже "пнули" и cooldown не истёк)
     */
    private fun collectNudgedUserIds(habitId: String) {
        nudgesCollectionJob?.cancel()
        nudgesCollectionJob = viewModelScope.launch {
            nudgeRepository.getNudgedUserIdsFlow(habitId, currentUserId)
                .catch { e ->
                    Timber.tag("GroupHabitDetailVM").e(e, "Failed to collect nudged user ids")
                }
                .collect { nudgedIds ->
                    _uiState.update { it.copy(nudgedUserIds = nudgedIds) }
                }
        }
    }
    
    /**
     * Отправить nudge (пинг) участнику
     */
    fun sendNudge(receiverId: String) {
        val habitId = _uiState.value.habit?.id ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingNudge = true) }
            
            when (sendNudgeUseCase(habitId, receiverId)) {
                is SendNudgeUseCase.Result.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isSendingNudge = false,
                            nudgedUserIds = state.nudgedUserIds + receiverId,
                            successMessage = application.getString(R.string.nudge_sent_success)
                        )
                    }
                }
                is SendNudgeUseCase.Result.OnCooldown -> {
                    _uiState.update { 
                        it.copy(
                            isSendingNudge = false,
                            error = application.getString(R.string.nudge_on_cooldown)
                        ) 
                    }
                }
                is SendNudgeUseCase.Result.Error -> {
                    _uiState.update { 
                        it.copy(
                            isSendingNudge = false,
                            error = application.getString(R.string.nudge_send_error)
                        ) 
                    }
                }
            }
        }
    }

    /**
     * Сбросить мою статистику по привычке (сезон + удаление записей)
     */
    fun resetMyStats() {
        val habitId = _uiState.value.habit?.id ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                resetProgressUseCase.resetGroupHabitFullForUser(habitId, currentUserId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = application.getString(R.string.reset_group_mine_success)
                    )
                }
                loadHabitDetails(habitId)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.reset_group_mine_error)
                    )
                }
            }
        }
    }

    /**
     * Сбросить статистику для всех участников (только автор)
     */
    fun resetForAll() {
        val habit = _uiState.value.habit ?: return
        if (habit.createdBy != currentUserId) {
            _uiState.update { it.copy(error = application.getString(R.string.reset_group_all_forbidden)) }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                resetProgressUseCase.resetGroupHabitFullForAll(habit.id, currentUserId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = application.getString(R.string.reset_group_all_success)
                    )
                }
                loadHabitDetails(habit.id)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.reset_group_all_error)
                    )
                }
            }
        }
    }
}
