package com.habittracker.domain.model

import android.content.Context

/** Способ, которым пользователь вошёл в аккаунт. */
enum class SignInMethod { PASSWORD, GOOGLE, OTHER }

/** Данные для повторного входа, без которого Firebase не даёт удалить аккаунт. */
sealed interface ReauthRequest {
    data class Password(val password: String) : ReauthRequest

    /** [activityContext] нужен Credential Manager, чтобы показать выбор аккаунта Google. */
    class Google(val activityContext: Context) : ReauthRequest
}

/** Firebase считает вход устаревшим — нужно войти заново и повторить действие. */
class ReauthRequiredException(cause: Throwable? = null) : Exception("Recent login required", cause)

/** Неверный пароль или выбран другой аккаунт Google. */
class InvalidCredentialsException(cause: Throwable? = null) : Exception("Invalid credentials", cause)

/** Учётной записи в Firebase Auth уже нет — например, прошлое удаление дошло, а ответ потерялся. */
class AccountAlreadyDeletedException(cause: Throwable? = null) : Exception("Account no longer exists", cause)

/** Пользователь закрыл окно выбора аккаунта. */
class NotSignedInException : Exception("No signed-in user")

class AuthCancelledException(cause: Throwable? = null) : Exception("Authentication cancelled", cause)
