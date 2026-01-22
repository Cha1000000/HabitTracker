package com.habittracker.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.UpdateHabitUseCase
import com.habittracker.domain.usecase.habit.JoinHabitByCodeUseCase
import com.habittracker.domain.usecase.notification.SaveParticipantJoinedNotificationUseCase
import com.habittracker.presentation.state.PublicHabitsUiState
import com.habittracker.presentation.ui.discover.HabitCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * ViewModel для экрана публичных привычек
 * Управляет состоянием каталога публичных привычек, поиском и присоединением
 */
@HiltViewModel
class PublicHabitsViewModel @Inject constructor(
    application: Application,
    private val habitRepository: HabitRepository,
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val joinHabitByCodeUseCase: JoinHabitByCodeUseCase,
    private val saveParticipantJoinedNotificationUseCase: SaveParticipantJoinedNotificationUseCase
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(PublicHabitsUiState())
    val uiState: StateFlow<PublicHabitsUiState> = _uiState.asStateFlow()
    
    private val _currentUserId = MutableStateFlow<String?>(null)
    
    init {
        loadPublicHabits()
        observeCurrentUser()
    }
    
    /**
     * Загружает список публичных привычек
     */
    private fun loadPublicHabits() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Загружаем все публичные привычки
                habitRepository.getPublicHabits()
                    .collect { habits ->
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                publicHabits = habits,
                                filteredHabits = filterAndSortHabits(
                                    habits = habits,
                                    query = state.searchQuery,
                                    category = state.selectedCategory,
                                    sortByPopularity = state.sortByPopularity
                                )
                            )
                        }
                    }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(R.string.public_habits_error_load)
                    )
                }
            }
        }
    }
    
    /**
     * Наблюдает за текущим пользователем
     */
    private fun observeCurrentUser() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _currentUserId.value = user?.id
            }
        }
    }
    
    /**
     * Изменение поискового запроса
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredHabits = filterAndSortHabits(
                    habits = state.publicHabits,
                    query = query,
                    category = state.selectedCategory,
                    sortByPopularity = state.sortByPopularity
                )
            )
        }
    }
    
    /**
     * Выбор категории
     */
    fun selectCategory(category: HabitCategory) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                filteredHabits = filterAndSortHabits(
                    habits = state.publicHabits,
                    query = state.searchQuery,
                    category = category,
                    sortByPopularity = state.sortByPopularity
                )
            )
        }
    }
    
    /**
     * Переключение сортировки
     */
    fun toggleSortOrder() {
        _uiState.update { state ->
            val newSortByPopularity = !state.sortByPopularity
            state.copy(
                sortByPopularity = newSortByPopularity,
                filteredHabits = filterAndSortHabits(
                    habits = state.publicHabits,
                    query = state.searchQuery,
                    category = state.selectedCategory,
                    sortByPopularity = newSortByPopularity
                )
            )
        }
    }
    
    /**
     * Присоединение к привычке
     */
    fun joinHabit(habit: Habit) {
        viewModelScope.launch {
            _currentUserId.value?.let { userId ->
                _uiState.update { it.copy(isLoading = true) }
                
                try {
                    // Присоединяться можно только к групповой привычке
                    if (!habit.isGroupHabit()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = getApplication<Application>().getString(R.string.error_code_group_only)
                            )
                        }
                        return@launch
                    }

                    // Проверяем, не является ли пользователь уже участником
                    if (habit.participants.contains(userId)) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = getApplication<Application>().getString(R.string.error_already_participating)
                            )
                        }
                        return@launch
                    }
                    
                    // Проверяем, есть ли место в группе
                    if (habit.participants.size >= habit.maxParticipants) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = getApplication<Application>().getString(R.string.public_habits_group_full)
                            )
                        }
                        return@launch
                    }
                    
                    // Присоединяемся к привычке
                    val updatedHabit = habit.copy(
                        createdBy = if (habit.createdBy.isBlank()) userId else habit.createdBy,
                        participants = habit.participants + userId
                    )
                    // Используем UpdateHabitUseCase для синхронизации с Firestore
                    updateHabitUseCase(updatedHabit)
                    
                    // Отправляем уведомления всем участникам
                    try {
                        val user = socialRepository.getUserById(userId)
                        if (user != null) {
                            saveParticipantJoinedNotificationUseCase(
                                habitId = habit.id,
                                habitTitle = habit.title,
                                joinedUserId = userId,
                                joinedUserName = user.name,
                                participantIds = updatedHabit.participants
                            )
                        }
                    } catch (e: Exception) {
                        // Логируем ошибку, но не прерываем процесс присоединения
                        android.util.Log.e("PublicHabitsViewModel", "Failed to send join notifications", e)
                    }
                    
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = getApplication<Application>().getString(R.string.join_habit_success, habit.title)
                        )
                    }
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = getApplication<Application>().getString(R.string.public_habits_join_error)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Присоединение по коду приглашения
     */
    fun joinByCode(code: String) {
        viewModelScope.launch {
            _currentUserId.value?.let { userId ->
                _uiState.update { it.copy(isLoading = true) }
                
                joinHabitByCodeUseCase(code, userId)
                    .onSuccess { habit ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                successMessage = getApplication<Application>().getString(R.string.join_habit_success, habit.title)
                            )
                        }
                        // Перезагружаем список привычек
                        loadPublicHabits()
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: getApplication<Application>().getString(R.string.error_invalid_invite_code)
                            )
                        }
                    }
            }
        }
    }
    
    /**
     * Очистка сообщений об ошибках
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Очистка сообщений об успехе
     */
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
    
    /**
     * Фильтрация и сортировка привычек
     */
    private fun filterAndSortHabits(
        habits: List<Habit>,
        query: String,
        category: HabitCategory,
        sortByPopularity: Boolean
    ): List<Habit> {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        
        var filtered = habits
        
        // Фильтрация по датам активности
        filtered = filtered.filter { habit ->
            // Не показываем привычки, которые ещё не начались
            val hasStarted = habit.startDate?.let { it <= today } ?: true
            
            // Не показываем привычки, которые уже закончились
            val notEnded = habit.endDate?.let { it >= today } ?: true
            
            hasStarted && notEnded && habit.isActive
        }
        
        // Фильтрация по поисковому запросу
        if (query.isNotEmpty()) {
            filtered = filtered.filter { habit ->
                habit.title.contains(query, ignoreCase = true) ||
                habit.description.contains(query, ignoreCase = true)
            }
        }
        
        // Фильтрация по категории
        if (category != HabitCategory.ALL) {
            filtered = filtered.filter { habit ->
                getCategoryForHabit(habit) == category
            }
        }
        
        // Сортировка
        return if (sortByPopularity) {
            filtered.sortedByDescending { it.participants.size }
        } else {
            filtered.sortedByDescending { it.createdAt }
        }
    }
    
    /**
     * Определение категории привычки по её содержанию
     */
    private fun getCategoryForHabit(habit: Habit): HabitCategory {
        val titleLower = habit.title.lowercase()
        val descriptionLower = habit.description.lowercase()
        val combined = "$titleLower $descriptionLower"
        
        return when {
            combined.containsAny("спорт", "фитнес", "трениров", "бег", "физич", "плава", "кататься на", "sport", "fitness", "train", "run", "swim", "ride", "gym", "exercise", "workout") -> HabitCategory.SPORT
            combined.containsAny("здоров", "питан", "сон", "вода", "витамин", "health", "nutrition", "sleep", "water", "vitamin", "diet") -> HabitCategory.HEALTH
            combined.containsAny("учеб", "книг", "чтен", "курс", "язык", "study", "book", "read", "course", "learn", "language") -> HabitCategory.EDUCATION
            combined.containsAny("работ", "продуктив", "планирова", "задач", "work", "productiv", "plan", "task", "focus") -> HabitCategory.PRODUCTIVITY
            combined.containsAny("медитац", "йог", "дыхан", "осознан", "meditation", "yoga", "breath", "mindful") -> HabitCategory.MINDFULNESS
            combined.containsAny("рисован", "музык", "творч", "искусств", "дизайн", "draw", "music", "creat", "art", "design", "paint") -> HabitCategory.CREATIVITY
            combined.containsAny("друз", "семь", "общен", "встреч", "кореш", "свидан", "friend", "family", "social", "meet", "date", "call") -> HabitCategory.SOCIAL
            else -> HabitCategory.OTHER
        }
    }
    
    /**
     * Расширение для проверки наличия любого из слов
     */
    private fun String.containsAny(vararg words: String): Boolean {
        return words.any { this.contains(it, ignoreCase = true) }
    }
}
