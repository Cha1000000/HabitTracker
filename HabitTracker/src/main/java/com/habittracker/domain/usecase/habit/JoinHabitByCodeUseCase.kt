package com.habittracker.domain.usecase.habit

import android.util.Log
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.canAddParticipants
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.model.isUserParticipant
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import javax.inject.Inject

/**
 * Use case для присоединения к групповой привычке по коду приглашения
 */
class JoinHabitByCodeUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val firestoreRepository: FirestoreRepository,
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

            // Добавляем пользователя к участникам привычки и синхронизируем с Firebase
            val updatedHabit = habit.copy(
                participants = habit.participants + userId
            )
            
            // Сначала обновляем в Firebase для синхронизации
            firestoreRepository.updateHabit(updatedHabit)
            
            // Затем обновляем локально
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

            // Отправляем уведомление создателю привычки
            try {
                socialRepository.sendHabitInvitationAcceptedNotification(
                    toUserId = habit.createdBy,
                    acceptedByName = user.name,
                    habitTitle = habit.title
                )
            } catch (e: Exception) {
                Log.e("JoinHabitByCodeUC", "Failed to send notification to creator: ${e.message}", e)
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
