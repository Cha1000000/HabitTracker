package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.HabitRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import kotlin.random.Random

/**
 * Use case для генерации уникальных кодов приглашения в групповые привычки
 */
class GenerateHabitInviteCodeUseCase @Inject constructor(
    private val habitRepository: HabitRepository
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
                ?: return Result.failure(Exception("Привычка не найдена"))

            // Проверяем права доступа
            if (habit.createdBy != userId) {
                return Result.failure(Exception("Только создатель привычки может генерировать коды приглашения"))
            }

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                return Result.failure(Exception("Коды приглашения доступны только для групповых привычек"))
            }

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                return Result.failure(Exception("Нельзя создавать коды для неактивной привычки"))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                return Result.failure(Exception("В привычке нет свободных мест для новых участников"))
            }

            // Генерируем уникальный код
            val inviteCode = generateUniqueCode()

            // Обновляем привычку с новым кодом
            val updatedHabit = habit.copy(
                inviteCode = inviteCode,
                isPublic = true, // Делаем привычку доступной по коду
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )

            habitRepository.updateHabit(updatedHabit)

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
                ?: return Result.failure(Exception("Привычка не найдена"))

            if (habit.createdBy != userId) {
                return Result.failure(Exception("Только создатель привычки может отключить код приглашения"))
            }

            val updatedHabit = habit.copy(
                inviteCode = null,
                isPublic = false,
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )

            habitRepository.updateHabit(updatedHabit)
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
        throw Exception("Не удалось сгенерировать уникальный код приглашения. Попробуйте позже.")
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
