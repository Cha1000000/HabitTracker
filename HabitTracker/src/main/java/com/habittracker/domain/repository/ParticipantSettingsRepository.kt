package com.habittracker.domain.repository

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitParticipantSettings
import kotlinx.coroutines.flow.Flow

interface ParticipantSettingsRepository {
    suspend fun get(habitId: String, userId: String): HabitParticipantSettings?
    fun observe(habitId: String, userId: String): Flow<HabitParticipantSettings?>
    suspend fun upsert(settings: HabitParticipantSettings)
    suspend fun upsertDefaultsFromHabit(habit: Habit, userId: String): HabitParticipantSettings
    suspend fun deleteForHabit(habitId: String)
    suspend fun deleteForHabitAndUser(habitId: String, userId: String)
}
