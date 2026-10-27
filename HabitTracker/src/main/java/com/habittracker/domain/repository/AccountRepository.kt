package com.habittracker.domain.repository

import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.PendingAccountDeletion
import kotlinx.coroutines.flow.Flow

/**
 * Операции над учётной записью целиком (в отличие от [AuthRepository], который отвечает за вход).
 */
interface AccountRepository {

    /**
     * Останавливает отправку очереди синхронизации, чтобы она не вернула в облако уже удалённые
     * документы. Очередь при этом сохраняется: при ошибке удаления изменения не теряются.
     *
     * @throws com.habittracker.domain.model.SyncInProgressException текущая отправка не
     *   завершилась вовремя; пауза снята, удалять нельзя.
     */
    suspend fun pauseSync()

    /** Возобновляет отправку очереди после [pauseSync]. */
    fun resumeSync()

    /**
     * Удаляет из облака (Firestore + Storage) все данные пользователя [userId].
     *
     * Групповые привычки и челленджи, где есть другие участники, не удаляются, а передаются
     * следующему участнику. Операция идемпотентна: при повторе после сбоя уже удалённое
     * просто не находится.
     *
     * @throws com.habittracker.domain.model.NoConnectionException нет сети или сервер не ответил.
     * @throws com.habittracker.domain.model.RequestRejectedException сервер отказал в доступе.
     */
    suspend fun deleteRemoteUserData(userId: String)

    /**
     * Удаляет данные пользователя с устройства: БД (вместе с очередью синхронизации), настройки,
     * файлы, напоминания, FCM-токен. Шаги независимы: сбой одного логируется и не мешает остальным.
     * Последним шагом снимает отметку удаления — только если БД и настройки стёрлись: иначе
     * стирание повторит следующий запуск.
     */
    suspend fun wipeLocalData()

    /** Незавершённое удаление аккаунта, если прошлую попытку прервали. */
    fun pendingDeletion(): PendingAccountDeletion?

    /** То же, что [pendingDeletion], с обновлениями: пока отметка стоит, слушатели облака не нужны. */
    fun observePendingDeletion(): Flow<PendingAccountDeletion?>

    /**
     * Отмечает стадию удаления до её начала. Пока отметка стоит, очередь синхронизации не
     * отправляется — даже после перезапуска приложения.
     *
     * @throws java.io.IOException отметку не удалось сохранить; продолжать удаление нельзя.
     */
    suspend fun markDeletion(userId: String, stage: DeletionStage)

    /** Пользователь передумал удалять: снимает отметку и отправляет накопленную очередь. */
    fun cancelPendingDeletion()
}
