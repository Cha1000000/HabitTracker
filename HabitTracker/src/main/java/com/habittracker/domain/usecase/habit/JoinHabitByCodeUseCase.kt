package com.habittracker.domain.usecase.habit

import android.util.Log
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.provider.StringProvider
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.usecase.notification.SaveParticipantJoinedNotificationUseCase
import javax.inject.Inject

/**
 * Use case для присоединения к групповой привычке по коду приглашения
 */
class JoinHabitByCodeUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository,
    private val generateInviteCodeUseCase: GenerateHabitInviteCodeUseCase,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase,
    private val participantSettingsRepository: ParticipantSettingsRepository,
    private val saveParticipantJoinedNotificationUseCase: SaveParticipantJoinedNotificationUseCase,
    private val stringProvider: StringProvider
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
                return Result.failure(Exception(stringProvider.getString(R.string.error_invalid_code_format)))
            }

            // Ищем привычку по коду
            val habit = habitRepository.getHabitByInviteCode(inviteCode)
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_habit_not_found_or_inactive)))

            // Проверяем, что привычка групповая
            if (!habit.isGroupHabit()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_code_group_only)))
            }

            // Проверяем, что привычка активна
            if (!habit.isActive) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_habit_inactive)))
            }

            // Проверяем, что привычка публичная (доступна по коду)
            if (!habit.isPublic || habit.inviteCode != inviteCode) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_code_invalid)))
            }

            // Проверяем, что пользователь еще не участвует в привычке
            if (habit.isUserParticipant(userId)) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_already_participant)))
            }

            // Проверяем, что есть свободные места
            if (!habit.canAddParticipants()) {
                return Result.failure(Exception(stringProvider.getString(R.string.error_no_free_slots_limit, habit.maxParticipants)))
            }

            // Проверяем, что пользователь существует
            val user = socialRepository.getUserById(userId)
                ?: return Result.failure(Exception(stringProvider.getString(R.string.error_user_not_found)))

            // Добавляем пользователя к участникам привычки и синхронизируем с Firebase
            val updatedHabit = habit.copy(
                participants = habit.participants + userId
            )
            
            // Обновляем через репозиторий (локально + Outbox sync)
            habitRepository.updateHabit(updatedHabit)

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

            // Сохраняем уведомления для всех участников
            try {
                saveParticipantJoinedNotificationUseCase(
                    habitId = habit.id,
                    habitTitle = habit.title,
                    joinedUserId = userId,
                    joinedUserName = user.name,
                    participantIds = habit.participants
                )
            } catch (e: Exception) {
                Log.e("JoinHabitByCodeUC", "Failed to save join notifications: ${e.message}", e)
            }

            // Возвращаем обновленную привычку из базы данных
            val finalHabit = habitRepository.getHabitById(habit.id)
                ?: updatedHabit // Fallback на обновленную привычку

            Result.success(finalHabit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
