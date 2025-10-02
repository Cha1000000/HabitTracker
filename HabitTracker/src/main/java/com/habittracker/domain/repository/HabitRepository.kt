package com.habittracker.domain.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    
    suspend fun insertHabit(habit: Habit)
    
    suspend fun updateHabit(habit: Habit)
    
    suspend fun deleteHabit(habitId: String)
    
    suspend fun getHabitById(habitId: String): Habit?
    
    suspend fun getHabitByInviteCode(inviteCode: String): Habit?
    
    fun getHabitByIdFlow(habitId: String): Flow<Habit?>
    
    fun getAllHabits(): Flow<List<Habit>>
    
    fun getHabitsByUserId(userId: String): Flow<List<Habit>>
    
    fun getPersonalHabits(userId: String): Flow<List<Habit>>
    
    fun getGroupHabits(userId: String): Flow<List<Habit>>
    
    suspend fun addParticipantToHabit(habitId: String, userId: String)
    
    suspend fun removeParticipantFromHabit(habitId: String, userId: String)
    
    suspend fun getHabitParticipants(habitId: String): List<String>
    
    suspend fun searchHabits(userId: String, query: String): List<Habit>
    
    fun getPublicHabits(): Flow<List<Habit>>
}
