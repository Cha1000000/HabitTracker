package com.habittracker.domain.usecase.habit

import android.util.Log
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.getParticipantsCount
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.repository.ParticipantSettingsRepository
import kotlinx.datetime.Clock
import javax.inject.Inject

/**
 * Use case для присоединения к групповой привычке по коду приглашения
 */
class JoinHabitByCodeUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository,
    private val generateInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase,
    private val participantSettingsRepository: ParticipantSettingsRepository
) {
    
    /**
     * Присоединяется к групповой привычке по коду приглашения
     * @param inviteCode Код приглашения
     * @param userId ID пользователя, который хочет присоединиться
     * @return Result с привычкой или ошибкой
     */
    suspend operator fun invoke(
        inviteCode: String,
        userId: String
    ): Result<Habit> {
        return try {
            // Проверяем формат кода
            if (!generateInviteCodeUseCase.isValidInviteCodeFormat(inviteCode)) {
                return Result.failure(Exception("Неверный формат кода приглашения"))
            }

            // Ищем привычку по коду
            val habit = habitRepository.getHabitByInviteCode(inviteCode)
                ?: return Result.failure(Exception("Привычка с таким кодом не найдена или неактивна"))

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                return Result.failure(Exception("Код приглашения действует только для групповых привычек"))
            }

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                return Result.failure(Exception("Привычка неактивна"))
            }

            // Проверяем, что привычка публичная (доступна по коду)
            if (!habit.isPublic || habit.inviteCode != inviteCode) {
                return Result.failure(Exception("Код приглашения недействителен"))
            }

            // Проверяем, что пользователь еще не участвует в привычке
            if (habit.isUserParticipant(userId)) {
                return Result.failure(Exception("Вы уже участвуете в этой привычке"))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                return Result.failure(Exception("В привычке нет свободных мест (максимум ${habit.maxParticipants} участников)"))
            }

            // Проверяем, что пользователь существует
            val user = socialRepository.getUserById(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            // Проверяем дружеские связи с создателем (опционально, можно убрать для публичных привычек)
            val areFriends = socialRepository.areFriends(userId, habit.createdBy)
            if (!areFriends) {
                // Для публичных привычек можно разрешить присоединение без дружеских связей
                // Но отправим уведомление создателю о новом участнике
            }

            // Добавляем пользователя к участникам привычки
            habitRepository.addParticipantToHabit(habit.id, userId)

            // Инициализируем персональные настройки и локально планируем напоминания для нового участника на этом устройстве
            try {
                if (habit.reminderTime != null) {
                    participantSettingsRepository.upsertDefaultsFromHabit(habit, userId)
                    Log.d("JoinHabitByCodeUC", "[reminder] scheduling for habit=${habit.id}")
                    scheduleHabitReminderUseCase(habit)
                } else {
                    Log.d("JoinHabitByCodeUC", "[reminder] skip: no reminderTime for habit=${habit.id}")
                }
            } catch (e: Exception) {
                Log.w("JoinHabitByCodeUC", "[reminder] failed to schedule: ${e.message}", e)
            }

            // Отправляем уведомление создателю привычки
            try {
                socialRepository.sendHabitInvitationAcceptedNotification(
                    toUserId = habit.createdBy,
                    acceptedByName = user.name,
                    habitTitle = habit.title
                )
            } catch (e: Exception) {
                // Игнорируем ошибки уведомлений
            }

            // Возвращаем обновленную привычку
            val updatedHabit = habitRepository.getHabitById(habit.id)
                ?: habit // Fallback на исходную привычку

            Result.success(updatedHabit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Проверяет валидность кода приглашения без присоединения
     * @param inviteCode Код приглашения
     * @return Result с информацией о привычке или ошибкой
     */
    suspend fun validateInviteCode(inviteCode: String): Result<HabitPreview> {
        return try {
            if (!generateInviteCodeUseCase.isValidInviteCodeFormat(inviteCode)) {
                return Result.failure(Exception("Неверный формат кода приглашения"))
            }

            val habit = habitRepository.getHabitByInviteCode(inviteCode)
                ?: return Result.failure(Exception("Привычка с таким кодом не найдена"))

            if (!habit.isActive || !habit.isPublic) {
                return Result.failure(Exception("Код приглашения недействителен"))
            }

            val creator = socialRepository.getUserById(habit.createdBy)
            val preview = HabitPreview(
                id = habit.id,
                title = habit.title,
                description = habit.description,
                icon = habit.icon,
                color = habit.color,
                creatorName = creator?.name ?: "Неизвестный пользователь",
                participantsCount = habit.getParticipantsCount(),
                maxParticipants = habit.maxParticipants,
                hasAvailableSlots = habit.canAddParticipants()
            )

            Result.success(preview)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Модель для предварительного просмотра привычки по коду приглашения
 */
data class HabitPreview(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
    val creatorName: String,
    val participantsCount: Int,
    val maxParticipants: Int,
    val hasAvailableSlots: Boolean
)
