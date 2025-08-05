package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case для получения приглашений в привычки
 */
class GetHabitInvitationsUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    /**
     * Получает входящие приглашения в привычки для пользователя
     * @param userId ID пользователя
     * @return Flow со списком входящих приглашений
     */
    fun getIncomingInvitations(userId: String): Flow<List<HabitInvitation>> {
        return socialRepository.getIncomingHabitInvitations(userId)
    }

    /**
     * Получает исходящие приглашения в привычки от пользователя
     * @param userId ID пользователя
     * @return Flow со списком исходящих приглашений
     */
    fun getOutgoingInvitations(userId: String): Flow<List<HabitInvitation>> {
        return socialRepository.getOutgoingHabitInvitations(userId)
    }

    /**
     * Получает все приглашения для конкретной привычки
     * @param habitId ID привычки
     * @return Flow со списком приглашений для привычки
     */
    fun getHabitInvitations(habitId: String): Flow<List<HabitInvitation>> {
        return socialRepository.getHabitInvitations(habitId)
    }

    /**
     * Получает количество ожидающих приглашений для пользователя
     * @param userId ID пользователя
     * @return Flow с количеством ожидающих приглашений
     */
    fun getPendingInvitationsCount(userId: String): Flow<Int> {
        return socialRepository.getPendingHabitInvitationsCount(userId)
    }

    /**
     * Получает конкретное приглашение по ID
     * @param invitationId ID приглашения
     * @return Приглашение или null, если не найдено
     */
    suspend fun getInvitationById(invitationId: String): HabitInvitation? {
        return socialRepository.getHabitInvitationById(invitationId)
    }
}
