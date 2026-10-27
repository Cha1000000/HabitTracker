package com.habittracker.domain.usecase.account

import com.habittracker.domain.model.AccountAlreadyDeletedException
import com.habittracker.domain.model.NoConnectionException
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Облако уже чистое: удаляем учётку в Auth и стираем устройство. Если Auth не удалось (нет сети,
 * устарел вход), ошибка уходит наверх, а устройство не стирается: пользователь в аккаунте и
 * может повторить — отметка `CLOUD_DONE` направит повтор сразу сюда, минуя облако.
 *
 * @throws NoConnectionException сервер Auth не ответил вовремя.
 */
internal suspend fun finishDeletionAfterCloud(
    authRepository: AuthRepository,
    accountRepository: AccountRepository
) {
    try {
        withTimeout(AUTH_DELETE_TIMEOUT_MS) { authRepository.deleteCurrentAuthUser() }
    } catch (e: AccountAlreadyDeletedException) {
        // Прошлая попытка удалила учётку, но ответ не дошёл — осталось только устройство
        Timber.tag(TAG).w(e, "Auth user already deleted")
    } catch (e: TimeoutCancellationException) {
        // Наследник CancellationException, но это сбой сети, а не отмена
        throw NoConnectionException(e)
    }
    accountRepository.wipeLocalData()
}

private const val TAG = "AccountDeletion"
private const val AUTH_DELETE_TIMEOUT_MS = 15_000L
