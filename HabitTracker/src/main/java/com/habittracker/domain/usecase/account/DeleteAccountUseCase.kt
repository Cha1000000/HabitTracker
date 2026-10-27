package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.AccountAlreadyDeletedException
import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.NotSignedInException
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Полностью удаляет аккаунт текущего пользователя.
 *
 * Данные на устройстве стираются **только после** удаления в облаке и в Auth: при ошибке раньше
 * пользователь остаётся в аккаунте со своими данными (включая неотправленную очередь
 * синхронизации) и может повторить. Повтор безопасен — облачные шаги идемпотентны, а если облако
 * уже чистое (отметка `CLOUD_DONE`), повтор сразу удаляет учётку в Auth.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(reauth: ReauthRequest): Result<Unit> {
        if (authRepository.getCurrentUserId() == null) {
            return Result.failure(NotSignedInException())
        }
        return try {
            // Повторный вход первым: без него Auth не даст удалить аккаунт, а заодно проверяем сеть
            authRepository.reauthenticate(reauth)
            if (isCloudAlreadyDeleted()) {
                withContext(NonCancellable) { finishDeletionAfterCloud(authRepository, accountRepository) }
            } else {
                deleteEverything()
            }
            Result.success(Unit)
        } catch (e: AccountAlreadyDeletedException) {
            // Прошлая попытка удалила аккаунт, но ответ не дошёл — облако уже чистое, доделываем устройство
            withContext(NonCancellable) { accountRepository.wipeLocalData() }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            // Наследник CancellationException, но это ошибка операции, а не отмена
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteEverything() {
        val userId = checkNotNull(authRepository.getCurrentUserId()) { "No signed-in user" }
        accountRepository.pauseSync()
        try {
            // Отметка переживёт убийство процесса: на старте удаление доведут или отменят, а до
            // тех пор очередь не отправит в облако то, что мы сейчас удаляем
            accountRepository.markDeletion(userId, DeletionStage.CLOUD_IN_PROGRESS)
            try {
                accountRepository.deleteRemoteUserData(userId)
            } catch (e: CancellationException) {
                // Экран закрыли посреди удаления — как «Назад»: иначе отметка держала бы синк
                // остановленным до следующего запуска
                if (e !is TimeoutCancellationException) accountRepository.cancelPendingDeletion()
                throw e
            }
            // С этого места отмена оставила бы устройство с данными удалённого аккаунта
            withContext(NonCancellable) {
                accountRepository.markDeletion(userId, DeletionStage.CLOUD_DONE)
                finishDeletionAfterCloud(authRepository, accountRepository)
            }
        } finally {
            // После стирания очередь пуста. При ошибке её держит отметка, пока пользователь не
            // повторит удаление или не откажется от него
            accountRepository.resumeSync()
        }
    }

    private fun isCloudAlreadyDeleted(): Boolean {
        val pending = accountRepository.pendingDeletion() ?: return false
        return pending.stage == DeletionStage.CLOUD_DONE && pending.userId == authRepository.getCurrentUserId()
    }
}
