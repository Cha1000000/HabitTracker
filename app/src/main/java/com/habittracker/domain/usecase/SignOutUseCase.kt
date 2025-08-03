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
        return authRepository.signOut()
    }
}
