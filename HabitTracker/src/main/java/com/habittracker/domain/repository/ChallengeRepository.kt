package com.habittracker.domain.repository

import com.habittracker.domain.model.Challenge
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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
}
