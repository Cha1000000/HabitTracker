package com.habittracker.domain.model

/**
 * Константы и валидация для модели Habit
 */
object HabitValidation {
    // Константы для валидации
    const val MIN_TITLE_LENGTH = 1
    const val MAX_TITLE_LENGTH = 100
    const val MAX_DESCRIPTION_LENGTH = 500
    const val MIN_TARGET_VALUE = 1
    const val MAX_TARGET_VALUE = 100
    const val MIN_GROUP_PARTICIPANTS = 2
    const val MAX_GROUP_PARTICIPANTS = 20
    const val INVITE_CODE_LENGTH = 8
    const val MAX_UNIT_LENGTH = 20
    
    // Регулярные выражения
    val INVITE_CODE_REGEX = Regex("^[A-Z0-9]{$INVITE_CODE_LENGTH}$")
    val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    
    /**
     * Результат валидации привычки
     */
    data class HabitValidationResult(
        val isValid: Boolean,
        val errors: List<HabitValidationError> = emptyList()
    ) {
        fun hasError(field: HabitField): Boolean = 
            errors.any { it.field == field }
            
        fun getError(field: HabitField): HabitValidationError? = 
            errors.find { it.field == field }
    }
    
    /**
     * Ошибка валидации
     */
    data class HabitValidationError(
        val field: HabitField,
        val message: String,
        val code: String
    )
    
    /**
     * Поля привычки для валидации
     */
    enum class HabitField {
        TITLE,
        DESCRIPTION,
        ICON,
        COLOR,
        TARGET_VALUE,
        UNIT,
        MAX_PARTICIPANTS,
        INVITE_CODE,
        PARTICIPANTS
    }
    
    /**
     * Валидация привычки
     */
    fun validateHabit(habit: Habit): HabitValidationResult {
        val errors = mutableListOf<HabitValidationError>()
        
        // Валидация заголовка
        when {
            habit.title.isBlank() -> errors.add(
                HabitValidationError(
                    HabitField.TITLE,
                    "Название привычки не может быть пустым",
                    "TITLE_EMPTY"
                )
            )
            habit.title.length < MIN_TITLE_LENGTH -> errors.add(
                HabitValidationError(
                    HabitField.TITLE,
                    "Название должно содержать минимум $MIN_TITLE_LENGTH символ",
                    "TITLE_TOO_SHORT"
                )
            )
            habit.title.length > MAX_TITLE_LENGTH -> errors.add(
                HabitValidationError(
                    HabitField.TITLE,
                    "Название не должно превышать $MAX_TITLE_LENGTH символов",
                    "TITLE_TOO_LONG"
                )
            )
        }
        
        // Валидация описания
        if (habit.description.length > MAX_DESCRIPTION_LENGTH) {
            errors.add(
                HabitValidationError(
                    HabitField.DESCRIPTION,
                    "Описание не должно превышать $MAX_DESCRIPTION_LENGTH символов",
                    "DESCRIPTION_TOO_LONG"
                )
            )
        }
        
        // Валидация иконки
        if (habit.icon.isBlank()) {
            errors.add(
                HabitValidationError(
                    HabitField.ICON,
                    "Иконка не может быть пустой",
                    "ICON_EMPTY"
                )
            )
        }
        
        // Валидация цвета
        if (!HEX_COLOR_REGEX.matches(habit.color)) {
            errors.add(
                HabitValidationError(
                    HabitField.COLOR,
                    "Неверный формат цвета. Используйте формат #RRGGBB",
                    "COLOR_INVALID_FORMAT"
                )
            )
        }
        
        // Валидация целевого значения
        when {
            habit.targetValue < MIN_TARGET_VALUE -> errors.add(
                HabitValidationError(
                    HabitField.TARGET_VALUE,
                    "Целевое значение должно быть минимум $MIN_TARGET_VALUE",
                    "TARGET_VALUE_TOO_LOW"
                )
            )
            habit.targetValue > MAX_TARGET_VALUE -> errors.add(
                HabitValidationError(
                    HabitField.TARGET_VALUE,
                    "Целевое значение не должно превышать $MAX_TARGET_VALUE",
                    "TARGET_VALUE_TOO_HIGH"
                )
            )
        }
        
        // Валидация единицы измерения
        when {
            habit.unit.isBlank() -> errors.add(
                HabitValidationError(
                    HabitField.UNIT,
                    "Единица измерения не может быть пустой",
                    "UNIT_EMPTY"
                )
            )
            habit.unit.length > MAX_UNIT_LENGTH -> errors.add(
                HabitValidationError(
                    HabitField.UNIT,
                    "Единица измерения не должна превышать $MAX_UNIT_LENGTH символов",
                    "UNIT_TOO_LONG"
                )
            )
        }
        
        // Валидация для групповых привычек
        if (habit.type == HabitType.GROUP) {
            // Валидация максимального количества участников
            when {
                habit.maxParticipants < MIN_GROUP_PARTICIPANTS -> errors.add(
                    HabitValidationError(
                        HabitField.MAX_PARTICIPANTS,
                        "Групповая привычка должна поддерживать минимум $MIN_GROUP_PARTICIPANTS участников",
                        "MAX_PARTICIPANTS_TOO_LOW"
                    )
                )
                habit.maxParticipants > MAX_GROUP_PARTICIPANTS -> errors.add(
                    HabitValidationError(
                        HabitField.MAX_PARTICIPANTS,
                        "Максимальное количество участников не должно превышать $MAX_GROUP_PARTICIPANTS",
                        "MAX_PARTICIPANTS_TOO_HIGH"
                    )
                )
            }
            
            // Валидация invite-кода
            habit.inviteCode?.let { code ->
                if (!INVITE_CODE_REGEX.matches(code)) {
                    errors.add(
                        HabitValidationError(
                            HabitField.INVITE_CODE,
                            "Код приглашения должен содержать $INVITE_CODE_LENGTH заглавных букв и цифр",
                            "INVITE_CODE_INVALID_FORMAT"
                        )
                    )
                }
            }
            
            // Валидация участников
            if (habit.participants.size > habit.maxParticipants) {
                errors.add(
                    HabitValidationError(
                        HabitField.PARTICIPANTS,
                        "Количество участников превышает максимально допустимое",
                        "TOO_MANY_PARTICIPANTS"
                    )
                )
            }
        }
        
        return HabitValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Быстрая проверка валидности привычки
     */
    fun isValidHabit(habit: Habit): Boolean = validateHabit(habit).isValid
    
    /**
     * Генерация случайного invite-кода
     */
    fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..INVITE_CODE_LENGTH)
            .map { chars.random() }
            .joinToString("")
    }
    
    /**
     * Проверка валидности invite-кода
     */
    fun isValidInviteCode(code: String): Boolean = INVITE_CODE_REGEX.matches(code)
}
