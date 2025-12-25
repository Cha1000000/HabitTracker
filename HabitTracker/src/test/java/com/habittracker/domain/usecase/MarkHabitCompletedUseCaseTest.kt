package com.habittracker.domain.usecase

import com.habittracker.data.sync.SyncManager
import com.habittracker.data.sync.SyncWorkScheduler
import com.habittracker.domain.model.FriendRequest
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.Friendship
import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitEntryWithUser
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.domain.model.gamification.GamificationEvent
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.GroupHabitNotificationRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.usecase.challenge.UpdateChallengeProgressUseCase
import com.habittracker.domain.usecase.gamification.CalculatePointsUseCase
import com.habittracker.domain.usecase.gamification.CheckAchievementsUseCase
import com.habittracker.domain.usecase.gamification.UpdateUserPointsUseCase
import com.habittracker.domain.usecase.notification.SendGroupHabitPushNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class MarkHabitCompletedUseCaseTest {

    private lateinit var habitEntryRepository: FakeHabitEntryRepository
    private lateinit var updateUserPointsUseCase: FakeUpdateUserPointsUseCase
    private lateinit var habitRepository: FakeHabitRepository
    private lateinit var socialRepository: FakeSocialRepository
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var notificationRepository: GroupHabitNotificationRepository
    private lateinit var sendGroupHabitPushNotificationUseCase: SendGroupHabitPushNotificationUseCase
    private lateinit var markHabitCompletedUseCase: MarkHabitCompletedUseCase
    private lateinit var syncManager: SyncManager
    private lateinit var syncWorkScheduler: SyncWorkScheduler
    private lateinit var updateChallengeProgressUseCase: UpdateChallengeProgressUseCase
    private lateinit var calculatePointsUseCase: CalculatePointsUseCase
    private lateinit var checkAchievementsUseCase: CheckAchievementsUseCase

    private val testHabitId = "habit1"
    private val testUserId = "user1"
    private val testDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val testNote = "Well done!"

    @Before
    fun setUp() {
        habitEntryRepository = FakeHabitEntryRepository()
        socialRepository = FakeSocialRepository()
        updateUserPointsUseCase = FakeUpdateUserPointsUseCase(FakeUserRepository(), socialRepository)
        habitRepository = FakeHabitRepository()
        firestoreRepository = mock()
        notificationRepository = mock()
        sendGroupHabitPushNotificationUseCase = SendGroupHabitPushNotificationUseCase(
            socialRepository, 
            habitRepository, 
            firestoreRepository,
            notificationRepository
        )
        syncManager = mock()
        syncWorkScheduler = mock()
        updateChallengeProgressUseCase = mock()
        calculatePointsUseCase = mock()
        checkAchievementsUseCase = mock()
    }

    private fun setupUseCase(scope: CoroutineScope) {
        markHabitCompletedUseCase = MarkHabitCompletedUseCase(
            habitEntryRepository = habitEntryRepository,
            updateUserPointsUseCase = updateUserPointsUseCase,
            calculatePointsUseCase = calculatePointsUseCase,
            checkAchievementsUseCase = checkAchievementsUseCase,
            sendGroupHabitPushNotificationUseCase = sendGroupHabitPushNotificationUseCase,
            updateChallengeProgressUseCase = updateChallengeProgressUseCase,
            coroutineScope = scope,
            syncManager = syncManager,
            syncWorkScheduler = syncWorkScheduler
        )
    }

    @Test
    fun `should write local and schedule sync when success`() = runTest {
        setupUseCase(this)

        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)

        // Assert - repository was called
        assertTrue(habitEntryRepository.markHabitCompletedCalled)
        // one-time sync scheduled
        verify(syncWorkScheduler).scheduleOneTimeSync()
    }

    @Test
    fun `should save locally and still schedule sync when outbox enqueue fails`() = runTest {
        setupUseCase(this)
        // Arrange: make enqueueUpsert throw
        runBlocking {
            org.mockito.kotlin.whenever(
                syncManager.enqueueUpsert(
                    entityType = any(),
                    entityId = any(),
                    payloadJson = any(),
                    updatedAt = any(),
                    operationId = any()
                )
            ).thenThrow(RuntimeException("Outbox error"))
        }
        
        // Act
        markHabitCompletedUseCase(testHabitId, testUserId, testDate, testNote)
        advanceUntilIdle() // Дождаться завершения всех корутин

        assertTrue(habitEntryRepository.markHabitCompletedCalled)
        // scheduleOneTimeSync still invoked in finally block
        verify(syncWorkScheduler).scheduleOneTimeSync()
    }

    @Test
    fun `should add points for completion`() = runTest {
        setupUseCase(this)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        markHabitCompletedUseCase(testHabitId, testUserId, today)

        advanceUntilIdle() // Выполняем все ожидающие корутины

        assertTrue(updateUserPointsUseCase.invokeCalled)
    }
}

