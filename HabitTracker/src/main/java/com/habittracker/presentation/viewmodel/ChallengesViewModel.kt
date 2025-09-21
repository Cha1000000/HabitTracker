package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.usecase.GetCurrentUserUseCase
import com.habittracker.domain.usecase.challenge.GetActiveChallengesUseCase
import com.habittracker.domain.usecase.challenge.GetChallengeResultsUseCase
import com.habittracker.domain.usecase.challenge.JoinChallengeUseCase
import com.habittracker.domain.usecase.challenge.LeaveChallengeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengesUiState(
    val activeChallenges: List<Challenge> = emptyList(),
    val endingSoonChallenges: List<Challenge> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedTab: ChallengeTab = ChallengeTab.ACTIVE,
    val showJoinDialog: Boolean = false,
    val joinChallengeId: String? = null
)

enum class ChallengeTab {
    ACTIVE,
    ENDING_SOON,
    COMPLETED
}

@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getActiveChallengesUseCase: GetActiveChallengesUseCase,
    private val getChallengeResultsUseCase: GetChallengeResultsUseCase,
    private val joinChallengeUseCase: JoinChallengeUseCase,
    private val leaveChallengeUseCase: LeaveChallengeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengesUiState())
    val uiState: StateFlow<ChallengesUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                getCurrentUserUseCase().collect { user ->
                    user?.let { currentUser ->
                        currentUserId = currentUser.id
                        
                        getActiveChallengesUseCase(currentUser.id).collect { challenges ->
                            _uiState.value = _uiState.value.copy(
                                activeChallenges = challenges.filter { it.status == ChallengeStatus.ACTIVE },
                                endingSoonChallenges = challenges.filter { it.status == ChallengeStatus.ACTIVE }, // Временно используем ACTIVE
                                isLoading = false
                            )
                        }
                    }
                }
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
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
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
}
