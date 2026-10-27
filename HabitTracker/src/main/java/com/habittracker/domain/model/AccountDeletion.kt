package com.habittracker.domain.model

import android.content.Context

/** Способ, которым пользователь вошёл в аккаунт. */
enum class SignInMethod { PASSWORD, GOOGLE, OTHER }

/** Данные для повторного входа, без которого Firebase не даёт удалить аккаунт. */
sealed interface ReauthRequest {
    data class Password(val password: String) : ReauthRequest

    /** [activityContext] нужен Credential Manager, чтобы показать выбор аккаунта Google. */
    class Google(val activityContext: Context) : ReauthRequest

    /** Вход был только что: Firebase разрешает удаление без повторного подтверждения. */
    data object None : ReauthRequest
}

/** Firebase считает вход устаревшим — нужно войти заново и повторить действие. */
class ReauthRequiredException(cause: Throwable? = null) : Exception("Recent login required", cause)

/** Неверный пароль или выбран другой аккаунт Google. */
class InvalidCredentialsException(cause: Throwable? = null) : Exception("Invalid credentials", cause)

/** Учётной записи в Firebase Auth уже нет — например, прошлое удаление дошло, а ответ потерялся. */
class AccountAlreadyDeletedException(cause: Throwable? = null) : Exception("Account no longer exists", cause)

/** Никто не вошёл в аккаунт. */
class NotSignedInException : Exception("No signed-in user")

/** Пользователь закрыл окно выбора аккаунта. */
class AuthCancelledException(cause: Throwable? = null) : Exception("Authentication cancelled", cause)

/** Нет сети или сервер не ответил вовремя. */
class NoConnectionException(cause: Throwable? = null) : Exception("No connection", cause)

/** Firebase временно заблокировал попытки входа — например, после нескольких неверных паролей. */
class TooManyAttemptsException(cause: Throwable? = null) : Exception("Too many attempts", cause)

/** На устройстве нет аккаунта Google, которым можно подтвердить вход. */
class NoGoogleAccountException(cause: Throwable? = null) : Exception("No Google account on device", cause)

/** Очередь синхронизации ещё отправляется — удалять сейчас нельзя, она вернула бы данные. */
class SyncInProgressException : Exception("Pending sync did not finish in time")

/** Сервер отказал: нет прав, учётка отключена или запрос противоречит правилам. */
class RequestRejectedException(cause: Throwable? = null) : Exception("Request rejected by server", cause)

/** Докуда дошло удаление аккаунта, если его прервали (например, убили процесс). */
enum class DeletionStage {
    /** Облачные данные удаляются: часть могла уже пропасть, аккаунт ещё жив. */
    CLOUD_IN_PROGRESS,

    /** Облако уже чистое — осталось удалить учётку в Auth и стереть устройство. */
    CLOUD_DONE
}

/** Незавершённое удаление аккаунта [userId]. Пока оно есть, в облако ничего не пишется. */
data class PendingAccountDeletion(val userId: String, val stage: DeletionStage)
