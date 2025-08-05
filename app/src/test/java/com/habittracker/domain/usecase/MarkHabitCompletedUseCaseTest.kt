package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.User
import com.habittracker.data.local.CalendarDayData
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarkHabitCompletedUseCaseTest {

    private lateinit var firestoreRepository: FakeFirestoreRepository
    private lateinit var habitEntryRepository: FakeHabitEntryRepository
    private lateinit var markHabitCompletedUseCase: MarkHabitCompletedUseCase

    private val testHabitId = "test-habit-id"
    private val testUserId = "test-user-id"
    private val testDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val testNote = "Completed successfully"

    @Before
    fun setUp() {
        firestoreRepository = FakeFirestoreRepository()
        habitEntryRepository = FakeHabitEntryRepository()
        
        // Создаем минимальные fake объекты
        val habitRepository = object : HabitRepository {
            override suspend fun insertHabit(habit: Habit) = Unit
            override suspend fun updateHabit(habit: Habit) = Unit
            override suspend fun deleteHabit(habitId: String) = Unit
            override suspend fun getHabitById(habitId: String): Habit? = null
            override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = null
            override fun getHabitByIdFlow(habitId: String): Flow<Habit?> = flowOf(null)
            override fun getAllHabits(): Flow<List<Habit>> = flowOf(emptyList())
            override fun getHabitsByUserId(userId: String): Flow<List<Habit>> = flowOf(emptyList())
            override fun getPersonalHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList())
            override fun getGroupHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList())
            override suspend fun addParticipantToHabit(habitId: String, userId: String) = Unit
            override suspend fun removeParticipantFromHabit(habitId: String, userId: String) = Unit
            override suspend fun getHabitParticipants(habitId: String): List<String> = emptyList()
            override suspend fun searchHabits(userId: String, query: String): List<Habit> = emptyList()
        }
        
        val socialRepository = object : SocialRepository {
            override suspend fun searchUsers(query: String, currentUserId: String): List<User> = emptyList()
            override suspend fun getUserById(userId: String): User? = null
            override suspend fun updateUserFcmToken(userId: String, token: String) = Unit
            override suspend fun updateFriendsCount(userId: String, count: Int) = Unit
            override suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String?) = throw NotImplementedError()
            override suspend fun acceptFriendRequest(requestId: String) = throw NotImplementedError()
            override suspend fun declineFriendRequest(requestId: String) = throw NotImplementedError()
            override suspend fun cancelFriendRequest(requestId: String) = throw NotImplementedError()
            override suspend fun getFriendRequestById(requestId: String) = throw NotImplementedError()
            override suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String) = throw NotImplementedError()
            override fun getIncomingFriendRequests(userId: String) = throw NotImplementedError()
            override fun getOutgoingFriendRequests(userId: String) = throw NotImplementedError()
            override fun getIncomingRequestsCount(userId: String) = throw NotImplementedError()
            override suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String) = throw NotImplementedError()
            override suspend fun removeFriend(currentUserId: String, friendId: String) = throw NotImplementedError()
            override suspend fun blockUser(currentUserId: String, userIdToBlock: String) = throw NotImplementedError()
            override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String) = throw NotImplementedError()
            override fun getFriends(userId: String) = throw NotImplementedError()
            override fun getFriendsCount(userId: String) = throw NotImplementedError()
            override suspend fun sendHabitInvitation(habitId: String, habitTitle: String, fromUserId: String, fromUserName: String, toUserId: String, message: String?) = throw NotImplementedError()
            override suspend fun acceptHabitInvitation(invitationId: String) = throw NotImplementedError()
            override suspend fun declineHabitInvitation(invitationId: String) = throw NotImplementedError()
            override suspend fun cancelHabitInvitation(invitationId: String) = throw NotImplementedError()
            override suspend fun getHabitInvitationById(invitationId: String) = throw NotImplementedError()
            override suspend fun getPendingHabitInvitation(habitId: String, userId: String) = throw NotImplementedError()
            override fun getIncomingHabitInvitations(userId: String) = throw NotImplementedError()
            override fun getOutgoingHabitInvitations(userId: String) = throw NotImplementedError()
            override fun getHabitInvitations(habitId: String) = throw NotImplementedError()
            override fun getPendingHabitInvitationsCount(userId: String) = throw NotImplementedError()
            override suspend fun getActiveOutgoingInvitationsCount(userId: String): Int = 0
            override suspend fun expireOldHabitInvitations() = Unit
            override suspend fun areFriends(userId1: String, userId2: String): Boolean = false
            override suspend fun canSendFriendRequest(fromUserId: String, toUserId: String): Boolean = false
            override suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String): Boolean = false
            override suspend fun expireOldFriendRequests() = Unit
            override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) = Unit
            override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) = Unit
            override suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String) = Unit
            override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String) = Unit
            override suspend fun sendPushNotification(fcmToken: String, title: String, body: String, data: Map<String, String>) = Unit
        }
        
        val sendGroupHabitPushNotificationUseCase = SendGroupHabitPushNotificationUseCase(socialRepository)
        
        markHabitCompletedUseCase = MarkHabitCompletedUseCase(
            firestoreRepository = firestoreRepository,
            habitEntryRepository = habitEntryRepository,
            habitRepository = habitRepository,
            socialRepository = socialRepository,
            sendGroupHabitPushNotificationUseCase = sendGroupHabitPushNotificationUseCase
        )
    }

    @Test
    fun `should save habit entry to both repositories when Firestore succeeds`() = runTest {
        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)

        // Assert
        assertTrue(firestoreRepository.saveHabitEntryCalled)
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
        
        val firestoreEntry = firestoreRepository.savedEntry
        assertNotNull(firestoreEntry)
        assertEquals(testHabitId, firestoreEntry?.habitId)
        assertEquals(testUserId, firestoreEntry?.userId)
        assertEquals(testDate, firestoreEntry?.date)
        assertTrue(firestoreEntry?.completed == true)
        assertEquals(testNote, firestoreEntry?.note)
    }

    @Test
    fun `should save habit entry locally when Firestore fails`() = runTest {
        // Arrange
        firestoreRepository.shouldThrowException = true

        // Act & Assert - ожидаем исключение, но локальное сохранение должно произойти
        try {
            markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)
            // Если исключение не выброшено, тест должен провалиться
            org.junit.Assert.fail("Expected RuntimeException to be thrown")
        } catch (e: RuntimeException) {
            // Ожидаемое поведение - исключение выброшено
            assertEquals("Firestore error", e.message)
        }

        // Assert - оба репозитория должны быть вызваны, даже если Firestore не работает
        assertTrue(firestoreRepository.saveHabitEntryCalled)
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
    }

    @Test
    fun `should handle null note parameter`() = runTest {
        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, null)

        // Assert
        assertTrue(firestoreRepository.saveHabitEntryCalled)
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
        
        val savedEntry = firestoreRepository.savedEntry
        assertNotNull(savedEntry)
        assertNull(savedEntry?.note)
    }

    // Простые fake implementations только для основных методов
    private class FakeFirestoreRepository : FirestoreRepository {
        var saveHabitEntryCalled = false
        var savedEntry: HabitEntry? = null
        var shouldThrowException = false

        override suspend fun saveHabitEntry(habitEntry: HabitEntry) {
            saveHabitEntryCalled = true
            savedEntry = habitEntry
            if (shouldThrowException) {
                throw RuntimeException("Firestore error")
            }
        }

        // Все остальные методы - заглушки
        override fun getUserHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList())
        override suspend fun saveHabit(habit: Habit) = Unit
        override suspend fun updateHabit(habit: Habit) = Unit
        override suspend fun deleteHabit(habitId: String) = Unit
        override suspend fun getHabitById(habitId: String): Habit? = null
        override suspend fun getHabitEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
        override fun getHabitEntries(habitId: String, userId: String): Flow<List<HabitEntry>> = flowOf(emptyList())
        override suspend fun syncData() = Unit
    }

    private class FakeHabitEntryRepository : HabitEntryRepository {
        var markHabitCompletedCalled = false

        override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {
            markHabitCompletedCalled = true
        }

        // Все остальные методы - заглушки
        override suspend fun insertEntry(entry: HabitEntry) = Unit
        override suspend fun updateEntry(entry: HabitEntry) = Unit
        override fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntry?> = flowOf(null)
        override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
        override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> = flowOf(emptyList())
        override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> = flowOf(emptyList())
        override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> = flowOf(emptyList())
        override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): List<HabitEntry> = emptyList()
        override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUser> = emptyList()
        override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Float = 0f
        override suspend fun getCalendarData(userId: String, startDate: LocalDate, endDate: LocalDate): List<CalendarDayData> = emptyList()
        override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) = Unit
    }
}