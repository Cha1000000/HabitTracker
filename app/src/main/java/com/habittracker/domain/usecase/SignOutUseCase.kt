package com.habittracker.domain.usecase

import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для выхода пользователя из системы.
 * Инкапсулирует логику выхода, включая очистку сессии аутентификации.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    /**
     * Выполняет выход пользователя из системы.
     *
     * @return Flow с результатом операции (успех/ошибка)
     */
    suspend operator fun invoke(): Flow<Result<Unit>> {
        return try {
            authRepository.logout()
            // Возвращаем успешный результат
            kotlinx.coroutines.flow.flow { emit(Result.success(Unit)) }
        } catch (e: Exception) {
            // Возвращаем ошибку, если что-то пошло не так
            kotlinx.coroutines.flow.flow { emit(Result.failure(e)) }
        }
    }
}
