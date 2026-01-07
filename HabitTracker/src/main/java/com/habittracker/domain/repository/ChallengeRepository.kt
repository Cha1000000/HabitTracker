package com.habittracker.domain.repository

import com.habittracker.domain.model.Challenge
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Интерфейс ChallengeRepository уже содержит все необходимые методы для Use Cases
 */
interface ChallengeRepository {
    
    suspend fun insertChallenge(challenge: Challenge)
    
    suspend fun updateChallenge(challenge: Challenge)
    
    suspend fun deleteChallenge(challengeId: String)
    
    suspend fun getChallengeById(challengeId: String): Challenge?
    
    fun getAllChallenges(): Flow<List<Challenge>>
    
    fun getChallengesForUser(userId: String): Flow<List<Challenge>>
    
    fun getActiveChallenges(): Flow<List<Challenge>>
    
    fun getCompletedChallenges(): Flow<List<Challenge>>
    
    suspend fun getChallengesForHabit(habitId: String): List<Challenge>
    
    suspend fun joinChallenge(challengeId: String, userId: String)
    
    suspend fun leaveChallenge(challengeId: String, userId: String)
    
    suspend fun getChallengeParticipants(challengeId: String): List<String>
    
    suspend fun updateChallengeStatus(challengeId: String, status: String)
    
    suspend fun getChallengeLeaderboard(challengeId: String): List<Pair<String, Int>>
    
    suspend fun endChallenge(challengeId: String, winnerId: String?)
    
    /**
     * Realtime наблюдение за челленджами пользователя: Firestore → Room merge → Flow из Room
     */
    fun observeChallengesForUser(userId: String): Flow<List<Challenge>>
    
    /**
     * Получить количество выполненных дней для пользователя в челлендже
     */
    suspend fun getCompletedDaysForUserInChallenge(
        challengeId: String,
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int
    
    /**
     * Получить текущую серию выполнений для пользователя в челлендже
     */
    suspend fun getCurrentStreakForUserInChallenge(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int
    
    /**
     * Получить количество выполненных дней для пользователя из Firestore
     */
    suspend fun getCompletedDaysFromFirestore(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int

    suspend fun getCurrentStreakFromFirestore(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int
    
    /**
     * Принять приглашение в челлендж
     */
    suspend fun acceptChallengeInvitation(challengeId: String, userId: String)
    
    /**
     * Отклонить приглашение в челлендж
     */
    suspend fun declineChallengeInvitation(challengeId: String, userId: String)
    
    /**
     * Отменить челлендж (только для создателя)
     */
    suspend fun cancelChallenge(challengeId: String)
    
    /**
     * Добавить участников в челлендж
     */
    suspend fun addParticipantsToChallenge(challengeId: String, participantIds: List<String>)
    
    /**
     * Получить суммарное значение (value) для пользователя в челлендже
     * Используется для измеряемых привычек
     */
    suspend fun getTotalValueForUserInChallenge(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int
    
    /**
     * Получить суммарное значение (value) для пользователя из Firestore
     * Используется для измеряемых привычек
     */
    suspend fun getTotalValueFromFirestore(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int
}
