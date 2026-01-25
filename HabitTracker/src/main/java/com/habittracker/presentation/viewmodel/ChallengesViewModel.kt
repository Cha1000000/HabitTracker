package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.getDaysRemaining
import com.habittracker.domain.model.isCompleted
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.challenge.CloseExpiredChallengesUseCase
import com.habittracker.domain.usecase.challenge.JoinChallengeUseCase
import com.habittracker.domain.usecase.challenge.LeaveChallengeUseCase
import com.habittracker.presentation.state.ChallengeTab
import com.habittracker.presentation.state.ChallengesUiState
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

private const val ENDING_SOON_DAYS = 3 // Челлендж считается "скоро заканчивающимся" если осталось <= 3 дней
private const val TAG = "ChallengesViewModel"

@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val challengeRepository: ChallengeRepository,
    private val firestoreRepository: FirestoreRepository,
    private val habitRepository: HabitRepository,
    private val joinChallengeUseCase: JoinChallengeUseCase,
    private val leaveChallengeUseCase: LeaveChallengeUseCase,
    private val closeExpiredChallengesUseCase: CloseExpiredChallengesUseCase,
    private val analyticsManager: AnalyticsManager,
    private val errorLogger: ErrorLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengesUiState())
    val uiState: StateFlow<ChallengesUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            closeExpiredChallengesUseCase()
        }
        initializeUser()
    }

    private fun initializeUser() {
        viewModelScope.launch {
            try {
                loadChallenges()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка загрузки пользователя: ${e.message}"
                )
            }
        }
    }

    private fun loadChallenges() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                getCurrentUserUseCase().collectLatest { user ->
                    user?.let { currentUser ->
                        currentUserId = currentUser.id
                        _uiState.value = _uiState.value.copy(currentUserId = currentUser.id)
                        
                        // Используем realtime-наблюдение за челленджами
                        challengeRepository.observeChallengesForUser(currentUser.id).collectLatest { challenges ->
                            Timber.tag(TAG).d("Received ${challenges.size} challenges for user ${currentUser.id}")
                            challenges.forEach { c ->
                                Timber.tag(TAG).d("Challenge: ${c.title}, status=${c.status}, participants=${c.participants}, acceptedParticipants=${c.acceptedParticipants}, createdBy=${c.createdBy}")
                            }
                            
                            // Приглашения - челленджи, где пользователь в participants, но не в acceptedParticipants
                            // Включаем и PENDING и ACTIVE статусы
                            val pendingInvitations = challenges.filter { challenge ->
                                challenge.participants.contains(currentUser.id) &&
                                !challenge.acceptedParticipants.contains(currentUser.id) &&
                                challenge.createdBy != currentUser.id && // Создатель автоматически принят
                                (challenge.status == ChallengeStatus.ACTIVE || challenge.status == ChallengeStatus.PENDING)
                            }
                            Timber.tag(TAG).d("Pending invitations: ${pendingInvitations.size}")
                            
                            // Активные - челленджи, где пользователь принял приглашение или является создателем
                            val acceptedChallenges = challenges.filter { challenge ->
                                (challenge.acceptedParticipants.contains(currentUser.id) || 
                                 challenge.createdBy == currentUser.id) &&
                                (challenge.status == ChallengeStatus.ACTIVE || challenge.status == ChallengeStatus.PENDING)
                            }
                            
                            val endingSoon = acceptedChallenges.filter { it.getDaysRemaining() <= ENDING_SOON_DAYS }
                            val completed = challenges.filter { it.isCompleted() }
                            
                            _uiState.value = _uiState.value.copy(
                                pendingInvitations = pendingInvitations,
                                activeChallenges = acceptedChallenges,
                                endingSoonChallenges = endingSoon,
                                completedChallenges = completed,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Нормальная отмена при refresh / пересоздании подписки
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Ошибка загрузки челленджей: ${e.message}"
                )
            }
        }
    }

    fun onTabSelected(tab: ChallengeTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun onJoinChallengeClick(challengeId: String) {
        _uiState.value = _uiState.value.copy(
            showJoinDialog = true,
            joinChallengeId = challengeId
        )
    }

    fun onJoinChallengeConfirm() {
        val challengeId = _uiState.value.joinChallengeId
        val userId = currentUserId
        
        if (challengeId != null && userId != null) {
            viewModelScope.launch {
                try {
                    val result = joinChallengeUseCase(challengeId, userId)
                    if (result.isSuccess) {
                        analyticsManager.logEvent(
                            AnalyticsEvents.CHALLENGE_JOINED,
                            mapOf(AnalyticsEvents.Params.CHALLENGE_ID to challengeId)
                        )
                        _uiState.value = _uiState.value.copy(
                            showJoinDialog = false,
                            joinChallengeId = null,
                            errorMessage = null
                        )
                        loadChallenges() // Обновляем список
                    } else {
                        _uiState.value = _uiState.value.copy(
                            showJoinDialog = false,
                            joinChallengeId = null,
                            errorMessage = result.exceptionOrNull()?.message ?: "Ошибка присоединения к челленджу"
                        )
                    }
                } catch (e: Exception) {
                    errorLogger.logError(e, "Failed to join challenge $challengeId")
                    _uiState.value = _uiState.value.copy(
                        showJoinDialog = false,
                        joinChallengeId = null,
                        errorMessage = "Ошибка: ${e.message}"
                    )
                }
            }
        }
    }

    fun onJoinChallengeCancel() {
        _uiState.value = _uiState.value.copy(
            showJoinDialog = false,
            joinChallengeId = null
        )
    }

    fun onLeaveChallengeClick(challengeId: String) {
        // Показываем диалог подтверждения
        _uiState.value = _uiState.value.copy(
            showLeaveDialog = true,
            leaveChallengeId = challengeId
        )
    }
    
    fun dismissLeaveDialog() {
        _uiState.value = _uiState.value.copy(
            showLeaveDialog = false,
            leaveChallengeId = null
        )
    }
    
    fun confirmLeaveChallenge() {
        val challengeId = _uiState.value.leaveChallengeId ?: return
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showLeaveDialog = false, leaveChallengeId = null)
            try {
                val result = leaveChallengeUseCase(challengeId, userId)
                if (result.isSuccess) {
                    loadChallenges() // Обновляем список
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Ошибка выхода из челленджа"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка: ${e.message}"
                )
            }
        }
    }

    fun onRefresh() {
        loadChallenges()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun acceptChallengeInvitation(challengeId: String) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAcceptingInvitation = true)
            try {
                val localOrUiChallenge = challengeRepository.getChallengeById(challengeId)
                    ?: _uiState.value.pendingInvitations.firstOrNull { it.id == challengeId }
                    ?: _uiState.value.activeChallenges.firstOrNull { it.id == challengeId }
                    ?: throw IllegalStateException("Челлендж не найден")

                val habit = firestoreRepository.getHabitById(localOrUiChallenge.habitId)
                    ?: throw IllegalStateException("Привычка не найдена")

                val shouldJoinHabit = habit.type == HabitType.GROUP && !habit.participants.contains(userId)
                val previousHabit = if (shouldJoinHabit) habit else null

                if (shouldJoinHabit) {
                    if (habit.participants.size >= habit.maxParticipants) {
                        throw IllegalStateException("Группа заполнена")
                    }

                    val updatedHabit = habit.copy(participants = habit.participants + userId)
                    habitRepository.updateHabit(updatedHabit)
                }

                try {
                    challengeRepository.acceptChallengeInvitation(challengeId, userId)
                    analyticsManager.logEvent(
                        AnalyticsEvents.CHALLENGE_JOINED,
                        mapOf(AnalyticsEvents.Params.CHALLENGE_ID to challengeId)
                    )
                } catch (e: Exception) {
                    if (previousHabit != null) {
                        runCatching {
                            habitRepository.updateHabit(previousHabit)
                        }.onFailure { rollbackError ->
                            Timber.tag(TAG).w(rollbackError, "Rollback failed for habitId=${previousHabit.id}")
                        }
                    }
                    throw e
                }

                _uiState.value = _uiState.value.copy(isAcceptingInvitation = false)
            } catch (e: Exception) {
                val message = when (e.message) {
                    "Группа заполнена" -> "Группа привычки заполнена — не удалось принять приглашение"
                    "Челлендж не найден" -> "Приглашение больше недоступно"
                    "Привычка не найдена" -> "Привычка недоступна — не удалось принять приглашение"
                    else -> "Не удалось принять приглашение: ${e.message ?: "неизвестная ошибка"}"
                }
                _uiState.value = _uiState.value.copy(
                    isAcceptingInvitation = false,
                    errorMessage = message
                )
            }
        }
    }

    fun declineChallengeInvitation(challengeId: String) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAcceptingInvitation = true)
            try {
                challengeRepository.declineChallengeInvitation(challengeId, userId)
                _uiState.value = _uiState.value.copy(isAcceptingInvitation = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAcceptingInvitation = false,
                    errorMessage = "Ошибка отклонения приглашения: ${e.message}"
                )
            }
        }
    }
}
