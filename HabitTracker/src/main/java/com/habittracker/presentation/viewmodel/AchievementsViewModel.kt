package com.habittracker.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.AchievementCategory
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.usecase.gamification.GetAchievementsUseCase
import com.habittracker.presentation.state.AchievementsUiState
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
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialUserIdArg: String? = savedStateHandle.get<String>("userId")
    private var targetUserId: String? = initialUserIdArg

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        // Загружаем первую категорию (HABITS) при открытии экрана
        loadAchievementsByCategory(AchievementCategory.HABITS)
    }

    fun loadAllAchievements() {
        loadAchievementsByCategory(null)
    }

    fun setTargetUserId(userId: String?) {
        if (targetUserId == userId) return
        targetUserId = userId
        loadAchievementsByCategory(_uiState.value.selectedCategory ?: AchievementCategory.HABITS)
    }

    fun loadAchievementsByCategory(category: AchievementCategory?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val resolvedUserId = targetUserId ?: run {
                    val currentUser = authRepository.getCurrentUser().first()
                    currentUser?.id
                }

                if (resolvedUserId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Пользователь не авторизован"
                    )
                    return@launch
                }

                getAchievementsUseCase.execute(resolvedUserId, category).collect { achievements ->
                    val sorted = achievements.sortedWith(
                        compareByDescending<com.habittracker.domain.model.AchievementWithProgress> { it.isUnlocked }
                            .thenByDescending { it.progress }
                            .thenBy { it.achievement.title.lowercase() }
                    )
                    val unlockedCount = achievements.count { it.isUnlocked }
                    val totalCount = achievements.size
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        achievements = sorted,
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

