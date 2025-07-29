package com.habittracker.domain.usecase

import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для выполнения входа пользователя с помощью Google аккаунта.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Flow<Result<Unit>> {
        return repository.signInWithGoogle(idToken)
    }
}
