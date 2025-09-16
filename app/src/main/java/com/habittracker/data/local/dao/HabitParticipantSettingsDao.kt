package com.habittracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habittracker.data.local.entity.HabitParticipantSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitParticipantSettingsDao {
    @Query("SELECT * FROM habit_participant_settings WHERE habitId = :habitId AND userId = :userId LIMIT 1")
    suspend fun get(habitId: String, userId: String): HabitParticipantSettingsEntity?

    @Query("SELECT * FROM habit_participant_settings WHERE habitId = :habitId AND userId = :userId LIMIT 1")
    fun observe(habitId: String, userId: String): Flow<HabitParticipantSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HabitParticipantSettingsEntity)

    @Query("DELETE FROM habit_participant_settings WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: String)

    @Query("DELETE FROM habit_participant_settings WHERE habitId = :habitId AND userId = :userId")
    suspend fun deleteForHabitAndUser(habitId: String, userId: String)
}
