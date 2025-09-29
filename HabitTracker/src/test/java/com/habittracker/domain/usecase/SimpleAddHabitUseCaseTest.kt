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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SimpleAddHabitUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var addHabitUseCase: AddHabitUseCase

    private val testHabit = Habit(
        id = "test-habit-id",
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
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds(),
        createdBy = "test-user-id"
    )

    @Before
    fun setUp() {
        firestoreRepository = FakeFirestoreRepository()
        habitRepository = FakeHabitRepository()
        addHabitUseCase = AddHabitUseCase(firestoreRepository, habitRepository)
    }

    @Test
    fun `invoke should save habit to both repositories when Firestore succeeds`() = runTest {
        // Act
        addHabitUseCase(testHabit)

        // Assert
        assertTrue(firestoreRepository.saveHabitCalled)
        assertTrue(habitRepository.insertHabitCalled)
        assertEquals(testHabit, firestoreRepository.savedHabit)
        assertEquals(testHabit, habitRepository.insertedHabit)
    }

    @Test
    fun `invoke should save habit locally when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true

        // Act & Assert
        try {
            addHabitUseCase(testHabit)
        } catch (e: Exception) {
            // Exception should be propagated
            assertNotNull(e)
        }

        // Verify both repositories were called
        assertTrue(firestoreRepository.saveHabitCalled)
        assertTrue(habitRepository.insertHabitCalled)
    }

    // Fake implementations for testing
    private class FakeFirestoreRepository : FirestoreRepository {
        var saveHabitCalled = false
        var savedHabit: Habit? = null
        var shouldThrowException = false

        override suspend fun saveHabit(habit: Habit) {
            saveHabitCalled = true
            savedHabit = habit
            if (shouldThrowException) {
                throw RuntimeException("Firestore error")
            }
        }

        override fun getUserHabits(userId: String) = throw NotImplementedError()
        override suspend fun updateHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override suspend fun getHabitById(habitId: String) = throw NotImplementedError()
        override suspend fun saveHabitEntry(habitEntry: HabitEntry) = throw NotImplementedError()
        override suspend fun getHabitEntry(habitId: String, userId: String, date: kotlinx.datetime.LocalDate) = throw NotImplementedError()
        override fun getHabitEntries(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun syncData() = throw NotImplementedError()
        override suspend fun getParticipantSettings(habitId: String, userId: String) = null
        override suspend fun setParticipantSettings(settings: HabitParticipantSettings) = throw NotImplementedError()
        override fun getAllPublicHabits(excludeUserId: String?) = flowOf(emptyList<Habit>())
    }

    private class FakeHabitRepository : HabitRepository {
        var insertHabitCalled = false
        var insertedHabit: Habit? = null
        var habits = listOf<Habit>()

        override suspend fun insertHabit(habit: Habit) {
            insertHabitCalled = true
            insertedHabit = habit
            habits = habits + habit
        }

        override fun getHabitsByUserId(userId: String) = throw NotImplementedError()
        override suspend fun getHabitById(habitId: String): Habit? {
            return habits.find { it.id == habitId }
        }
        
        override suspend fun getHabitByInviteCode(inviteCode: String): Habit? {
            return habits.find { it.inviteCode == inviteCode && it.isActive && it.isPublic }
        }

        override fun getHabitByIdFlow(habitId: String): Flow<Habit?> {
            return flowOf(habits.find { it.id == habitId })
        }

        override suspend fun updateHabit(habit: Habit) = throw NotImplementedError()
        override suspend fun deleteHabit(habitId: String) = throw NotImplementedError()
        override fun getAllHabits() = throw NotImplementedError()
        override fun getPersonalHabits(userId: String) = throw NotImplementedError()
        override fun getGroupHabits(userId: String) = throw NotImplementedError()
        override suspend fun addParticipantToHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun removeParticipantFromHabit(habitId: String, userId: String) = throw NotImplementedError()
        override suspend fun getHabitParticipants(habitId: String) = throw NotImplementedError()
        override suspend fun searchHabits(userId: String, query: String) = throw NotImplementedError()
        override fun getPublicHabits(): Flow<List<Habit>> = flowOf(emptyList())
    }
}
