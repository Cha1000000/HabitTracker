package com.habittracker.domain.usecase.notification

import android.util.Log
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для отправки уведомлений о присоединении участника к привычке
 */
class SaveParticipantJoinedNotificationUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    companion object {
        private const val TAG = "SaveParticipantJoinedNotificationUC"
    }

    /**
     * Отправляет push-уведомления о присоединении участника всем текущим участникам привычки
     * @param habitId ID привычки
     * @param habitTitle Название привычки
     * @param joinedUserId ID присоединившегося пользователя
     * @param joinedUserName Имя присоединившегося пользователя
     * @param participantIds Список ID всех участников
     */
    suspend operator fun invoke(
        habitId: String,
        habitTitle: String,
        joinedUserId: String,
        joinedUserName: String,
        participantIds: List<String>
    ): Result<Unit> {
        return try {
            // Отправляем push-уведомления всем участникам (кроме присоединившегося)
            participantIds.forEach { participantId ->
                if (participantId != joinedUserId) {
                    try {
                        socialRepository.sendParticipantJoinedNotification(
                            toUserId = participantId,
                            joinedUserName = joinedUserName,
                            habitTitle = habitTitle,
                            habitId = habitId
                        )
                        Log.d(TAG, "Push-уведомление отправлено: userId=$participantId, joinedUser=$joinedUserName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки push для userId=$participantId", e)
                    }
                }
            }
            
            Log.d(TAG, "Push-уведомления о присоединении отправлены для ${participantIds.size - 1} участников")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки уведомлений о присоединении", e)
            Result.failure(e)
        }
    }
}
