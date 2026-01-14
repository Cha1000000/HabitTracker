package com.habittracker.domain.repository

import android.content.Intent
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
     * @return Flow, который эмитит результат операции: успех или ошибку.
     * Intent больше не нужен с новым Credential Manager API.
     */
    suspend fun signInWithGoogle(): Flow<Result<Intent?>>

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
}
