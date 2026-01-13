package com.habittracker.domain.usecase.habit

import com.habittracker.R
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.usecase.UpdateHabitUseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.random.Random

/**
 * Use case для генерации уникальных кодов приглашения в групповые привычки
 */
class GenerateHabitInviteCodeUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val stringProvider: StringProvider
) {
    
    companion object {
        private const val CODE_LENGTH = 8
        private const val CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val MAX_GENERATION_ATTEMPTS = 10
    }

    /**
     * Генерирует уникальный код приглашения для групповой привычки
     * @param habitId ID привычки
     * @param userId ID пользователя (должен быть создателем привычки)
     * @return Result с кодом приглашения или ошибкой
     */
    suspend operator fun invoke(
        habitId: String,
        userId: String
    ): Result<String> {
        return try {
            // Получаем привычку для проверки прав
            val habit = habitRepository.getHabitById(habitId)
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_habit_not_found)))

            // Проверяем права доступа
            if (habit.createdBy != userId) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_only_creator_can_generate_code)))
            }

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_invite_codes_group_only)))
            }

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_cannot_create_code_inactive_habit)))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_no_free_slots)))
            }

            // Генерируем уникальный код
            val inviteCode = generateUniqueCode()

            // Обновляем привычку с новым кодом
            val updatedHabit = habit.copy(
                inviteCode = inviteCode,
                isPublic = true, // Делаем привычку доступной по коду
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            )

            // Используем UpdateHabitUseCase для синхронизации с Firestore
            updateHabitUseCase(updatedHabit)

            Result.success(inviteCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отключает код приглашения для привычки
     * @param habitId ID привычки
     * @param userId ID пользователя (должен быть создателем привычки)
     * @return Result с результатом операции
     */
    suspend fun disableInviteCode(
        habitId: String,
        userId: String
    ): Result<Unit> {
        return try {
            val habit = habitRepository.getHabitById(habitId)
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_habit_not_found)))

            if (habit.createdBy != userId) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_only_creator_can_disable_code)))
            }

            val updatedHabit = habit.copy(
                inviteCode = null,
                isPublic = false,
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            )

            // Используем UpdateHabitUseCase для синхронизации с Firestore
            updateHabitUseCase(updatedHabit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Генерирует уникальный код приглашения
     */
    private suspend fun generateUniqueCode(): String {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val code = generateRandomCode()
            
            // Проверяем уникальность кода
            val existingHabit = habitRepository.getHabitByInviteCode(code)
            if (existingHabit == null) {
                return code
            }
        }
        
        // Если не удалось сгенерировать уникальный код за MAX_GENERATION_ATTEMPTS попыток
        throw Exception(stringProvider.getString(R.string.error_generate_code_failed))
    }

    /**
     * Генерирует случайный код заданной длины
     */
    private fun generateRandomCode(): String {
        return (1..CODE_LENGTH)
            .map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }
            .joinToString("")
    }

    /**
     * Проверяет валидность формата кода приглашения
     */
    fun isValidInviteCodeFormat(code: String): Boolean {
        return code.length == CODE_LENGTH && 
               code.all { it in CODE_CHARS }
    }
}
