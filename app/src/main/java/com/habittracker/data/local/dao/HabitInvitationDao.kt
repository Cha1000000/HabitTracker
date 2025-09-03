package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habittracker.data.local.entity.HabitInvitationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitInvitationDao {

    @Query("SELECT * FROM habit_invitations WHERE id = :id")
    suspend fun getById(id: String): HabitInvitationEntity?

    @Query("SELECT * FROM habit_invitations WHERE toUserId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getIncomingInvitations(userId: String): Flow<List<HabitInvitationEntity>>

    @Query("SELECT * FROM habit_invitations WHERE fromUserId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getOutgoingInvitations(userId: String): Flow<List<HabitInvitationEntity>>

    @Query("SELECT * FROM habit_invitations WHERE habitId = :habitId ORDER BY createdAt DESC")
    fun getHabitInvitations(habitId: String): Flow<List<HabitInvitationEntity>>

    @Query("SELECT * FROM habit_invitations WHERE toUserId = :userId OR fromUserId = :userId ORDER BY createdAt DESC")
    fun getAllUserInvitations(userId: String): Flow<List<HabitInvitationEntity>>

    @Query("SELECT COUNT(*) FROM habit_invitations WHERE toUserId = :userId AND status = 'PENDING'")
    fun getPendingInvitationsCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM habit_invitations WHERE fromUserId = :userId AND status = 'PENDING'")
    suspend fun getActiveOutgoingInvitationsCount(userId: String): Int

    @Query("SELECT * FROM habit_invitations WHERE habitId = :habitId AND toUserId = :userId AND status = 'PENDING'")
    suspend fun getPendingInvitation(habitId: String, userId: String): HabitInvitationEntity?

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(invitation: HabitInvitationEntity)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAll(invitations: List<HabitInvitationEntity>)

    @Update
    suspend fun update(invitation: HabitInvitationEntity)

    @Delete
    suspend fun delete(invitation: HabitInvitationEntity)

    @Query("DELETE FROM habit_invitations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM habit_invitations WHERE habitId = :habitId")
    suspend fun deleteByHabitId(habitId: String)

    @Query("DELETE FROM habit_invitations WHERE status IN ('EXPIRED', 'DECLINED', 'CANCELLED') AND createdAt < :beforeTimestamp")
    suspend fun deleteOldInvitations(beforeTimestamp: Long)

    @Query("UPDATE habit_invitations SET status = 'EXPIRED' WHERE status = 'PENDING' AND expiresAt IS NOT NULL AND expiresAt < :currentTimestamp")
    suspend fun expireOldInvitations(currentTimestamp: Long)
}