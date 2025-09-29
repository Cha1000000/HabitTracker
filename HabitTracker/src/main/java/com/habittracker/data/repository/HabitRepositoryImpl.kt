package com.habittracker.data.repository

import com.habittracker.data.local.dao.HabitDao
import com.habittracker.data.local.mappers.toDomain
import com.habittracker.data.local.mappers.toEntity
import com.habittracker.domain.model.Habit
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao
) : HabitRepository {
    
    override suspend fun insertHabit(habit: Habit) {
        habitDao.insertHabit(habit.toEntity())
    }
    
    override suspend fun updateHabit(habit: Habit) {
        habitDao.insertHabit(habit.toEntity())
    }
    
    override suspend fun deleteHabit(habitId: String) {
        habitDao.deleteHabitById(habitId)
    }
    
    override suspend fun getHabitById(habitId: String): Habit? {
        return habitDao.getHabitById(habitId)?.toDomain()
    }
    
    override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
        return habitDao.getHabitByInviteCode(inviteCode)?.toDomain()
    }
    
    override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
        return habitDao.getHabitByIdFlow(habitId).map { entity ->
            entity?.toDomain()
        }
    }
    
    override fun getAllHabits(): Flow<List<Habit>> {
        return habitDao.getAllHabits().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getHabitsByUserId(userId: String): Flow<List<Habit>> {
        return habitDao.getUserHabits(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getPersonalHabits(userId: String): Flow<List<Habit>> {
        return habitDao.getUserPersonalHabits(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getGroupHabits(userId: String): Flow<List<Habit>> {
        return habitDao.getUserGroupHabits(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun addParticipantToHabit(habitId: String, userId: String) {
        val habit = habitDao.getHabitById(habitId)
        if (habit != null) {
            val participants = habit.participants.toMutableList()
            if (!participants.contains(userId)) {
                participants.add(userId)
                habitDao.updateHabitParticipants(habitId, participants, Clock.System.now().toEpochMilliseconds())
            }
        }
    }
    
    override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {
        val habit = habitDao.getHabitById(habitId)
        if (habit != null) {
            val participants = habit.participants.toMutableList()
            if (participants.contains(userId)) {
                participants.remove(userId)
                habitDao.updateHabitParticipants(habitId, participants, Clock.System.now().toEpochMilliseconds())
            }
        }
    }
    
    override suspend fun getHabitParticipants(habitId: String): List<String> {
        return habitDao.getHabitById(habitId)?.participants ?: emptyList()
    }
    
    override suspend fun searchHabits(userId: String, query: String): List<Habit> {
        return habitDao.searchUserHabits(userId, query)
            .map { entities -> entities.map { it.toDomain() } }
            .first()
    }
    
    override fun getPublicHabits(): Flow<List<Habit>> {
        // Получаем все публичные привычки из локальной БД
        // В будущем можно добавить синхронизацию с Firebase
        return habitDao.getAllHabits().map { entities ->
            entities
                .filter { it.isPublic && it.isActive }
                .map { it.toDomain() }
        }
    }
}
