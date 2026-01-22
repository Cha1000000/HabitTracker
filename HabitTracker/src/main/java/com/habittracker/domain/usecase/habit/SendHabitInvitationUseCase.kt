package com.habittracker.domain.usecase.habit

import com.habittracker.R
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject
import timber.log.Timber

/**
 * Use case для отправки приглашения в групповую привычку
 */
class SendHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val habitRepository: HabitRepository,
    private val stringProvider: StringProvider
) {
    /**
     * Отправляет приглашение в групповую привычку
     * @param habitId ID привычки
     * @param fromUserId ID отправителя
     * @param toUserId ID получателя
     * @param message Сообщение (опционально)
     * @return Result с приглашением или ошибкой
     */
    suspend operator fun invoke(
        habitId: String,
        fromUserId: String,
        toUserId: String,
        message: String? = null
    ): Result<HabitInvitation> {
        return try {
            Timber.tag("SendHabitInvitationUC").d("[start] habitId=$habitId from=$fromUserId to=$toUserId")
            // Проверяем, что пользователь не приглашает сам себя
            if (fromUserId == toUserId) {
                Timber.tag("SendHabitInvitationUC").w("[validation] self-invite blocked")
                return Result.failure(Exception(stringProvider.getString(R.string.error_self_invite)))
            }

            // Получаем привычку для проверки прав и получения информации
            val habit = habitRepository.getHabitById(habitId)
                ?: run {
                    Timber.tag("SendHabitInvitationUC").w("[validation] habit not found: $habitId")
                    return Result.failure(Exception(stringProvider.getString(R.string.error_habit_not_found)))
                }

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                Timber.tag("SendHabitInvitationUC").w("[validation] habit inactive")
                return Result.failure(Exception(stringProvider.getString(R.string.error_invite_inactive_habit)))
            }

            // Проверяем, что отправитель является создателем привычки
            if (habit.createdBy != fromUserId) {
                Timber.tag("SendHabitInvitationUC").w("[validation] only creator can invite: creator=${habit.createdBy}")
                return Result.failure(Exception(stringProvider.getString(R.string.error_only_creator_can_invite)))
            }

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                Timber.tag("SendHabitInvitationUC").w("[validation] not a group habit")
                return Result.failure(Exception(stringProvider.getString(R.string.error_invite_group_only)))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                Timber.tag("SendHabitInvitationUC").w("[validation] no free slots: max=${habit.maxParticipants}")
                return Result.failure(Exception(stringProvider.getString(R.string.error_no_free_slots_limit, habit.maxParticipants)))
            }

            // Проверяем, что пользователь еще не участвует в привычке
            if (habit.isUserParticipant(toUserId)) {
                Timber.tag("SendHabitInvitationUC").w("[validation] user already participant: $toUserId")
                return Result.failure(Exception(stringProvider.getString(R.string.error_user_already_participant)))
            }

            // Проверяем, что получатель существует
            val toUser = socialRepository.getUserById(toUserId)
                ?: run {
                    Timber.tag("SendHabitInvitationUC").w("[validation] recipient not found: $toUserId")
                    return Result.failure(Exception(stringProvider.getString(R.string.error_user_not_found)))
                }

            // Проверяем дружеские связи (можно приглашать только друзей)
            val areFriends = socialRepository.areFriends(fromUserId, toUserId)
            if (!areFriends) {
                Timber.tag("SendHabitInvitationUC").w("[validation] not friends: from=$fromUserId to=$toUserId")
                return Result.failure(Exception(stringProvider.getString(R.string.error_invite_friends_only)))
            }

            // Проверяем, нет ли уже активного приглашения для этого пользователя в эту привычку
            val existingInvitation = socialRepository.getPendingHabitInvitation(habitId, toUserId)
            if (existingInvitation != null) {
                Timber.tag("SendHabitInvitationUC").w("[validation] invitation already exists: habit=$habitId to=$toUserId")
                return Result.failure(Exception(stringProvider.getString(R.string.error_invitation_already_exists)))
            }

            // Проверяем лимит активных исходящих приглашений от пользователя (максимум 10)
            val activeInvitationsCount = socialRepository.getActiveOutgoingInvitationsCount(fromUserId)
            if (activeInvitationsCount >= 10) {
                Timber.tag("SendHabitInvitationUC").w("[validation] outgoing limit reached: $activeInvitationsCount")
                return Result.failure(Exception(stringProvider.getString(R.string.error_outgoing_invites_limit)))
            }

            // Получаем информацию об отправителе
            val fromUser = socialRepository.getUserById(fromUserId)
                ?: run {
                    Timber.tag("SendHabitInvitationUC").w("[validation] sender not found: $fromUserId")
                    return Result.failure(Exception(stringProvider.getString(R.string.error_sender_not_found)))
                }

            // Отправляем приглашение через SocialRepository
            Timber.tag("SendHabitInvitationUC").d("[repository] sendHabitInvitation() -> habit=${habitId} to=${toUserId}")
            val result = socialRepository.sendHabitInvitation(
                habitId = habitId,
                habitTitle = habit.title,
                fromUserId = fromUserId,
                fromUserName = fromUser.name,
                toUserId = toUserId,
                message = message
            )
            if (result.isSuccess) {
                Timber.tag("SendHabitInvitationUC").d("[repository] SUCCESS habit=${habitId} to=${toUserId}")
            } else {
                Timber.tag("SendHabitInvitationUC").w("[repository] FAILURE habit=${habitId} to=${toUserId}: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            Timber.tag("SendHabitInvitationUC").w(e, "[error] ${e.message}")
            Result.failure(e)
        }
    }
}
