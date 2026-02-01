package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.presentation.utils.GroupHabitConstants
import com.habittracker.presentation.utils.GroupHabitValidationResult
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.HabitTimeOfDay

@Immutable
data class AddEditHabitUiState(
    // Основные поля
    val habitId: String? = null,
    val title: String = "",
    val description: String = "",
    val icon: String = "🛎",
    val color: Int = 0xFF2196F3.toInt(), // Цвет привычки как Int
    val type: HabitType = HabitType.PERSONAL,
    val goalType: HabitGoalType = HabitGoalType.SIMPLE,


    // Настройки частоты
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    
    // Время выполнения (множественный выбор для создания нескольких привычек)
    val selectedTimesOfDay: Set<HabitTimeOfDay> = setOf(HabitTimeOfDay.ANY_TIME),
    // Флаг режима редактирования (при редактировании нельзя менять количество периодов)
    val isEditMode: Boolean = false,
    
    // Даты начала и окончания привычки
    val startDate: LocalDate? = null, // Дата начала (null = с сегодня)
    val endDate: LocalDate? = null, // Дата окончания (null = бессрочно)
    val canEditStartDate: Boolean = true, // Можно ли редактировать дату начала (false после первого выполнения)

    // Напоминание
    // Явный флаг включения напоминаний (не завязан на nullability времени)
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime? = null,
    // Выбранные дни недели для напоминаний (используются для WEEKLY/TWICE_A_WEEK/CUSTOM)
    val reminderDaysInput: Set<DayOfWeek> = emptySet(),
    
    // Повторное напоминание (follow-up)
    val followUpReminderEnabled: Boolean = true,
    val followUpReminderDuration: LocalTime = LocalTime(2, 0), // По умолчанию 2 часа

    // Целевые значения
    val targetValue: Int = 1,
    // Текстовое состояние для поля «Целевое значение», чтобы поддерживать промежуточные значения (пустая строка, ведущие нули и т.п.)
    val targetValueInput: String = "1",
    val unit: String = "раз",

    // Групповые привычки
    val maxParticipants: Int = GroupHabitConstants.DEFAULT_MAX_PARTICIPANTS,
    // Строковое состояние для поля ввода «Максимум участников»
    // Нужно для корректного ввода и промежуточных значений (например, пустая строка)
    val maxParticipantsInput: String = GroupHabitConstants.DEFAULT_MAX_PARTICIPANTS.toString(),
    val inviteCode: String? = null,
    val isPublic: Boolean = false,
    val showRemoveFromPublicCatalogOption: Boolean = false,
    val removeFromPublicCatalog: Boolean = false,
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
    // Можно ли применить напоминание для всех (создатель групповой привычки)
    val canApplyReminderToAll: Boolean = false,

    // Индикаторы состояния
    val hasUnsavedChanges: Boolean = false,
    val isFormValid: Boolean = true,
    // Флаг намерения отправить приглашения после сохранения (Variant B)
    val sendInvitesAfterSave: Boolean = false,
    // Когда true — экран полностью инициализирован данными (исключаем стартовый кадр с дефолтами)
    val isInitialized: Boolean = false
)