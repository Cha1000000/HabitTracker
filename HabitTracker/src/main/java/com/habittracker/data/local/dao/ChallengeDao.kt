package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.ChallengeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: ChallengeEntity)

    @Update
    suspend fun updateChallenge(challenge: ChallengeEntity)

    @Query("UPDATE challenges SET deletedAtMillis = :deletedAtMillis, updatedAtMillis = :updatedAtMillis WHERE id = :challengeId")
    suspend fun softDeleteChallengeById(challengeId: String, deletedAtMillis: Long, updatedAtMillis: Long)

    @Query("SELECT * FROM challenges WHERE id = :challengeId")
    suspend fun getChallengeById(challengeId: String): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE id = :challengeId AND deletedAtMillis IS NULL")
    suspend fun getActiveChallengeById(challengeId: String): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE deletedAtMillis IS NULL ORDER BY endDate DESC")
    fun getAllChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE deletedAtMillis IS NULL AND habitId = :habitId")
    fun getChallengesForHabit(habitId: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE deletedAtMillis IS NULL AND participants LIKE '%' || :userId || '%' ORDER BY endDate DESC")
    fun getUserChallenges(userId: String): Flow<List<ChallengeEntity>>
    
    @Query("SELECT * FROM challenges WHERE deletedAtMillis IS NULL AND participants LIKE '%' || :userId || '%' ORDER BY endDate DESC")
    suspend fun getUserChallengesSync(userId: String): List<ChallengeEntity>

    @Query("SELECT * FROM challenges WHERE deletedAtMillis IS NULL AND status = :status ORDER BY endDate DESC")
    fun getChallengesByStatus(status: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE deletedAtMillis IS NULL AND status IN (:statuses) ORDER BY endDate DESC")
    fun getChallengesByStatuses(statuses: List<String>): Flow<List<ChallengeEntity>>

    @Query("UPDATE challenges SET participants = :participants WHERE id = :challengeId")
    suspend fun updateChallengeParticipants(challengeId: String, participants: List<String>)

    @Query("UPDATE challenges SET participants = :participants, acceptedParticipants = :acceptedParticipants, updatedAtMillis = :updatedAtMillis WHERE id = :challengeId")
    suspend fun updateChallengeParticipantsAndAccepted(challengeId: String, participants: List<String>, acceptedParticipants: List<String>, updatedAtMillis: Long)

    @Query("UPDATE challenges SET status = :status WHERE id = :challengeId")
    suspend fun updateChallengeStatus(challengeId: String, status: String)

    @Query("UPDATE challenges SET winnerId = :winnerId, status = 'COMPLETED' WHERE id = :challengeId")
    suspend fun updateChallengeWinner(challengeId: String, winnerId: String?)
}