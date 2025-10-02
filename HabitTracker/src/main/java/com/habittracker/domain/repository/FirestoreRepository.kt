package com.habittracker.domain.repository

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitEntry
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для работы с Firebase Firestore.
 * Обеспечивает синхронизацию данных о привычках между устройствами.
 */
interface FirestoreRepository {

    /**
     * Получает все привычки пользователя из Firestore.
     *
     * @param userId UID пользователя
     * @return Flow со списком привычек
     */
    fun getUserHabits(userId: String): Flow<List<Habit>>

    /**
     * Сохраняет привычку в Firestore.
     *
     * @param habit Привычка для сохранения
     */
    suspend fun saveHabit(habit: Habit)

    /**
     * Обновляет привычку в Firestore.
     *
     * @param habit Обновленная привычка
     */
    suspend fun updateHabit(habit: Habit)

    /**
     * Удаляет привычку из Firestore.
     *
     * @param habitId ID привычки
     */
    suspend fun deleteHabit(habitId: String)

    /**
     * Получает привычку по ID из Firestore.
     *
     * @param habitId ID привычки
     * @return Привычка или null, если не найдена
     */
    suspend fun getHabitById(habitId: String): Habit?

    /**
     * Сохраняет запись о выполнении привычки в Firestore.
     *
     * @param habitEntry Запись о выполнении
     */
    suspend fun saveHabitEntry(habitEntry: HabitEntry)

    /**
     * Получает записи о выполнении привычки из Firestore.
     *
     * @param habitId ID привычки
     * @param userId UID пользователя
     * @return Flow со списком записей
     */
    fun getHabitEntries(habitId: String, userId: String): Flow<List<HabitEntry>>

    /**
     * Получает запись о выполнении привычки за конкретную дату.
     *
     * @param habitId ID привычки
     * @param userId UID пользователя
     * @param date Дата
     * @return Запись о выполнении или null
     */
    suspend fun getHabitEntry(habitId: String, userId: String, date: kotlinx.datetime.LocalDate): HabitEntry?

    /**
     * Синхронизирует локальные данные с Firestore.
     * Загружает изменения с сервера и отправляет локальные изменения.
     */
    suspend fun syncData()

    // Participant personal settings for reminders
    suspend fun getParticipantSettings(habitId: String, userId: String): HabitParticipantSettings?
    suspend fun setParticipantSettings(settings: HabitParticipantSettings)
    
    /**
     * Получает все публичные привычки от всех пользователей.
     * Используется для экрана "Публичные привычки".
     *
     * @param excludeUserId ID пользователя, чьи привычки нужно исключить (обычно текущий пользователь)
     * @return Flow со списком публичных привычек
     */
    fun getAllPublicHabits(excludeUserId: String? = null): Flow<List<Habit>>
    
    /**
     * Ищет привычку по коду приглашения в Firestore.
     *
     * @param inviteCode Код приглашения
     * @return Привычка или null, если не найдена
     */
    suspend fun getHabitByInviteCode(inviteCode: String): Habit?
}
