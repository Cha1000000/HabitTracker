package com.habittracker.presentation.fakes

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

// Fake repository for previews
class FakeHabitRepository : HabitRepository {
    private val _habits = mutableListOf<Habit>()
    private val habitsFlow = MutableStateFlow<List<Habit>>(emptyList())

    // Add a test user ID for previews
    private val testUserId = "test_user_id"

    init {
        // Add some test data for previews
        _habits.add(
            Habit(
                id = UUID.randomUUID().toString(),
                title = "Test Habit",
                description = "Test Description",
                icon = "🏃",
                type = HabitType.PERSONAL,
                frequency = HabitFrequency.DAILY,
                reminderTime = LocalTime(hour = 8, minute = 0),
                reminderEnabled = true,
                targetValue = 100,
                unit = "minutes",
                createdBy = "current_user_id",
                participants = listOf("current_user_id"),
                isActive = true,
                createdAt = now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                updatedAt = now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            )
        )
    }

    override suspend fun insertHabit(habit: Habit) {
        _habits.add(habit)
        habitsFlow.emit(_habits.toList())
    }

    override suspend fun updateHabit(habit: Habit) {
        val index = _habits.indexOfFirst { it.id == habit.id }
        if (index != -1) {
            _habits[index] = habit
            habitsFlow.emit(_habits.toList())
        }
    }

    override suspend fun deleteHabit(habitId: String) {
        _habits.removeAll { it.id == habitId }
        habitsFlow.emit(_habits.toList())
    }

    override suspend fun getHabitById(habitId: String): Habit? {
        return _habits.find { it.id == habitId }
    }

    override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
        return _habits.find { it.inviteCode == inviteCode && it.isActive && it.isPublic }
    }

    override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
        return habitsFlow.map { habits -> habits.find { it.id == habitId } }
    }

    override fun getAllHabits(): Flow<List<Habit>> {
        return habitsFlow
    }

    override fun getHabitsByUserId(userId: String): Flow<List<Habit>> {
        return habitsFlow.map { habits ->
            habits.filter { it.createdBy == testUserId }
        }
    }

    override fun getPersonalHabits(userId: String): Flow<List<Habit>> {
        return habitsFlow.map { habits ->
            habits.filter { it.createdBy == testUserId && it.type == HabitType.PERSONAL }
        }
    }

    override fun getGroupHabits(userId: String): Flow<List<Habit>> {
        return habitsFlow.map { habits ->
            habits.filter { it.createdBy == testUserId && it.type == HabitType.GROUP }
        }
    }

    override suspend fun addParticipantToHabit(habitId: String, userId: String) {
        // In a real implementation, this would add a participant to a group habit
    }

    override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {
        // In a real implementation, this would remove a participant from a group habit
    }

    override suspend fun getHabitParticipants(habitId: String): List<String> {
        // In a real implementation, this would return the list of participant IDs
        return emptyList()
    }

    override suspend fun searchHabits(userId: String, query: String): List<Habit> {
        return _habits.filter {
            it.createdBy == testUserId &&
            (it.title.contains(query, ignoreCase = true) ||
             it.description.contains(query, ignoreCase = true))
        }
    }
    
    override fun getPublicHabits(): Flow<List<Habit>> {
        return flow {
            emit(_habits.filter { it.isPublic && it.isActive })
        }
    }
    
    // HabitParticipantSettings methods for fake repository
    override suspend fun getHabitParticipantSettings(habitId: String, userId: String): HabitParticipantSettings? {
        // Return null for fake implementation
        return null
    }
    
    override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) {
        // No-op for fake implementation
    }
    
    override fun observeHabitParticipantSettings(habitId: String, userId: String): Flow<HabitParticipantSettings?> {
        // Return empty flow for fake implementation
        return flowOf(null)
    }
}