package com.habittracker.domain.usecase

import android.util.Log
import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.util.SyncPayloads
import kotlinx.datetime.Clock
import javax.inject.Inject

/**
 * Use case для удаления привычки.
 * Room-first + Outbox:
 * Если PERSONAL:
 *   1) Удаляем локально (Room)
 *   2) Ставит DELETE в Outbox через SyncManager
 * Если GROUP:
 *   — Пользователь «выходит» из привычки: удаляем его из участников.
 *   — Если создатель выходит, перевешиваем createdBy на первого участника (если есть).
 *   — Если после выхода остаётся 1 участник — меняем тип на PERSONAL и очищаем participants.
 *   — Если участников не остаётся вовсе — удаляем привычку целиком (как PERSONAL).
 *   — В остальных случаях — сохраняем GROUP и обновляем participants/createdBy.
 * 3) Форсит one-time sync и пытается асинхронно дренировать очередь
 * Ошибки Firestore/Outbox не пробрасываются в UI (не пугаем пользователя),
 * локальные ошибки Room — пробрасываем, т.к. операция реально не завершилась.
 */
class DeleteHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val firestoreRepository: FirestoreRepository,
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val cancelHabitReminderUseCase: CancelHabitReminderUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase
) {
    // Decision policy for GROUP habits — pure domain logic without side-effects
    internal sealed class Decision {
        data object DeleteAll : Decision()
        data class ConvertToPersonal(val newCreatedBy: String) : Decision()
        data class UpdateGroup(val newCreatedBy: String, val participants: List<String>) : Decision()
    }

    private suspend fun enqueueSafeDeleteAndKick(habitId: String, notBeforeMillis: Long) {
        try {
            syncManager.enqueueSafeDelete(
                entityType = "habit",
                entityId = habitId,
                notBeforeMillis = notBeforeMillis,
                maxRetries = 3,
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w("DeleteHabitUseCase", "enqueueSafeDelete failed: ${e.message}", e)
        } finally {
            // Запускаем немедленную синхронизацию (проигнорирует DELETE до notBefore),
            // а также планируем отложенную, чтобы гарантированно обработать DELETE после notBefore
            kickSync()
            val delay = notBeforeMillis - System.currentTimeMillis()
            try { syncWorkScheduler.scheduleOneTimeSyncDelayed(delay) } catch (e: Exception) {
                Log.w("DeleteHabitUseCase", "scheduleOneTimeSyncDelayed failed: ${e.message}", e)
            }
        }
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
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        val habit: Habit = habitRepository.getHabitById(habitId) ?: return

        // На текущем устройстве больше не должно быть локальных уведомлений по этой привычке
        try { cancelHabitReminderUseCase(habitId) } catch (e: Exception) {
            Log.w("DeleteHabitUseCase", "cancelHabitReminder failed: ${e.message}", e)
        }

        if (habit.type == HabitType.PERSONAL) {
            habitRepository.deleteHabit(habitId)
            
            try {
                firestoreRepository.deleteHabit(habitId)
            } catch (e: Exception) {
                Log.w("DeleteHabitUseCase", "Firestore delete failed, using fallback: ${e.message}")
                try {
                    syncManager.enqueueDelete(
                        entityType = "habit",
                        entityId = habitId,
                        updatedAt = nowMillis
                    )
                    kickSync()
                } catch (e2: Exception) {
                    Log.e("DeleteHabitUseCase", "Fallback enqueue failed: ${e2.message}")
                }
            }
            return
        }

        // GROUP: пользователь покидает привычку согласно политике решений
        when (val decision = decideGroupDeletion(habit, userId)) {
            is Decision.DeleteAll -> {
                habitRepository.deleteHabit(habitId)
                
                try {
                    firestoreRepository.deleteHabit(habitId)
                } catch (e: Exception) {
                    Log.w("DeleteHabitUseCase", "Firestore delete failed: ${e.message}")
                    // Fallback: добавляем в очередь
                    try {
                        syncManager.enqueueDelete(
                            entityType = "habit",
                            entityId = habitId,
                            updatedAt = nowMillis
                        )
                        kickSync()
                    } catch (e2: Exception) {
                        Log.e("DeleteHabitUseCase", "Fallback enqueue failed: ${e2.message}")
                    }
                }
            }
            is Decision.ConvertToPersonal -> {
                val updatedHabit = habit.copy(
                    type = HabitType.PERSONAL,
                    participants = emptyList(),
                    createdBy = decision.newCreatedBy,
                    updatedAt = nowMillis
                )
                habitRepository.updateHabit(updatedHabit)

                val payload = SyncPayloads.habitPartialUpdateToJson(
                    participants = emptyList(),
                    type = HabitType.PERSONAL,
                    createdBy = decision.newCreatedBy,
                    updatedAt = nowMillis
                )
                enqueueUpsertAndKickSync(habitId, payload, nowMillis)
            }
            is Decision.UpdateGroup -> {
                // Проверяем, покидает ли текущий пользователь группу
                val isUserLeaving = (userId == habit.createdBy && decision.newCreatedBy != userId) || 
                                    (userId != habit.createdBy && habit.participants.contains(userId))
                
                if (isUserLeaving) {
                    // Пользователь покидает группу - обновляем привычку, убирая его из participants
                    // Это предотвращает race condition с Firestore sync, которая может вернуть привычку обратно
                    val updatedHabit = habit.copy(
                        participants = decision.participants,
                        createdBy = decision.newCreatedBy,
                        updatedAt = nowMillis
                    )
                    // Используем UpdateHabitUseCase для атомарного обновления Firestore + Room
                    updateHabitUseCase(updatedHabit)
                    // Не вызываем enqueueUpsertAndKickSync, т.к. UpdateHabitUseCase уже обновил Firestore
                    return
                } else {
                    // Обновляем привычку для других участников
                    val updatedHabit = habit.copy(
                        type = HabitType.GROUP,
                        participants = decision.participants,
                        createdBy = decision.newCreatedBy,
                        updatedAt = nowMillis
                    )
                    habitRepository.updateHabit(updatedHabit)
                }

                // В Firestore всегда обновляем список участников (убираем покидающего пользователя)
                val payload = SyncPayloads.habitPartialUpdateToJson(
                    participants = decision.participants,
                    type = null, // тип остаётся GROUP — не шлём лишнее
                    createdBy = if (decision.newCreatedBy != habit.createdBy) decision.newCreatedBy else null,
                    updatedAt = nowMillis
                )
                enqueueUpsertAndKickSync(habitId, payload, nowMillis)
            }
        }
    }

    private suspend fun enqueueUpsertAndKickSync(habitId: String, payloadJson: String, nowMillis: Long) {
        try {
            syncManager.enqueueUpsert(
                entityType = "habit",
                entityId = habitId,
                payloadJson = payloadJson,
                updatedAt = nowMillis
            )
        } catch (e: Exception) {
            Log.w("DeleteHabitUseCase", "enqueueUpsert failed: ${e.message}", e)
        } finally {
            kickSync()
        }
    }

    private suspend fun kickSync() {
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (e: Exception) {
            Log.w("DeleteHabitUseCase", "scheduleOneTimeSync failed: ${e.message}", e)
        }
        try { syncManager.drainPending() } catch (e: Exception) {
            Log.w("DeleteHabitUseCase", "drainPending failed: ${e.message}", e)
        }
    }
}
