package com.habittracker.domain.usecase

import android.content.Intent
import com.habittracker.domain.repository.AuthRepository
import javax.inject.Inject

class GetGoogleSignInIntentUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): Intent {
        return repository.getGoogleSignInIntent()
    }
}
