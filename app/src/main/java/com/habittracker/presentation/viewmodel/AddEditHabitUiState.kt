package com.habittracker.presentation.viewmodel

import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.presentation.utils.GroupHabitValidationResult
import com.habittracker.presentation.utils.GroupHabitConstants
import kotlinx.datetime.LocalTime

data class AddEditHabitUiState(
    // Основные поля
    val habitId: String? = null,
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    val color: Int = 0xFF2196F3.toInt(), // Цвет привычки как Int
    val type: HabitType = HabitType.PERSONAL,
    
    // Настройки частоты
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    
    // Напоминание
    val reminderTime: LocalTime? = null,
    
    // Целевые значения
    val targetValue: Int = 1,
    val unit: String = "раз",
    
    // Групповые привычки
    val maxParticipants: Int = GroupHabitConstants.DEFAULT_MAX_PARTICIPANTS,
    // Строковое состояние для поля ввода «Максимум участников»
    // Нужно для корректного ввода и промежуточных значений (например, пустая строка)
    val maxParticipantsInput: String = GroupHabitConstants.DEFAULT_MAX_PARTICIPANTS.toString(),
    val inviteCode: String? = null,
    val isPublic: Boolean = false,
    val participants: List<User> = emptyList(),
    val selectedFriendsToInvite: List<User> = emptyList(),
    val showFriendSelector: Boolean = false,
    val availableFriends: List<User> = emptyList(),
    
    // Состояния загрузки
    val isHabitSaved: Boolean = false,
    val isLoading: Boolean = false,
    val isGeneratingInviteCode: Boolean = false,
    val isLoadingFriends: Boolean = false,
    val isSendingInvitations: Boolean = false,
    
    // Обработка ошибок и валидации
    val error: String? = null,
    val successMessage: String? = null,
    val titleError: String? = null,
    val descriptionError: String? = null,
    val targetValueError: String? = null,
    val participantsError: String? = null,
    val inviteCodeError: String? = null,
    val groupHabitValidationResult: GroupHabitValidationResult? = null,
    
    // UX улучшения
    val showValidationErrors: Boolean = false,
    val canSaveHabit: Boolean = true,
    val inviteCodeGenerationAttempts: Int = 0,
    val lastInvitationSentAt: Long? = null,
    
    // Индикаторы состояния
    val hasUnsavedChanges: Boolean = false,
    val isFormValid: Boolean = true
)
