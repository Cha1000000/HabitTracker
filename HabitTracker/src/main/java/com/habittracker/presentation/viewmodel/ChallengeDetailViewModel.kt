package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeParticipantStats
import com.habittracker.domain.model.getDurationInDays
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengeDetailUiState(
    val isLoading: Boolean = true,
    val challenge: Challenge? = null,
    val habit: Habit? = null,
    val participants: List<User> = emptyList(),
    val participantStats: Map<String, ChallengeParticipantStats> = emptyMap(),
    val currentUserId: String? = null,
    val error: String? = null,
    val isActionInProgress: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val showCancelConfirmDialog: Boolean = false,
    val showLeaveConfirmDialog: Boolean = false,
    val showRemoveParticipantDialog: Boolean = false,
    val participantToRemove: User? = null,
    val showAddParticipantDialog: Boolean = false,
    val availableFriends: List<User> = emptyList(),
    val selectedNewParticipants: List<User> = emptyList()
)

@HiltViewModel
class ChallengeDetailViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengeDetailUiState())
    val uiState: StateFlow<ChallengeDetailUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            currentUserId = authRepository.getCurrentUserId()
        )
    }

    fun loadChallenge(challengeId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val challenge = challengeRepository.getChallengeById(challengeId)
                
                if (challenge == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Челлендж не найден"
                    )
                    return@launch
                }

                // Load habit
                val habit = habitRepository.getHabitById(challenge.habitId)

                // Load participants
                val participants = mutableListOf<User>()
                for (participantId in challenge.participants) {
                    val user = socialRepository.getUserById(participantId)
                    if (user != null) {
                        participants.add(user)
                    }
                }

                // Calculate stats for each participant
                val stats = calculateParticipantStats(challenge, participants)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    challenge = challenge,
                    habit = habit,
                    participants = participants,
                    participantStats = stats
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка загрузки: ${e.message}"
                )
            }
        }
    }

    private suspend fun calculateParticipantStats(
        challenge: Challenge,
        participants: List<User>
    ): Map<String, ChallengeParticipantStats> {
        val stats = mutableMapOf<String, ChallengeParticipantStats>()
        val totalDays = challenge.getDurationInDays()

        // Get completion data for each participant
        val completionRates = mutableListOf<Pair<String, Float>>()

        val currentUserId = _uiState.value.currentUserId
        
        for (user in participants) {
            try {
                // Для текущего пользователя берём из Room, для других - из Firestore
                val completedDays = if (user.id == currentUserId) {
                    challengeRepository.getCompletedDaysForUserInChallenge(
                        challengeId = challenge.id,
                        habitId = challenge.habitId,
                        userId = user.id,
                        startDate = challenge.startDate,
                        endDate = challenge.endDate
                    )
                } else {
                    challengeRepository.getCompletedDaysFromFirestore(
                        habitId = challenge.habitId,
                        userId = user.id,
                        startDate = challenge.startDate,
                        endDate = challenge.endDate
                    )
                }
                
                val currentStreak = challengeRepository.getCurrentStreakForUserInChallenge(
                    habitId = challenge.habitId,
                    userId = user.id,
                    startDate = challenge.startDate,
                    endDate = challenge.endDate
                )

                val completionRate = if (totalDays > 0) {
                    completedDays.toFloat() / totalDays.toFloat()
                } else 0f

                completionRates.add(user.id to completionRate)

                stats[user.id] = ChallengeParticipantStats(
                    userId = user.id,
                    completedDays = completedDays,
                    totalDays = totalDays,
                    currentStreak = currentStreak,
                    completionRate = completionRate,
                    position = 0 // Will be set after sorting
                )
            } catch (e: Exception) {
                // If we can't get stats, use defaults
                stats[user.id] = ChallengeParticipantStats(
                    userId = user.id,
                    completedDays = 0,
                    totalDays = totalDays,
                    currentStreak = 0,
                    completionRate = 0f,
                    position = participants.size
                )
            }
        }

        // Sort by completion rate and assign positions
        val sortedByRate = completionRates.sortedByDescending { it.second }
        sortedByRate.forEachIndexed { index, (userId, _) ->
            stats[userId]?.let { stat ->
                stats[userId] = stat.copy(position = index + 1)
            }
        }

        return stats
    }

    fun showDeleteConfirmDialog() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmDialog = true)
    }

    fun hideDeleteConfirmDialog() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmDialog = false)
    }

    fun showCancelConfirmDialog() {
        _uiState.value = _uiState.value.copy(showCancelConfirmDialog = true)
    }

    fun hideCancelConfirmDialog() {
        _uiState.value = _uiState.value.copy(showCancelConfirmDialog = false)
    }

    fun deleteChallenge(onSuccess: () -> Unit) {
        val challengeId = _uiState.value.challenge?.id ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true, showDeleteConfirmDialog = false)
            try {
                challengeRepository.deleteChallenge(challengeId)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    error = "Ошибка удаления: ${e.message}"
                )
            }
        }
    }

    fun cancelChallenge(onSuccess: () -> Unit) {
        val challengeId = _uiState.value.challenge?.id ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true, showCancelConfirmDialog = false)
            try {
                challengeRepository.cancelChallenge(challengeId)
                // Reload challenge to show updated status
                loadChallenge(challengeId)
                _uiState.value = _uiState.value.copy(isActionInProgress = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    error = "Ошибка отмены: ${e.message}"
                )
            }
        }
    }

    fun resendInvitation(userId: String) {
        val challenge = _uiState.value.challenge ?: return
        val habit = _uiState.value.habit ?: return
        val currentUserId = _uiState.value.currentUserId ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            try {
                val creatorUser = socialRepository.getUserById(currentUserId)
                socialRepository.sendChallengeInvitationNotification(
                    toUserId = userId,
                    fromUserName = creatorUser?.name ?: "Пользователь",
                    challengeTitle = challenge.title,
                    habitTitle = habit.title
                )
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    error = "Ошибка отправки приглашения: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun showLeaveConfirmDialog() {
        _uiState.value = _uiState.value.copy(showLeaveConfirmDialog = true)
    }
    
    fun hideLeaveConfirmDialog() {
        _uiState.value = _uiState.value.copy(showLeaveConfirmDialog = false)
    }
    
    fun leaveChallenge(onNavigateBack: () -> Unit) {
        val challenge = _uiState.value.challenge ?: return
        val currentUserId = _uiState.value.currentUserId ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true, showLeaveConfirmDialog = false)
            try {
                challengeRepository.leaveChallenge(challenge.id, currentUserId)
                _uiState.value = _uiState.value.copy(isActionInProgress = false)
                onNavigateBack()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    error = "Ошибка выхода из челленджа: ${e.message}"
                )
            }
        }
    }
    
    fun showAddParticipantDialog() {
        viewModelScope.launch {
            val currentUserId = _uiState.value.currentUserId ?: return@launch
            val challenge = _uiState.value.challenge ?: return@launch
            
            // Загружаем друзей, которые ещё не участвуют в челлендже
            socialRepository.getFriends(currentUserId).collect { friendships ->
                val friends = friendships.map { friendship ->
                    if (friendship.user1.id == currentUserId) friendship.user2 else friendship.user1
                }
                val availableFriends = friends.filter { friend ->
                    !challenge.participants.contains(friend.id)
                }
                
                _uiState.value = _uiState.value.copy(
                    showAddParticipantDialog = true,
                    availableFriends = availableFriends,
                    selectedNewParticipants = emptyList()
                )
                return@collect
            }
        }
    }
    
    fun hideAddParticipantDialog() {
        _uiState.value = _uiState.value.copy(
            showAddParticipantDialog = false,
            selectedNewParticipants = emptyList()
        )
    }
    
    fun onNewParticipantToggle(user: User) {
        val currentSelected = _uiState.value.selectedNewParticipants.toMutableList()
        if (currentSelected.contains(user)) {
            currentSelected.remove(user)
        } else {
            currentSelected.add(user)
        }
        _uiState.value = _uiState.value.copy(selectedNewParticipants = currentSelected)
    }
    
    fun showRemoveParticipantDialog(user: User) {
        val challenge = _uiState.value.challenge ?: return
        val currentUserId = _uiState.value.currentUserId ?: return
        
        // Нельзя удалить создателя или себя
        if (user.id == challenge.createdBy || user.id == currentUserId) return
        
        _uiState.value = _uiState.value.copy(
            showRemoveParticipantDialog = true,
            participantToRemove = user
        )
    }
    
    fun hideRemoveParticipantDialog() {
        _uiState.value = _uiState.value.copy(
            showRemoveParticipantDialog = false,
            participantToRemove = null
        )
    }
    
    fun confirmRemoveParticipant() {
        val challenge = _uiState.value.challenge ?: return
        val participant = _uiState.value.participantToRemove ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isActionInProgress = true,
                showRemoveParticipantDialog = false,
                participantToRemove = null
            )
            try {
                challengeRepository.leaveChallenge(challenge.id, participant.id)
                
                // Перезагружаем челлендж
                loadChallenge(challenge.id)
                
                _uiState.value = _uiState.value.copy(isActionInProgress = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    error = "Ошибка удаления участника: ${e.message}"
                )
            }
        }
    }
    
    fun addSelectedParticipants() {
        val challenge = _uiState.value.challenge ?: return
        val selectedParticipants = _uiState.value.selectedNewParticipants
        val habit = _uiState.value.habit ?: return
        val currentUserId = _uiState.value.currentUserId ?: return
        
        if (selectedParticipants.isEmpty()) {
            hideAddParticipantDialog()
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            try {
                // Добавляем участников в челлендж
                val newParticipantIds = selectedParticipants.map { it.id }
                challengeRepository.addParticipantsToChallenge(challenge.id, newParticipantIds)
                
                // Отправляем уведомления новым участникам
                val creatorUser = socialRepository.getUserById(currentUserId)
                for (participant in selectedParticipants) {
                    socialRepository.sendChallengeInvitationNotification(
                        toUserId = participant.id,
                        fromUserName = creatorUser?.name ?: "Пользователь",
                        challengeTitle = challenge.title,
                        habitTitle = habit.title
                    )
                }
                
                // Перезагружаем челлендж
                loadChallenge(challenge.id)
                
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    showAddParticipantDialog = false,
                    selectedNewParticipants = emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isActionInProgress = false,
                    error = "Ошибка добавления участников: ${e.message}"
                )
            }
        }
    }
}
