package com.habittracker.data.repository

import com.habittracker.data.local.ChallengeDao
import com.habittracker.data.local.HabitEntryDao
import com.habittracker.data.local.entity.toDomain
import com.habittracker.data.local.entity.toEntity
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.repository.ChallengeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChallengeRepositoryImpl @Inject constructor(
    private val challengeDao: ChallengeDao,
    private val habitEntryDao: HabitEntryDao
) : ChallengeRepository {
    
    override suspend fun insertChallenge(challenge: Challenge) {
        challengeDao.insertChallenge(challenge.toEntity())
    }
    
    override suspend fun updateChallenge(challenge: Challenge) {
        challengeDao.updateChallenge(challenge.toEntity())
    }
    
    override suspend fun deleteChallenge(challengeId: String) {
        challengeDao.deleteChallenge(challengeId)
    }
    
    override suspend fun getChallengeById(challengeId: String): Challenge? {
        return challengeDao.getChallengeById(challengeId)?.toDomain()
    }
    
    override fun getAllChallenges(): Flow<List<Challenge>> {
        return challengeDao.getAllChallenges().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getChallengesForUser(userId: String): Flow<List<Challenge>> {
        return challengeDao.getUserChallenges(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getActiveChallenges(): Flow<List<Challenge>> {
        return challengeDao.getChallengesByStatus("ACTIVE").map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getCompletedChallenges(): Flow<List<Challenge>> {
        return challengeDao.getChallengesByStatus("COMPLETED").map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getChallengesForHabit(habitId: String): List<Challenge> {
        return challengeDao.getChallengesForHabit(habitId).first().map { it.toDomain() }
    }
    
    override suspend fun joinChallenge(challengeId: String, userId: String) {
        val challenge = getChallengeById(challengeId)
        if (challenge != null) {
            val participants = challenge.participants.toMutableList()
            if (!participants.contains(userId)) {
                participants.add(userId)
                challengeDao.updateChallengeParticipants(challengeId, participants)
            }
        }
    }
    
    override suspend fun leaveChallenge(challengeId: String, userId: String) {
        val challenge = getChallengeById(challengeId)
        if (challenge != null) {
            val participants = challenge.participants.toMutableList()
            if (participants.contains(userId)) {
                participants.remove(userId)
                challengeDao.updateChallengeParticipants(challengeId, participants)
            }
        }
    }
    
    override suspend fun getChallengeParticipants(challengeId: String): List<String> {
        return getChallengeById(challengeId)?.participants ?: emptyList()
    }
    
    override suspend fun updateChallengeStatus(challengeId: String, status: String) {
        challengeDao.updateChallengeStatus(challengeId, status)
    }
    
    override suspend fun getChallengeLeaderboard(challengeId: String): List<Pair<String, Int>> {
        val challenge = getChallengeById(challengeId) ?: return emptyList()
        return challenge.participants.map { userId ->
            val score = habitEntryDao.getCompletedEntriesCount(challenge.habitId, userId)
            Pair(userId, score)
        }.sortedByDescending { it.second }
    }
    
    override suspend fun endChallenge(challengeId: String, winnerId: String?) {
        challengeDao.updateChallengeWinner(challengeId, winnerId)
    }
}
