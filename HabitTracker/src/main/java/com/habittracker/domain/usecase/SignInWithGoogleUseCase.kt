package com.habittracker.domain.usecase

import android.content.Context
import android.content.Intent
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для выполнения входа пользователя с помощью Google аккаунта через Credential Manager API.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(activityContext: Context): Flow<Result<Intent?>> {
        return repository.signInWithGoogle(activityContext)
    }
}
