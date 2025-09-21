package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.AchievementCategory
import com.habittracker.domain.model.AchievementWithProgress
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.gamification.GetAchievementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val getAchievementsUseCase: GetAchievementsUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        loadAllAchievements()
    }

    fun loadAllAchievements() {
        loadAchievementsByCategory(null)
    }

    fun loadAchievementsByCategory(category: AchievementCategory?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val currentUser = authRepository.getCurrentUser().first()
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Пользователь не авторизован"
                    )
                    return@launch
                }

                getAchievementsUseCase.execute(currentUser.id, category).collect { achievements ->
                    val unlockedCount = achievements.count { it.isUnlocked }
                    val totalCount = achievements.size
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        achievements = achievements,
                        unlockedCount = unlockedCount,
                        totalCount = totalCount,
                        selectedCategory = category,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось загрузить достижения"
                )
            }
        }
    }

    fun refreshAchievements() {
        loadAchievementsByCategory(_uiState.value.selectedCategory)
    }
}

data class AchievementsUiState(
    val isLoading: Boolean = false,
    val achievements: List<AchievementWithProgress> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
    val selectedCategory: AchievementCategory? = null,
    val error: String? = null
)
