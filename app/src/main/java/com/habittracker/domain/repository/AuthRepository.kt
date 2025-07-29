package com.habittracker.domain.repository

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
     * Выполняет вход пользователя с помощью Google аккаунта.
     *
     * @param idToken Токен, полученный от Google Sign-In.
     * @return Flow, который эмитит результат операции: успех или ошибку.
     */
    suspend fun signInWithGoogle(idToken: String): Flow<Result<Unit>>

    /**
     * Выполняет выход текущего пользователя из системы.
     */
    suspend fun logout()
}
