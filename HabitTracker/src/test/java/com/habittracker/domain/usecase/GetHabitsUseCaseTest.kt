package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GetHabitsUseCaseTest {

    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var getHabitsUseCase: GetHabitsUseCase

    private val testUserId = "test-user-id"
    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val testHabits = listOf(
        Habit(
            id = "habit-1",
            title = "Test Habit 1",
            description = "Description 1",
            icon = "",
            color = "#2196F3",
            type = HabitType.PERSONAL,
            frequency = HabitFrequency.DAILY,
            reminderTime = null,
            targetValue = 1,
            unit = "раз",
            participants = emptyList(),
            isActive = true,
            createdAt = today,
            updatedAt = today,
            createdBy = testUserId
        ),
        Habit(
            id = "habit-2",
            title = "Test Habit 2",
            description = "Description 2",
            icon = "",
            color = "#4CAF50",
            type = HabitType.PERSONAL,
            frequency = HabitFrequency.WEEKLY,
            reminderTime = null,
            targetValue = 3,
            unit = "раз",
            participants = emptyList(),
            isActive = true,
            createdAt = today,
            updatedAt = today,
            createdBy = testUserId
        )
    )

    @Before
    fun setUp() {
        habitRepository = FakeHabitRepository()
        getHabitsUseCase = GetHabitsUseCase(habitRepository)
    }

    @Test
    fun `invoke should return habits from HabitRepository`() = runTest {
        // Arrange
        habitRepository.habitsToReturn = testHabits

        // Act
        val result = getHabitsUseCase(testUserId).first()

        // Assert
        assertEquals(testHabits, result)
        assertTrue(habitRepository.getHabitsByUserIdCalled)
    }

    @Test
    fun `invoke should throw when repository fails`() = runTest {
        // Arrange
        habitRepository.shouldThrowException = true

        // Act & Assert - ожидаем исключение
        try {
            getHabitsUseCase(testUserId).first()
            fail("Expected exception when repository fails")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("Local repository error") == true)
        }

        assertTrue(habitRepository.getHabitsByUserIdCalled)
    }

    // Fake implementations for testing
    private class FakeHabitRepository : HabitRepository {
        var getHabitsByUserIdCalled = false
        var habitsToReturn: List<Habit> = emptyList()
        var shouldThrowException = false

        override fun getHabitsByUserId(userId: String): Flow<List<Habit>> {
            getHabitsByUserIdCalled = true
            return if (shouldThrowException) {
                flow { throw RuntimeException("Local repository error") }
            } else {
                flowOf(habitsToReturn)
            }
        }

        override suspend fun insertHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun updateHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun getHabitById(habitId: String): Habit? {
            return habitsToReturn.find { it.id == habitId }
        }
        
        override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
            return habitsToReturn.find { it.inviteCode == inviteCode && it.isActive && it.isPublic }
        }

        override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
            return flowOf(habitsToReturn.find { it.id == habitId })
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
