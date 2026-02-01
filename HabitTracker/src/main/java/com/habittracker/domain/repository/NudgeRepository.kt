package com.habittracker.domain.repository

import com.habittracker.domain.model.Nudge
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для работы с "пингами" (nudges) между участниками групповых привычек
 */
interface NudgeRepository {
    
    /**
     * Отправить nudge (пинг) участнику
     */
    suspend fun sendNudge(nudge: Nudge): Result<Unit>
    
    /**
     * Получить последний nudge от senderId к receiverId для конкретной привычки
     */
    suspend fun getLastNudge(habitId: String, senderId: String, receiverId: String): Nudge?
    
    /**
     * Проверить, можно ли отправить nudge (прошёл ли cooldown)
     */
    suspend fun canSendNudge(habitId: String, senderId: String, receiverId: String): Boolean
    
    /**
     * Получить список userId, которым текущий пользователь уже отправил nudge
     * и cooldown ещё не истёк (для блокировки кнопок в UI)
     */
    fun getNudgedUserIdsFlow(habitId: String, senderId: String): Flow<Set<String>>
    
    /**
     * Получить список userId на cooldown (одноразовый запрос)
     */
    suspend fun getNudgedUserIds(habitId: String, senderId: String): Set<String>
}
