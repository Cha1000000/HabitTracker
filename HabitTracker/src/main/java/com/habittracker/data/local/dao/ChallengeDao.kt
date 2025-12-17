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
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertChallenge(challenge: ChallengeEntity)

    @Update
    suspend fun updateChallenge(challenge: ChallengeEntity)

    @Query("DELETE FROM challenges WHERE id = :challengeId")
    suspend fun deleteChallenge(challengeId: String)

    @Query("SELECT * FROM challenges WHERE id = :challengeId")
    suspend fun getChallengeById(challengeId: String): ChallengeEntity?

    @Query("SELECT * FROM challenges ORDER BY endDate DESC")
    fun getAllChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE habitId = :habitId")
    fun getChallengesForHabit(habitId: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE participants LIKE '%' || :userId || '%' ORDER BY endDate DESC")
    fun getUserChallenges(userId: String): Flow<List<ChallengeEntity>>
    
    @Query("SELECT * FROM challenges WHERE participants LIKE '%' || :userId || '%' ORDER BY endDate DESC")
    suspend fun getUserChallengesSync(userId: String): List<ChallengeEntity>

    @Query("SELECT * FROM challenges WHERE status = :status ORDER BY endDate DESC")
    fun getChallengesByStatus(status: String): Flow<List<ChallengeEntity>>

    @Query("UPDATE challenges SET participants = :participants WHERE id = :challengeId")
    suspend fun updateChallengeParticipants(challengeId: String, participants: List<String>)

    @Query("UPDATE challenges SET participants = :participants, acceptedParticipants = :acceptedParticipants, updatedAtMillis = :updatedAtMillis WHERE id = :challengeId")
    suspend fun updateChallengeParticipantsAndAccepted(challengeId: String, participants: List<String>, acceptedParticipants: List<String>, updatedAtMillis: Long)

    @Query("UPDATE challenges SET status = :status WHERE id = :challengeId")
    suspend fun updateChallengeStatus(challengeId: String, status: String)

    @Query("UPDATE challenges SET winnerId = :winnerId, status = 'COMPLETED' WHERE id = :challengeId")
    suspend fun updateChallengeWinner(challengeId: String, winnerId: String?)
}