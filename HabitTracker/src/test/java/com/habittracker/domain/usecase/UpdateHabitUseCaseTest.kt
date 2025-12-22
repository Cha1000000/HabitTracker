package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class UpdateHabitUseCaseTest {

    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var updateHabitUseCase: UpdateHabitUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
        title = "Updated Test Habit",
        description = "Updated Test Description",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = null,
        targetValue = 1,
        unit = "раз",
        participants = emptyList(),
        isActive = true,
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        createdBy = "test-user-id"
    )

    @Before
    fun setUp() {
        habitRepository = FakeHabitRepository()
        updateHabitUseCase = UpdateHabitUseCase(habitRepository)
    }

    @Test
    fun `invoke should update habit in repository`() = runTest {
        // Act
        updateHabitUseCase(testHabit)

        // Assert
        assertTrue(habitRepository.updateHabitCalled)
        assertEquals(testHabit, habitRepository.updatedHabit)
    }

    private class FakeHabitRepository : HabitRepository {
        var updateHabitCalled = false
        var updatedHabit: Habit? = null
        private val habits = mutableListOf<Habit>()

        override suspend fun updateHabit(habit: Habit) {
            updateHabitCalled = true
            updatedHabit = habit
        }

        override suspend fun insertHabit(habit: Habit) = throw NotImplementedError()
        override fun getHabitsByUserId(userId: String) = throw NotImplementedError()
        override suspend fun getHabitById(id: String): Habit? {
            return habits.find { it.id == id }
        }
        
        override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
            return habits.find { it.inviteCode == inviteCode && it.isActive && it.isPublic }
        }

        override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
            return flowOf(habits.find { it.id == habitId })
        }

        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override fun getAllHabits() = throw NotImplementedError()
        override fun getPersonalHabits(userId: String) = throw NotImplementedError()
        override fun getGroupHabits(userId: String) = throw NotImplementedError()
        override suspend fun addParticipantToHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun removeParticipantFromHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun getHabitParticipants(habitId: String) = throw NotImplementedError()
        override suspend fun searchHabits(userId: String, query: String) = throw NotImplementedError()
        override fun getPublicHabits(): Flow<List<Habit>> = flowOf(emptyList())
        override suspend fun getHabitParticipantSettings(habitId: String, userId: String) = null
        override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) = throw NotImplementedError()
        override fun observeHabitParticipantSettings(habitId: String, userId: String) = flowOf(null)
    }
}
