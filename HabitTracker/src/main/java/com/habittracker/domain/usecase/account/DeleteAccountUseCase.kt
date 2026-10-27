package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.AccountAlreadyDeletedException
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
 * Данные на устройстве стираются **только после** успешного удаления в облаке и в Firebase Auth:
 * при любой ошибке раньше пользователь остаётся в аккаунте со своими данными (включая
 * неотправленную очередь синхронизации) и может повторить. Повтор безопасен — облачные шаги
 * идемпотентны.
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
            deleteEverything()
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
            accountRepository.deleteRemoteUserData(userId)
            // С этого места отмена оставила бы устройство с данными удалённого аккаунта
            withContext(NonCancellable) {
                authRepository.deleteCurrentAuthUser()
                accountRepository.wipeLocalData()
            }
        } finally {
            // После стирания очередь пуста; при ошибке — отправит накопленное
            accountRepository.resumeSync()
        }
    }
}
