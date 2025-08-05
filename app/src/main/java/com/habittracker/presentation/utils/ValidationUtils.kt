package com.habittracker.presentation.utils

import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User

/**
 * Утилиты для валидации данных групповых привычек
 */
object ValidationUtils {
    
    /**
     * Валидация названия привычки
     */
    fun validateHabitTitle(title: String): ValidationResult {
        return when {
            title.isBlank() -> ValidationResult.Error("Название привычки не может быть пустым")
            title.length < 3 -> ValidationResult.Error("Название должно содержать минимум 3 символа")
            title.length > 50 -> ValidationResult.Error("Название не должно превышать 50 символов")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Валидация описания привычки
     */
    fun validateHabitDescription(description: String): ValidationResult {
        return when {
            description.length > 200 -> ValidationResult.Error("Описание не должно превышать 200 символов")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Валидация максимального количества участников
     */
    fun validateMaxParticipants(maxParticipants: Int, habitType: HabitType): ValidationResult {
        return when {
            habitType == HabitType.PERSONAL -> ValidationResult.Success
            maxParticipants < 2 -> ValidationResult.Error("Групповая привычка должна иметь минимум 2 участника")
            maxParticipants > 20 -> ValidationResult.Error("Максимальное количество участников: 20")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Валидация списка приглашенных друзей
     */
    fun validateInvitedFriends(
        friends: List<User>, 
        maxParticipants: Int,
        currentParticipants: List<User>
    ): ValidationResult {
        val totalParticipants = friends.size + currentParticipants.size + 1 // +1 для создателя
        return when {
            totalParticipants > maxParticipants -> 
                ValidationResult.Error("Превышен лимит участников ($maxParticipants)")
            friends.isEmpty() -> 
                ValidationResult.Warning("Вы не выбрали друзей для приглашения")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Валидация invite-кода
     */
    fun validateInviteCode(inviteCode: String?): ValidationResult {
        return when {
            inviteCode.isNullOrBlank() -> ValidationResult.Error("Код приглашения не может быть пустым")
            inviteCode.length != 6 -> ValidationResult.Error("Код приглашения должен содержать 6 символов")
            !inviteCode.matches(Regex("^[A-Z0-9]{6}$")) -> 
                ValidationResult.Error("Код должен содержать только заглавные буквы и цифры")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Проверка возможности создания групповой привычки
     */
    fun canCreateGroupHabit(
        title: String,
        maxParticipants: Int,
        selectedFriends: List<User>
    ): ValidationResult {
        val titleValidation = validateHabitTitle(title)
        if (titleValidation is ValidationResult.Error) return titleValidation
        
        val participantsValidation = validateMaxParticipants(maxParticipants, HabitType.GROUP)
        if (participantsValidation is ValidationResult.Error) return participantsValidation
        
        return ValidationResult.Success
    }
    
    /**
     * Валидация частоты выполнения привычки
     */
    fun validateHabitFrequency(frequency: String?): ValidationResult {
        return when {
            frequency.isNullOrBlank() -> ValidationResult.Error("Необходимо выбрать частоту выполнения")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Валидация целевого значения привычки
     */
    fun validateTargetValue(targetValue: Int, unit: String): ValidationResult {
        return when {
            targetValue <= 0 -> ValidationResult.Error("Целевое значение должно быть больше 0")
            targetValue > 1000 -> ValidationResult.Error("Целевое значение не должно превышать 1000")
            unit.isBlank() -> ValidationResult.Error("Необходимо указать единицу измерения")
            unit.length > 20 -> ValidationResult.Error("Единица измерения не должна превышать 20 символов")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Проверка лимитов для создания групповых привычек пользователем
     */
    fun validateUserGroupHabitsLimit(currentGroupHabitsCount: Int, maxAllowed: Int = 10): ValidationResult {
        return when {
            currentGroupHabitsCount >= maxAllowed -> 
                ValidationResult.Error("Достигнут лимит групповых привычек ($maxAllowed)")
            currentGroupHabitsCount >= maxAllowed * 0.8 -> 
                ValidationResult.Warning("Приближается лимит групповых привычек (${currentGroupHabitsCount}/$maxAllowed)")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Валидация времени напоминания
     */
    fun validateReminderTime(hour: Int, minute: Int): ValidationResult {
        return when {
            hour < 0 || hour > 23 -> ValidationResult.Error("Некорректный час (0-23)")
            minute < 0 || minute > 59 -> ValidationResult.Error("Некорректные минуты (0-59)")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Проверка конфликтов в расписании привычек
     */
    fun validateScheduleConflicts(
        newHabitTitle: String,
        newReminderTime: String?,
        existingHabits: List<String>
    ): ValidationResult {
        if (newReminderTime == null) return ValidationResult.Success
        
        val conflictingHabits = existingHabits.filter { it.contains(newReminderTime) }
        return when {
            conflictingHabits.isNotEmpty() -> 
                ValidationResult.Warning("В это время уже запланированы другие привычки")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Комплексная валидация формы создания групповой привычки
     */
    fun validateGroupHabitForm(
        title: String,
        description: String,
        maxParticipants: Int,
        selectedFriends: List<User>,
        targetValue: Int,
        unit: String,
        currentGroupHabitsCount: Int
    ): GroupHabitValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Валидация названия
        when (val titleResult = validateHabitTitle(title)) {
            is ValidationResult.Error -> errors.add(titleResult.message)
            is ValidationResult.Warning -> warnings.add(titleResult.message)
            else -> {}
        }
        
        // Валидация описания
        when (val descResult = validateHabitDescription(description)) {
            is ValidationResult.Error -> errors.add(descResult.message)
            is ValidationResult.Warning -> warnings.add(descResult.message)
            else -> {}
        }
        
        // Валидация участников
        when (val participantsResult = validateMaxParticipants(maxParticipants, HabitType.GROUP)) {
            is ValidationResult.Error -> errors.add(participantsResult.message)
            is ValidationResult.Warning -> warnings.add(participantsResult.message)
            else -> {}
        }
        
        // Валидация приглашенных друзей
        when (val friendsResult = validateInvitedFriends(selectedFriends, maxParticipants, emptyList())) {
            is ValidationResult.Error -> errors.add(friendsResult.message)
            is ValidationResult.Warning -> warnings.add(friendsResult.message)
            else -> {}
        }
        
        // Валидация целевого значения
        when (val targetResult = validateTargetValue(targetValue, unit)) {
            is ValidationResult.Error -> errors.add(targetResult.message)
            is ValidationResult.Warning -> warnings.add(targetResult.message)
            else -> {}
        }
        
        // Валидация лимитов пользователя
        when (val limitResult = validateUserGroupHabitsLimit(currentGroupHabitsCount)) {
            is ValidationResult.Error -> errors.add(limitResult.message)
            is ValidationResult.Warning -> warnings.add(limitResult.message)
            else -> {}
        }
        
        return GroupHabitValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            canProceed = errors.isEmpty()
        )
    }
}

/**
 * Результат валидации
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    
    val isValid: Boolean get() = this is Success || this is Warning
    val isError: Boolean get() = this is Error
    val messageText: String? get() = when (this) {
        is Success -> null
        is Warning -> message
        is Error -> message
    }
}

/**
 * Результат валидации формы создания групповой привычки
 */
data class GroupHabitValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val canProceed: Boolean
)