// Fake Implementations for testing

class FakeHabitEntryRepository : HabitEntryRepository {
    var markHabitCompletedCalled = false

    override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {
        markHabitCompletedCalled = true
    }

    override suspend fun insertEntry(entry: HabitEntry) {}
    override suspend fun updateEntry(entry: HabitEntry) {}
    override fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntry?> = flowOf(null)
    override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
    override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> = flowOf(emptyList<HabitEntry>())
    override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> = flowOf(emptyList<HabitEntry>())
    override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> = flowOf(emptyList<HabitEntry>())
    override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): List<HabitEntry> = emptyList()
    override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate): List<HabitEntryWithUser> = emptyList()
    override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Float = 0.0f
    override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) {}
}

class FakeUpdateUserPointsUseCase(
    private val userRepository: UserRepository,
    private val socialRepository: com.habittracker.domain.repository.SocialRepository
) : UpdateUserPointsUseCase(userRepository, socialRepository) {
    var invokeCalled = false
    override suspend fun invoke(userId: String, event: GamificationEvent): Result<Unit> {
        invokeCalled = true
        return Result.success(Unit)
    }
}

class FakeHabitRepository : HabitRepository {

    override suspend fun getHabitById(habitId: String): Habit? = Habit(
        id = habitId,
        title = "Test Habit",
        createdBy = "testUserId",
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        color = "#FFFFFF",
        type = HabitType.PERSONAL
    )

    override suspend fun insertHabit(habit: Habit) {}
    override suspend fun updateHabit(habit: Habit) {}
    override suspend fun deleteHabit(habitId: String) {}
    override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = null
    override fun getHabitByIdFlow(habitId: String): Flow<Habit?> = flowOf(null)
    override fun getAllHabits(): Flow<List<Habit>> = flowOf(emptyList<Habit>())
    override fun getHabitsByUserId(userId: String): Flow<List<Habit>> = flowOf(emptyList<Habit>())
    override fun getPersonalHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList<Habit>())
    override fun getGroupHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList<Habit>())
    override suspend fun addParticipantToHabit(habitId: String, userId: String) {}
    override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {}
    override suspend fun getHabitParticipants(habitId: String): List<String> = emptyList()
    override suspend fun searchHabits(userId: String, query: String): List<Habit> = emptyList()
    override fun getPublicHabits(): Flow<List<Habit>> = flowOf(emptyList())
    override suspend fun getHabitParticipantSettings(habitId: String, userId: String) = null
    override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) = throw NotImplementedError()
    override fun observeHabitParticipantSettings(habitId: String, userId: String) = flowOf(null)
}

