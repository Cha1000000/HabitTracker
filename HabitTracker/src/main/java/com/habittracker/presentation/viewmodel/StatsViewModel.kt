package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.gamification.GetUserStatsUseCase
import com.habittracker.presentation.state.StatsUiState
import com.habittracker.domain.usecase.gamification.GetLeaderboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getUserStatsUseCase: GetUserStatsUseCase,
    private val getLeaderboardUseCase: GetLeaderboardUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadUserStats()
    }

    private fun loadUserStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                authRepository.getCurrentUser().collectLatest { currentUser ->
                    if (currentUser != null) {
                        // Загружаем статистику пользователя
                        val userStats = getUserStatsUseCase.getUserStats(currentUser.id)
                        
                        // Загружаем лидерборд (подписываемся на Flow)
                        launch {
                            getLeaderboardUseCase(currentUser.id).collect { leaderboard ->
                                _uiState.value = _uiState.value.copy(
                                    leaderboard = leaderboard
                                )
                            }
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            userStats = userStats,
                            error = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Пользователь не авторизован"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки статистики"
                )
            }
        }
    }

    fun refresh() {
        loadUserStats()
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}