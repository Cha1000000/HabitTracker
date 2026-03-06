package com.habittracker.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.gamification.GetUserStatsUseCase
import com.habittracker.domain.usecase.gamification.GetLeaderboardUseCase
import com.habittracker.domain.usecase.gamification.GetYearlyActivityUseCase
import com.habittracker.domain.usecase.gamification.LeaderboardType
import com.habittracker.domain.model.gamification.ActivityLevel
import com.habittracker.presentation.state.StatsUiState
import com.habittracker.presentation.ui.habits.components.HeatmapItem
import com.habittracker.presentation.ui.habits.components.HeatmapLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val getYearlyActivityUseCase: GetYearlyActivityUseCase,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userIdArg: String? = savedStateHandle.get<String>("userId")

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
    
    private var leaderboardJob: Job? = null
    private var activityJob: Job? = null
    private var currentUserId: String? = null

    init {
        loadUserStats()
    }

    private fun loadUserStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                if (userIdArg != null) {
                    currentUserId = userIdArg
                    
                    // Загружаем статистику пользователя
                    val userStats = getUserStatsUseCase.getUserStats(userIdArg)
                    
                    // Загружаем лидерборд с текущим типом
                    loadLeaderboard(userIdArg, _uiState.value.leaderboardType)

                    // Загружаем активность за год
                    loadYearlyActivity(userIdArg)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userStats = userStats,
                        error = null
                    )
                } else {
                    authRepository.getCurrentUser().collectLatest { currentUser ->
                        if (currentUser != null) {
                            currentUserId = currentUser.id
                            
                            // Загружаем статистику пользователя
                            val userStats = getUserStatsUseCase.getUserStats(currentUser.id)
                            
                            // Загружаем лидерборд с текущим типом
                            loadLeaderboard(currentUser.id, _uiState.value.leaderboardType)
    
                            // Загружаем активность за год
                            loadYearlyActivity(currentUser.id)
    
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
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки статистики"
                )
            }
        }
    }
    
    private fun loadYearlyActivity(userId: String) {
        activityJob?.cancel()
        activityJob = viewModelScope.launch {
            getYearlyActivityUseCase(userId).collect { activityList ->
                val heatmapItems = activityList.map { daily ->
                    HeatmapItem(
                        date = daily.date,
                        count = daily.count,
                        level = when (daily.level) {
                            ActivityLevel.NONE -> HeatmapLevel.NONE
                            ActivityLevel.LOW -> HeatmapLevel.LOW
                            ActivityLevel.MEDIUM -> HeatmapLevel.MEDIUM
                            ActivityLevel.HIGH -> HeatmapLevel.HIGH
                            ActivityLevel.VERY_HIGH -> HeatmapLevel.VERY_HIGH
                        }
                    )
                }
                _uiState.value = _uiState.value.copy(yearlyActivity = heatmapItems)
            }
        }
    }
    
    private fun loadLeaderboard(userId: String, type: LeaderboardType) {
        leaderboardJob?.cancel()
        leaderboardJob = viewModelScope.launch {
            getLeaderboardUseCase.invoke(userId, type).collect { entries ->
                _uiState.value = _uiState.value.copy(
                    leaderboardEntries = entries,
                    leaderboard = entries.map { it.user }
                )
            }
        }
    }
    
    /**
     * Переключает тип лидерборда между ALL_TIME и WEEKLY
     */
    fun toggleLeaderboardType() {
        val newType = when (_uiState.value.leaderboardType) {
            LeaderboardType.ALL_TIME -> LeaderboardType.WEEKLY
            LeaderboardType.WEEKLY -> LeaderboardType.ALL_TIME
        }
        _uiState.value = _uiState.value.copy(leaderboardType = newType)
        currentUserId?.let { loadLeaderboard(it, newType) }
    }

    fun refresh() {
        loadUserStats()
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}