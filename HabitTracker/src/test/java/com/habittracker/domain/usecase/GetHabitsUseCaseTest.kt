package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GetHabitsUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
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
        firestoreRepository = FakeFirestoreRepository()
        habitRepository = FakeHabitRepository()
        getHabitsUseCase = GetHabitsUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should return habits from Firestore when available`() = runTest {
        // Arrange
        firestoreRepository.habitsToReturn = testHabits

        // Act
        val result = getHabitsUseCase(testUserId).first()

        // Assert
        assertEquals(testHabits, result)
        assertTrue(firestoreRepository.getUserHabitsCalled)
    }

    @Test
    fun `invoke should fallback to local repository when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true
        habitRepository.habitsToReturn = testHabits

        // Act
        val result = getHabitsUseCase(testUserId).first()

        // Assert
        assertEquals(testHabits, result)
        assertTrue(firestoreRepository.getUserHabitsCalled)
        assertTrue(habitRepository.getHabitsByUserIdCalled)
    }

    @Test
    fun `invoke should return empty list when both repositories fail`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true
        habitRepository.shouldThrowException = true

        // Act & Assert - ожидаем исключение, так как оба репозитория падают
        try {
            val result = getHabitsUseCase(testUserId).first()
            // Если дошли сюда, значит исключение не было выброшено - это ошибка
            fail("Expected exception when both repositories fail")
        } catch (e: RuntimeException) {
            // Ожидаемое поведение - исключение должно быть выброшено
            assertNotNull(e)
        }

        assertTrue(firestoreRepository.getUserHabitsCalled)
        assertTrue(habitRepository.getHabitsByUserIdCalled)
    }

    // Fake implementations for testing
    private class FakeFirestoreRepository : FirestoreRepository {
        var getUserHabitsCalled = false
        var habitsToReturn: List<Habit> = emptyList()
        var shouldThrowException = false

        override fun getUserHabits(userId: String): Flow<List<Habit>> {
            getUserHabitsCalled = true
            return if (shouldThrowException) {
                flow { throw RuntimeException("Firestore error") }
            } else {
                flowOf(habitsToReturn)
            }
        }

        override suspend fun saveHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun updateHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override suspend fun getHabitById(habitId: String) = throw NotImplementedError()
        override suspend fun saveHabitEntry(habitEntry: HabitEntry) = throw NotImplementedError()
        override suspend fun getHabitEntry(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
        override fun getHabitEntries(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun syncData() = throw NotImplementedError()
        override suspend fun getParticipantSettings(habitId: String, userId: String) = null
        override suspend fun setParticipantSettings(settings: HabitParticipantSettings) = throw NotImplementedError()
        override fun getAllPublicHabits(excludeUserId: String?) = flowOf(emptyList<Habit>())
        override suspend fun getHabitByInviteCode(inviteCode: String) = throw NotImplementedError()
    }

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
        override suspend fun getHabitById(id: String): Habit? {
            return habitsToReturn.find { it.id == id }
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
