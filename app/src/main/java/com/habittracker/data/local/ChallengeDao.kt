package com.habittracker.data.local

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

    @Query("DELETE FROM challenges WHERE id = :challengeId")
    suspend fun deleteChallenge(challengeId: String)

    @Query("SELECT * FROM challenges WHERE id = :challengeId")
    fun getChallengeById(challengeId: String): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenges WHERE habitId = :habitId")
    fun getChallengesForHabit(habitId: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE participants LIKE '%' || :userId || '%' ORDER BY endDate DESC")
    fun getUserChallenges(userId: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE status = :status ORDER BY endDate DESC")
    fun getChallengesByStatus(status: String): Flow<List<ChallengeEntity>>
}
