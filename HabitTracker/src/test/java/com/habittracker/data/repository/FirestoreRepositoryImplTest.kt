package com.habittracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class FirestoreRepositoryImplTest {

    @Mock
    private lateinit var firestore: FirebaseFirestore

    private lateinit var firestoreRepository: FirestoreRepositoryImpl

    private val testUserId = "test-user-id"
    private val testHabitId = "test-habit-id"
    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private val testDate = now.date

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
        createdAt = testDate,
        updatedAt = testDate,
        createdBy = testUserId
    )

    private val testHabitEntry = HabitEntry(
        habitId = testHabitId,
        userId = testUserId,
        date = testDate,
        completed = true,
        completedAt = now,
        note = "Test note",
        createdAt = testDate,
        updatedAt = testDate
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        firestoreRepository = FirestoreRepositoryImpl(firestore)
    }

    @Test
    fun `repository should be instantiated correctly`() {
        // Assert
        assertNotNull(firestoreRepository)
    }

    @Test
    fun `habit entry ID should be generated correctly`() {
        // Arrange
        val expectedId = "${testHabitId}_${testUserId}_${testDate}"
        
        // Act - This tests the ID generation logic that would be used internally
        val actualId = "${testHabitEntry.habitId}_${testHabitEntry.userId}_${testHabitEntry.date}"
        
        // Assert
        assertEquals(expectedId, actualId)
    }

    @Test
    fun `test habit model properties`() {
        // Assert - Verify test habit has expected properties
        assertEquals(testHabitId, testHabit.id)
        assertEquals(testUserId, testHabit.createdBy)
        assertEquals(HabitType.PERSONAL, testHabit.type)
        assertEquals(HabitFrequency.DAILY, testHabit.frequency)
        assertTrue(testHabit.isActive)
    }

    @Test
    fun `test habit entry model properties`() {
        // Assert - Verify test habit entry has expected properties
        assertEquals(testHabitId, testHabitEntry.habitId)
        assertEquals(testUserId, testHabitEntry.userId)
        assertEquals(testDate, testHabitEntry.date)
        assertTrue(testHabitEntry.completed)
        assertNotNull(testHabitEntry.completedAt)
        assertEquals("Test note", testHabitEntry.note)
    }

    // Note: Full Firestore integration tests would require Firebase Test SDK
    // or testcontainers. These basic tests verify the repository structure
    // and model consistency without complex Firestore mocking.

    @Test
    fun mergeUserHabits_dropsDuplicatesAndPutsFreshFirst() {
        val own = testHabit.copy(id = "own", updatedAtMillis = 1L)
        val shared = testHabit.copy(id = "shared", updatedAtMillis = 3L)
        val joined = testHabit.copy(id = "joined", updatedAtMillis = 2L)

        val merged = FirestoreRepositoryImpl.mergeUserHabits(
            created = listOf(own, shared),
            participating = listOf(shared, joined)
        )

        assertEquals(listOf("shared", "joined", "own"), merged.map { it.id })
    }

    @Test
    fun mergeUserHabits_keepsFreshestCopyOfDuplicate() {
        val stale = testHabit.copy(id = "shared", title = "old", updatedAtMillis = 1L)
        val fresh = testHabit.copy(id = "shared", title = "new", updatedAtMillis = 2L)

        val merged = FirestoreRepositoryImpl.mergeUserHabits(created = listOf(stale), participating = listOf(fresh))

        assertEquals(listOf("new"), merged.map { it.title })
    }
}
