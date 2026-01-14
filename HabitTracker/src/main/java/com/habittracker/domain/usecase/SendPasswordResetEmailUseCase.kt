package com.habittracker.domain.usecase

import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для отправки письма сброса пароля на указанный email.
 */
class SendPasswordResetEmailUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String): Flow<Result<Unit>> {
        return repository.sendPasswordResetEmail(email)
    }
}
