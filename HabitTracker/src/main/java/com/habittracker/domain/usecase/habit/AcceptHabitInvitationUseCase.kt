package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.isExpired
import com.habittracker.domain.model.isPending
import com.habittracker.domain.usecase.GetHabitByIdUseCase
import com.habittracker.domain.usecase.ScheduleHabitReminderUseCase
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.repository.SocialRepository
import javax.inject.Inject
import timber.log.Timber

/**
 * Use case для принятия приглашения в групповую привычку
 */
class AcceptHabitInvitationUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val getHabitByIdUseCase: GetHabitByIdUseCase,
    private val scheduleHabitReminderUseCase: ScheduleHabitReminderUseCase,
    private val participantSettingsRepository: ParticipantSettingsRepository
) {
    /**
     * Принимает приглашение в групповую привычку
     * @param invitationId ID приглашения
     * @param userId ID пользователя, принимающего приглашение
     * @return Result с обновленным приглашением или ошибкой
     */
    suspend operator fun invoke(
        invitationId: String,
        userId: String
    ): Result<HabitInvitation> {
        return try {
            // Получаем приглашение
            val invitation = socialRepository.getHabitInvitationById(invitationId)
                ?: return Result.failure(Exception("Приглашение не найдено"))

            // Проверяем, что приглашение адресовано этому пользователю
            if (invitation.toUserId != userId) {
                return Result.failure(Exception("Нет прав для принятия этого приглашения"))
            }

            // Проверяем статус приглашения
            if (!invitation.isPending()) {
                return Result.failure(Exception("Приглашение уже обработано или недействительно"))
            }

            // Проверяем, не истекло ли приглашение
            if (invitation.isExpired()) {
                return Result.failure(Exception("Приглашение истекло"))
            }

            // Проверяем, что пользователь существует
            val user = socialRepository.getUserById(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            // Принимаем приглашение через SocialRepository
            val result = socialRepository.acceptHabitInvitation(invitationId)
            // Побочные эффекты (добавление участника, пуш-уведомления) выполняются на уровне репозитория

            // Локально инициализируем персональные настройки и планируем напоминания для нового участника на этом устройстве
            result.onSuccess { accepted ->
                try {
                    val habitId = accepted.habitId
                    val habit = getHabitByIdUseCase(habitId)
                    if (habit != null && habit.reminderTime != null) {
                        // Инициализируем персональные настройки из текущей привычки
                        participantSettingsRepository.upsertDefaultsFromHabit(habit, userId)
                        Timber.tag("AcceptHabitInvitationUC").d("[reminder] scheduling for habit=$habitId")
                        scheduleHabitReminderUseCase(habit)
                    } else {
                        Timber.tag("AcceptHabitInvitationUC").d("[reminder] skip: habit null or no reminderTime for habit=$habitId")
                    }
                } catch (e: Exception) {
                    Timber.tag("AcceptHabitInvitationUC").w(e, "[reminder] failed to schedule: ${e.message}")
                }
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
