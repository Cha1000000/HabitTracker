package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/** Что делать на старте после прерванного удаления аккаунта. */
enum class PendingDeletionOutcome {
    /** Незавершённого удаления нет — обычный запуск. */
    NONE,

    /** Удаление доведено до конца, устройство стёрто — показываем вход. */
    FINISHED,

    /**
     * Удаление не закончено — пользователь решает на экране удаления. Облако удалено не до конца:
     * закончить или передумать; облако уже чистое: подтвердить вход, чтобы удалить учётку.
     */
    NEEDS_CONFIRMATION
}

/**
 * Доводит до конца удаление аккаунта, прерванное убийством процесса. Вызывается на старте,
 * до выбора первого экрана.
 */
class ResolvePendingAccountDeletionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(): PendingDeletionOutcome {
        val pending = accountRepository.pendingDeletion() ?: return PendingDeletionOutcome.NONE
        val userId = authRepository.getCurrentUserId()
        if (pending.stage == DeletionStage.CLOUD_DONE && userId != pending.userId) {
            // Облако уже чистое, а на устройстве остались данные удалённого аккаунта — стираем.
            // Сюда попадаем, только если прошлое стирание сорвалось
            Timber.tag(TAG).w("Device still holds data of a deleted account, wiping")
            accountRepository.wipeLocalData()
            return PendingDeletionOutcome.FINISHED
        }
        if (userId == null || userId != pending.userId) {
            // Удалять в облаке нечем: сессии нет или она чужая (отметка осталась от сбоя). Ничего не трогаем
            Timber.tag(TAG).w("No session of the deleting user, marker dropped")
            accountRepository.cancelPendingDeletion()
            return PendingDeletionOutcome.NONE
        }
        if (pending.stage == DeletionStage.CLOUD_IN_PROGRESS) {
            if (!authRepository.isCurrentUserDeleted()) return PendingDeletionOutcome.NEEDS_CONFIRMATION
            // Учётку удалили в другом месте — облака с этой стороны уже не коснуться
            Timber.tag(TAG).w("Account deleted elsewhere during cloud deletion, wiping")
            accountRepository.wipeLocalData()
            return PendingDeletionOutcome.FINISHED
        }
        return try {
            finishDeletionAfterCloud(authRepository, accountRepository)
            PendingDeletionOutcome.FINISHED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Нет сети или вход устарел — пользователь подтвердит на экране удаления
            Timber.tag(TAG).w(e, "Failed to finish deletion after cloud")
            PendingDeletionOutcome.NEEDS_CONFIRMATION
        }
    }

    private companion object {
        const val TAG = "ResolvePendingDeletion"
    }
}
