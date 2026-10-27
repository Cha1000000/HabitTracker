package com.habittracker.domain.repository

import android.content.Context
import android.content.Intent
import com.habittracker.domain.model.ReauthRequest
import com.habittracker.domain.model.SignInMethod
import com.habittracker.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для управления аутентификацией пользователя.
 */
interface AuthRepository {

    /**
     * Получает текущего аутентифицированного пользователя.
     *
     * @return Flow, который эмитит объект User, если пользователь вошел в систему, иначе null.
     */
    fun getCurrentUser(): Flow<User?>

    /**
     * Получает UID текущего аутентифицированного пользователя.
     *
     * @return UID пользователя или null, если пользователь не аутентифицирован.
     */
    fun getCurrentUserId(): String?

    /** UID вошедшего пользователя при каждом входе и выходе; `null` — никто не вошёл. */
    fun observeCurrentUserId(): Flow<String?>

    /**
     * Выполняет вход пользователя по email и паролю.
     *
     * @param email Email пользователя.
     * @param password Пароль пользователя.
     * @return Flow, который эмитит результат операции: успех или ошибку.
     */
    suspend fun login(email: String, password: String): Flow<Result<Unit>>

    /**
     * Регистрирует нового пользователя.
     *
     * @param email Email для регистрации.
     * @param password Пароль для регистрации.
     * @return Flow, который эмитит результат операции: успех или ошибку.
     */
    suspend fun register(email: String, password: String): Flow<Result<Unit>>

    /**
     * Выполняет вход пользователя с помощью Google аккаунта через Credential Manager API.
     *
     * @param activityContext Activity context, необходимый для Credential Manager UI.
     * @return Flow, который эмитит результат операции: успех или ошибку.
     */
    suspend fun signInWithGoogle(activityContext: Context): Flow<Result<Intent?>>

    /**
     * Выполняет выход текущего пользователя из системы.
     *
     * @return Flow, который эмитит результат операции: успех или ошибку.
     */
    suspend fun signOut(): Flow<Result<Unit>>

    /**
     * Отправляет письмо для сброса пароля на указанный email.
     *
     * @param email Email пользователя для сброса пароля.
     * @return Flow, который эмитит результат операции: успех или ошибку.
     */
    suspend fun sendPasswordResetEmail(email: String): Flow<Result<Unit>>

    /** Способ входа текущего пользователя; `null`, если никто не вошёл. */
    fun currentSignInMethod(): SignInMethod?

    /**
     * Повторно подтверждает вход текущего пользователя.
     *
     * @throws com.habittracker.domain.model.InvalidCredentialsException неверный пароль или чужой аккаунт.
     * @throws com.habittracker.domain.model.AuthCancelledException пользователь закрыл выбор аккаунта.
     * @throws com.habittracker.domain.model.AccountAlreadyDeletedException учётной записи уже нет.
     */
    suspend fun reauthenticate(request: ReauthRequest)

    /**
     * Удаляет учётную запись текущего пользователя из Firebase Auth.
     *
     * @throws com.habittracker.domain.model.ReauthRequiredException вход устарел, нужен [reauthenticate].
     */
    suspend fun deleteCurrentAuthUser()
}
