package com.habittracker.domain.usecase.habit

import android.util.Log
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject

/**
 * Use case для отправки приглашения в групповую привычку
 */
class SendHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val habitRepository: HabitRepository
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
            Log.d("SendHabitInvitationUC", "[start] habitId=$habitId from=$fromUserId to=$toUserId")
            // Проверяем, что пользователь не приглашает сам себя
            if (fromUserId == toUserId) {
                Log.w("SendHabitInvitationUC", "[validation] self-invite blocked")
                return Result.failure(Exception("Нельзя пригласить самого себя"))
            }

            // Получаем привычку для проверки прав и получения информации
            val habit = habitRepository.getHabitById(habitId)
                ?: run {
                    Log.w("SendHabitInvitationUC", "[validation] habit not found: $habitId")
                    return Result.failure(Exception("Привычка не найдена"))
                }

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                Log.w("SendHabitInvitationUC", "[validation] habit inactive")
                return Result.failure(Exception("Нельзя приглашать в неактивную привычку"))
            }

            // Проверяем, что отправитель является создателем привычки
            if (habit.createdBy != fromUserId) {
                Log.w("SendHabitInvitationUC", "[validation] only creator can invite: creator=${habit.createdBy}")
                return Result.failure(Exception("Только создатель привычки может отправлять приглашения"))
            }

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                Log.w("SendHabitInvitationUC", "[validation] not a group habit")
                return Result.failure(Exception("Можно приглашать только в групповые привычки"))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                Log.w("SendHabitInvitationUC", "[validation] no free slots: max=${habit.maxParticipants}")
                return Result.failure(Exception("В привычке нет свободных мест (максимум ${habit.maxParticipants} участников)"))
            }

            // Проверяем, что пользователь еще не участвует в привычке
            if (habit.isUserParticipant(toUserId)) {
                Log.w("SendHabitInvitationUC", "[validation] user already participant: $toUserId")
                return Result.failure(Exception("Пользователь уже участвует в этой привычке"))
            }

            // Проверяем, что получатель существует
            val toUser = socialRepository.getUserById(toUserId)
                ?: run {
                    Log.w("SendHabitInvitationUC", "[validation] recipient not found: $toUserId")
                    return Result.failure(Exception("Пользователь не найден"))
                }

            // Проверяем дружеские связи (можно приглашать только друзей)
            val areFriends = socialRepository.areFriends(fromUserId, toUserId)
            if (!areFriends) {
                Log.w("SendHabitInvitationUC", "[validation] not friends: from=$fromUserId to=$toUserId")
                return Result.failure(Exception("Можно приглашать только друзей. Сначала добавьте пользователя в друзья"))
            }

            // Проверяем, нет ли уже активного приглашения для этого пользователя в эту привычку
            val existingInvitation = socialRepository.getPendingHabitInvitation(habitId, toUserId)
            if (existingInvitation != null) {
                Log.w("SendHabitInvitationUC", "[validation] invitation already exists: habit=$habitId to=$toUserId")
                return Result.failure(Exception("Пользователю уже отправлено приглашение в эту привычку"))
            }

            // Проверяем лимит активных исходящих приглашений от пользователя (максимум 10)
            val activeInvitationsCount = socialRepository.getActiveOutgoingInvitationsCount(fromUserId)
            if (activeInvitationsCount >= 10) {
                Log.w("SendHabitInvitationUC", "[validation] outgoing limit reached: $activeInvitationsCount")
                return Result.failure(Exception("Превышен лимит активных приглашений (максимум 10). Дождитесь ответа на предыдущие приглашения"))
            }

            // Получаем информацию об отправителе
            val fromUser = socialRepository.getUserById(fromUserId)
                ?: run {
                    Log.w("SendHabitInvitationUC", "[validation] sender not found: $fromUserId")
                    return Result.failure(Exception("Отправитель не найден"))
                }

            // Отправляем приглашение через SocialRepository
            Log.d("SendHabitInvitationUC", "[repository] sendHabitInvitation() -> habit=${habitId} to=${toUserId}")
            val result = socialRepository.sendHabitInvitation(
                habitId = habitId,
                habitTitle = habit.title,
                fromUserId = fromUserId,
                fromUserName = fromUser.name,
                toUserId = toUserId,
                message = message
            )
            if (result.isSuccess) {
                Log.d("SendHabitInvitationUC", "[repository] SUCCESS habit=${habitId} to=${toUserId}")
            } else {
                Log.w("SendHabitInvitationUC", "[repository] FAILURE habit=${habitId} to=${toUserId}: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            Log.w("SendHabitInvitationUC", "[error] ${e.message}", e)
            Result.failure(e)
        }
    }
}