class FakeSocialRepository : SocialRepository {
    override suspend fun getUserById(userId: String): User? = User(id = userId, name = "Test User", email = "test@test.com", createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date, lastActiveAt = Clock.System.now().toEpochMilliseconds())
    override suspend fun getUserByIdFromFirestore(userId: String): User? = getUserById(userId)
    override suspend fun searchUsers(query: String, currentUserId: String): List<User> = emptyList()
    override suspend fun updateUserFcmToken(userId: String, token: String) {}
    override suspend fun updateFriendsCount(userId: String, count: Int) {}
    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String?): Result<FriendRequest> = Result.failure(NotImplementedError())
    override suspend fun acceptFriendRequest(requestId: String): Result<Friendship> = Result.failure(NotImplementedError())
    override suspend fun declineFriendRequest(requestId: String): Result<FriendRequest> = Result.failure(NotImplementedError())
    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getFriendRequestById(requestId: String): FriendRequest? = null
    override suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String): FriendRequest? = null
    override fun getIncomingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> = flowOf(emptyList<FriendRequestWithUsers>())
    override fun getOutgoingFriendRequests(userId: String): Flow<List<FriendRequestWithUsers>> = flowOf(emptyList<FriendRequestWithUsers>())
    override fun getIncomingRequestsCount(userId: String): Flow<Int> = flowOf(0)
    override suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String): Friendship? = null
    override suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit> = Result.success(Unit)
    override suspend fun blockUser(currentUserId: String, userIdToBlock: String): Result<Friendship> = Result.failure(NotImplementedError())
    override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String): Result<Friendship> = Result.failure(NotImplementedError())
    override fun getFriends(userId: String): Flow<List<FriendshipWithUsers>> = flowOf(emptyList<FriendshipWithUsers>())
    override fun getFriendsCount(userId: String): Flow<Int> = flowOf(0)
    override suspend fun sendHabitInvitation(habitId: String, habitTitle: String, fromUserId: String, fromUserName: String, toUserId: String, message: String?): Result<HabitInvitation> = Result.failure(NotImplementedError())
    override suspend fun acceptHabitInvitation(invitationId: String): Result<HabitInvitation> = Result.failure(NotImplementedError())
    override suspend fun declineHabitInvitation(invitationId: String): Result<HabitInvitation> = Result.failure(NotImplementedError())
    override suspend fun cancelHabitInvitation(invitationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getHabitInvitationById(invitationId: String): HabitInvitation? = null
    override suspend fun getPendingHabitInvitation(habitId: String, userId: String): HabitInvitation? = null
    override fun getIncomingHabitInvitations(userId: String): Flow<List<HabitInvitation>> = flowOf(emptyList<HabitInvitation>())
    override fun getOutgoingHabitInvitations(userId: String): Flow<List<HabitInvitation>> = flowOf(emptyList<HabitInvitation>())
    override fun getHabitInvitations(habitId: String): Flow<List<HabitInvitation>> = flowOf(emptyList<HabitInvitation>())
    override fun getPendingHabitInvitationsCount(userId: String): Flow<Int> = flowOf(0)
    override suspend fun getActiveOutgoingInvitationsCount(userId: String): Int = 0
    override suspend fun expireOldHabitInvitations() {}
    override suspend fun areFriends(userId1: String, userId2: String): Boolean = false
    override suspend fun canSendFriendRequest(fromUserId: String, toUserId: String): Boolean = false
    override suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String): Boolean = false
    override suspend fun expireOldFriendRequests() {}
    override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) {}
    override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) {}
    override suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String) {}
    override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String, habitId: String) {}
    override suspend fun sendParticipantJoinedNotification(toUserId: String, joinedUserName: String, habitTitle: String, habitId: String) {}
    override suspend fun sendPushNotification(fcmToken: String, title: String, body: String, data: Map<String, String>) {}
    override suspend fun sendChallengeInvitationNotification(toUserId: String, fromUserName: String, challengeId: String, challengeTitle: String, habitTitle: String) {}
    override suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeId: String, challengeTitle: String) {}
    override suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeId: String, challengeTitle: String) {}
    override suspend fun sendChallengeCompletedNotification(toUserId: String, challengeId: String, challengeTitle: String, message: String, isWinner: Boolean) {}
    override suspend fun sendChallengeCancelledNotification(toUserId: String, challengeId: String, challengeTitle: String, reason: String) {}
}

class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()

    init {
        val testUser = User(
            id = "test-user-id",
            name = "Test User",
            email = "test@test.com",
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            lastActiveAt = Clock.System.now().toEpochMilliseconds()
        )
        users[testUser.id] = testUser
    }

    override suspend fun insertUser(user: User) {
        users[user.id] = user
    }

    override suspend fun updateUser(user: User) {
        users[user.id] = user
    }

    override suspend fun deleteUser(userId: String) {
        users.remove(userId)
    }

    override suspend fun getUserById(userId: String): User? = users[userId]

    override suspend fun getUserByEmail(email: String): User? = users.values.find { it.email == email }

    override fun getAllUsers(): Flow<List<User>> = flowOf(users.values.toList())

    override suspend fun searchUsersByName(query: String): List<User> = emptyList()

    override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {}

    override suspend fun upsertUserRemoteAndLocal(user: User, fcmToken: String?) { /* no-op for tests */ }

    override fun observeUser(userId: String): Flow<User?> = flowOf(users[userId])

    override suspend fun backfillAvatarThumbnails(): Result<Int> = Result.success(0)

    override suspend fun updateLastActive(userId: String) {}
}