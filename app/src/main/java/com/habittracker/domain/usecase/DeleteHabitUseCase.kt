package com.habittracker.domain.usecase

import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.util.SyncPayloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    private val syncManager: SyncManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val coroutineScope: CoroutineScope
) {
    private val backgroundTasksScope = coroutineScope

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
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        val habit: Habit = habitRepository.getHabitById(habitId)
            ?: run { return } // Уже удалена локально — ничего не делаем

        if (habit.type == HabitType.PERSONAL) {
            // Локально удаляем, в Firestore — мягкое удаление (isActive=false)
            habitRepository.deleteHabit(habitId)
            val payload = SyncPayloads.habitPartialUpdateToJson(
                isActive = false,
                updatedAt = nowMillis
            )
            enqueueUpsertAndKickSync(habitId, payload, nowMillis)
            return
        }

        // GROUP: пользователь покидает привычку согласно политике решений
        when (val decision = decideGroupDeletion(habit, userId)) {
            is Decision.DeleteAll -> {
                // Никого не осталось — локально удаляем, в Firestore делаем soft-delete
                habitRepository.deleteHabit(habitId)
                val payload = SyncPayloads.habitPartialUpdateToJson(
                    isActive = false,
                    updatedAt = nowMillis
                )
                enqueueUpsertAndKickSync(habitId, payload, nowMillis)
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
                val updatedHabit = habit.copy(
                    type = HabitType.GROUP,
                    participants = decision.participants,
                    createdBy = decision.newCreatedBy,
                    updatedAt = nowMillis
                )
                habitRepository.updateHabit(updatedHabit)

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

    private suspend fun enqueueDeleteAndKickSync(habitId: String, nowMillis: Long) {
        try {
            syncManager.enqueueDelete(
                entityType = "habit",
                entityId = habitId,
                updatedAt = nowMillis
            )
        } catch (_: Exception) {
        } finally {
            kickSync()
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
        } catch (_: Exception) {
        } finally {
            kickSync()
        }
    }

    private fun kickSync() {
        try { syncWorkScheduler.scheduleOneTimeSync() } catch (_: Exception) { }
        backgroundTasksScope.launch {
            try { syncManager.drainPending() } catch (_: Exception) { }
        }
    }
}
