package com.habittracker.domain.usecase.auth

import com.habittracker.domain.model.User
import com.habittracker.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Обновляет/создает пользователя локально и в Firestore. Опционально добавляет FCM токен.
 * Room-first для мгновенного UI, Firestore merge для консистентности.
 */
class UpsertSignedInUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(user: User, fcmToken: String? = null) {
        userRepository.upsertUserRemoteAndLocal(user, fcmToken)
    }
}
