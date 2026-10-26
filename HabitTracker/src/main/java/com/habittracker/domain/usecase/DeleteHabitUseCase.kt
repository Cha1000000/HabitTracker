package com.habittracker.domain.usecase

import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import timber.log.Timber

/**
 * Use case для удаления привычки.
 * Delegate Outbox operations to HabitRepository.
 * Manages Group habit logic (leaving/deleting/transferring ownership).
 */
class DeleteHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val cancelHabitReminderUseCase: CancelHabitReminderUseCase,
    private val sendGroupHabitPushNotificationUseCase: com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
) {
    // Decision policy for GROUP habits — pure domain logic without side-effects
    internal sealed class Decision {
        data object DeleteAll : Decision()
        data class ConvertToPersonal(val newCreatedBy: String) : Decision()
        data class UpdateGroup(val newCreatedBy: String, val participants: List<String>) : Decision()
    }

    internal fun decideGroupDeletion(habit: Habit, userId: String): Decision {
        // Creator leaves
        if (userId == habit.createdBy) {
            val participants = habit.participants
            if (participants.isEmpty()) return Decision.DeleteAll

            val newCreatedBy = participants.first()
            val remainingParticipants = participants.drop(1)
            val remainingCount = 1 + remainingParticipants.size

            return if (remainingCount == 1) {
                Decision.ConvertToPersonal(newCreatedBy)
            } else {
                Decision.UpdateGroup(newCreatedBy, remainingParticipants)
            }
        }

        // Regular participant leaves
        val remainingParticipants = habit.participants.filter { it != userId }
        val remainingCount = 1 + remainingParticipants.size // 1 for createdBy
        return when {
            remainingCount <= 0 -> Decision.DeleteAll // defensive
            remainingCount == 1 -> Decision.ConvertToPersonal(habit.createdBy)
            else -> Decision.UpdateGroup(habit.createdBy, remainingParticipants)
        }
    }

    suspend operator fun invoke(habitId: String, userId: String) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val habit: Habit = habitRepository.getHabitById(habitId) ?: return

        // На текущем устройстве больше не должно быть локальных уведомлений по этой привычке
        try { cancelHabitReminderUseCase(habitId) } catch (e: Exception) {
            Timber.tag("DeleteHabitUseCase").w(e, "cancelHabitReminder failed: ${e.message}")
        }

        if (habit.type == HabitType.PERSONAL) {
            // Repository handles Local + Outbox
            habitRepository.deleteHabit(habitId)
            kickSync()
            return
        }

        // GROUP: пользователь покидает привычку согласно политике решений
        when (val decision = decideGroupDeletion(habit, userId)) {
            is Decision.DeleteAll -> {
                habitRepository.deleteHabit(habitId)
                kickSync()
            }
            is Decision.ConvertToPersonal -> {
                val updatedHabit = habit.copy(
                    type = HabitType.PERSONAL,
                    participants = emptyList(),
                    createdBy = decision.newCreatedBy,
                    updatedAt = today
                )
                habitRepository.updateHabit(updatedHabit)
                kickSync()
            }
            is Decision.UpdateGroup -> {
                // Проверяем, покидает ли текущий пользователь группу
                val isUserLeaving = (userId == habit.createdBy && decision.newCreatedBy != userId) || 
                                    (userId != habit.createdBy && habit.participants.contains(userId))
                
                // Обновляем привычку (меняем владельца, список участников)
                val updatedHabit = habit.copy(
                    type = HabitType.GROUP,
                    participants = decision.participants,
                    createdBy = decision.newCreatedBy,
                    updatedAt = today
                )
                
                // Используем репозиторий для обновления (Local + Outbox)
                habitRepository.updateHabit(updatedHabit)
                kickSync()
                
                if (isUserLeaving) {
                    // Отправляем уведомление оставшимся участникам о выходе пользователя
                    Timber.tag("DeleteHabitUseCase").d("User $userId is leaving habit ${habit.title}, sending notifications to remaining participants")
                    try {
                        val allRemainingParticipants = (listOf(decision.newCreatedBy) + decision.participants).distinct()
                        Timber.tag("DeleteHabitUseCase").d("Remaining participants: $allRemainingParticipants")
                        sendGroupHabitPushNotificationUseCase.sendParticipantLeftNotification(
                            habitId = habitId,
                            leftUserId = userId,
                            remainingParticipants = allRemainingParticipants
                        )
                    } catch (e: Exception) {
                        Timber.tag("DeleteHabitUseCase").w("Failed to send participant left notification: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun kickSync() {
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (e: Exception) {
            Timber.tag("DeleteHabitUseCase").w(e, "scheduleOneTimeSync failed: ${e.message}")
        }
        try { syncManager.drainPending() } catch (e: Exception) {
            Timber.tag("DeleteHabitUseCase").w(e, "drainPending failed: ${e.message}")
        }
    }
}
