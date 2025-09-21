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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GetHabitByIdUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var getHabitByIdUseCase: GetHabitByIdUseCase

    private val testHabitId = "test-habit-id"
    private val nowMillis = Clock.System.now().toEpochMilliseconds()
    private val testHabit = Habit(
        id = testHabitId,
        title = "Test Habit",
        description = "Test Description",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = null,
        targetValue = 1,
        unit = "раз",
        participants = emptyList(),
        isActive = true,
        createdAt = nowMillis,
        updatedAt = nowMillis,
        createdBy = "test-user-id"
    )

    @Before
    fun setUp() {
        firestoreRepository = FakeFirestoreRepository()
        habitRepository = FakeHabitRepository()
        getHabitByIdUseCase = GetHabitByIdUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should return habit from Firestore when available`() = runTest {
        // Arrange
        firestoreRepository.habitToReturn = testHabit

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertEquals(testHabit, result)
        assertTrue(firestoreRepository.getHabitByIdCalled)
    }

    @Test
    fun `invoke should fallback to local repository when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true
        habitRepository.habitToReturn = testHabit

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertEquals(testHabit, result)
        assertTrue(firestoreRepository.getHabitByIdCalled)
        assertTrue(habitRepository.getHabitByIdCalled)
    }

    @Test
    fun `invoke should return null when both repositories fail`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true
        habitRepository.habitToReturn = null

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertNull(result)
        assertTrue(firestoreRepository.getHabitByIdCalled)
        assertTrue(habitRepository.getHabitByIdCalled)
    }

    @Test
    fun `invoke should return null when habit not found in both repositories`() = runTest {
        // Arrange - оба репозитория возвращают null
        firestoreRepository.habitToReturn = null
        habitRepository.habitToReturn = null

        // Act
        val result = getHabitByIdUseCase(testHabitId)

        // Assert
        assertNull(result)
        assertTrue(firestoreRepository.getHabitByIdCalled)
        // Локальный репозиторий НЕ вызывается, если Firestore возвращает null (а не исключение)
        assertFalse(habitRepository.getHabitByIdCalled)
    }

    // Fake implementations for testing
    private class FakeFirestoreRepository : FirestoreRepository {
        var getHabitByIdCalled = false
        var habitToReturn: Habit? = null
        var shouldThrowException = false

        override suspend fun getHabitById(habitId: String): Habit? {
            getHabitByIdCalled = true
            if (shouldThrowException) {
                throw RuntimeException("Firestore error")
            }
            return habitToReturn
        }

        override fun getUserHabits(userId: String) = throw NotImplementedError()
        override suspend fun saveHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun updateHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override suspend fun saveHabitEntry(habitEntry: HabitEntry) = throw NotImplementedError()
        override suspend fun getHabitEntry(habitId: String, userId: String, date: LocalDate) = throw NotImplementedError()
        override fun getHabitEntries(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun syncData() = throw NotImplementedError()
        override suspend fun getParticipantSettings(habitId: String, userId: String) = null
        override suspend fun setParticipantSettings(settings: HabitParticipantSettings) = throw NotImplementedError()
    }

    private class FakeHabitRepository : HabitRepository {
        var getHabitByIdCalled = false
        var habitToReturn: Habit? = null
        private val habits = mutableListOf<Habit>()

        override suspend fun getHabitById(id: String): Habit? {
            getHabitByIdCalled = true
            return habitToReturn ?: habits.find { it.id == id }
        }

        override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
            return habits.find { it.inviteCode == inviteCode && it.isActive && it.isPublic }
        }

        override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
            return flowOf(habitToReturn ?: habits.find { it.id == habitId })
        }

        override suspend fun insertHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun updateHabit(habit: Habit) = throw NotImplementedError()
        override fun getHabitsByUserId(userId: String) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override fun getAllHabits() = throw NotImplementedError()
        override fun getPersonalHabits(userId: String) = throw NotImplementedError()
        override fun getGroupHabits(userId: String) = throw NotImplementedError()
        override suspend fun addParticipantToHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun removeParticipantFromHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun getHabitParticipants(habitId: String) = throw NotImplementedError()
        override suspend fun searchHabits(userId: String, query: String) = throw NotImplementedError()
    }
}
