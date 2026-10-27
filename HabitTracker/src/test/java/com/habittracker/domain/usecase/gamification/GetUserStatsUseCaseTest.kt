package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitParticipantSettings
import com.habittracker.domain.model.User
import com.habittracker.domain.model.gamification.UserStats
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GetUserStatsUseCaseTest {

    private fun ts(date: LocalDate, hour: Int, minute: Int): LocalDateTime {
        val start = date.atStartOfDayIn(TimeZone.currentSystemDefault())
        return start.plus((hour * 60L + minute), DateTimeUnit.MINUTE).toLocalDateTime(TimeZone.currentSystemDefault())
    }

    private fun next(date: LocalDate, target: DayOfWeek): LocalDate {
        var d = date
        repeat(7) {
            if (d.dayOfWeek == target) return d
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return date
    }

    @Test
    fun timeStats_areCalculatedCorrectly() = runTest {
        val userId = "u1"
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val saturday = next(today, DayOfWeek.SATURDAY)
        val monday = next(today, DayOfWeek.MONDAY)

        val habitId = "h1"
        val habits = listOf(
            Habit(
                id = habitId,
                title = "Workout",
                createdBy = userId,
                isActive = true,
                createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            )
        )

        val entries = listOf(
            // Утреннее выполнение в понедельник 06:30
            HabitEntry(
                habitId = habitId,
                userId = userId,
                date = monday,
                completed = true,
                completedAt = ts(monday, 6, 30),
                createdAt = monday,
                updatedAt = monday
            ),
            // Вечернее выполнение в субботу 21:00
            HabitEntry(
                habitId = habitId,
                userId = userId,
                date = saturday,
                completed = true,
                completedAt = ts(saturday, 21, 0),
                createdAt = saturday,
                updatedAt = saturday
            ),
            // Доп. утро в субботу 06:15 (чтобы hour 6 был наиболее продуктивным)
            HabitEntry(
                habitId = habitId,
                userId = userId,
                date = saturday,
                completed = true,
                completedAt = ts(saturday, 6, 15),
                createdAt = saturday,
                updatedAt = saturday
            )
        )

        val useCase = GetUserStatsUseCase(
            userRepository = object : UserRepository {
                override suspend fun insertUser(user: User) {}
                override suspend fun updateUser(user: User) {}
                override suspend fun deleteUser(userId: String) {}
                override suspend fun getUserById(userId: String): User = User(id = userId, name = "Test", level = 2, points = 150)
                override suspend fun getUserByEmail(email: String): User? = null
                override fun getAllUsers(): Flow<List<User>> = flowOf(emptyList())
                override suspend fun searchUsersByName(query: String): List<User> = emptyList()
                override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {}
                override suspend fun upsertUserRemoteAndLocal(user: User, fcmToken: String?) {}
                override fun observeUser(userId: String): Flow<User?> = flowOf(null)
                override suspend fun updateLastActive(userId: String) {}
                override suspend fun getUserFromFirestore(userId: String): User? = null
            },
            habitRepository = object : HabitRepository {
                override suspend fun insertHabit(habit: Habit) {}
                override suspend fun updateHabit(habit: Habit) {}
                override suspend fun deleteHabit(habitId: String) {}
                override suspend fun getHabitById(habitId: String): Habit? = habits.firstOrNull { it.id == habitId }
                override suspend fun getHabitByInviteCode(inviteCode: String): Habit? = null
                override fun getHabitByIdFlow(habitId: String): Flow<Habit?> = flowOf(habits.firstOrNull { it.id == habitId })
                override fun getAllHabits(): Flow<List<Habit>> = flowOf(habits)
                override fun getHabitsByUserId(userId: String): Flow<List<Habit>> = flowOf(habits)
                override fun getPersonalHabits(userId: String): Flow<List<Habit>> = flowOf(habits)
                override fun getGroupHabits(userId: String): Flow<List<Habit>> = flowOf(emptyList())
                override suspend fun addParticipantToHabit(habitId: String, userId: String) {}
                override suspend fun removeParticipantFromHabit(habitId: String, userId: String) {}
                override suspend fun getHabitParticipants(habitId: String): List<String> = emptyList()
                override suspend fun searchHabits(userId: String, query: String): List<Habit> = habits
                override fun getPublicHabits(): Flow<List<Habit>> = flowOf(emptyList())
                override suspend fun getHabitParticipantSettings(habitId: String, userId: String) = null
                override suspend fun updateHabitParticipantSettings(settings: HabitParticipantSettings) {}
                override fun observeHabitParticipantSettings(habitId: String, userId: String) = flowOf(null)
            },
            habitEntryRepository = object : HabitEntryRepository {
                override suspend fun insertEntry(entry: HabitEntry) {}
                override suspend fun updateEntry(entry: HabitEntry) {}
                override fun getEntryFlow(habitId: String, userId: String, date: LocalDate): Flow<HabitEntry?> = flowOf(null)
                override suspend fun getEntry(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
                override fun getEntriesForHabit(habitId: String): Flow<List<HabitEntry>> = flowOf(entries.filter { it.habitId == habitId })
                override fun getEntriesForUser(userId: String): Flow<List<HabitEntry>> = flowOf(entries)
                override fun getEntriesForUserAndHabit(userId: String, habitId: String): Flow<List<HabitEntry>> = flowOf(entries.filter { it.userId == userId && it.habitId == habitId })
                override suspend fun getEntriesForUserAndHabitOnce(userId: String, habitId: String): List<HabitEntry> =
                    entries.filter { it.userId == userId && it.habitId == habitId }

                override suspend fun deleteEntriesForUserAndHabit(userId: String, habitId: String) {}
                override suspend fun deleteEntriesForHabit(habitId: String) {}
                override suspend fun getEntriesForUserAndPeriod(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): List<HabitEntry> = entries
                override suspend fun getEntriesWithUsersForHabitAndDate(habitId: String, date: LocalDate) = emptyList<com.habittracker.domain.model.HabitEntryWithUser>()
                override suspend fun getCompletionRateForHabit(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Float = 0f
                override suspend fun markHabitCompleted(habitId: String, userId: String, date: LocalDate, note: String?) {}
                override suspend fun markHabitIncomplete(habitId: String, userId: String, date: LocalDate) {}
                override suspend fun setRestDay(habitId: String, userId: String, date: LocalDate, isRestDay: Boolean) {}
                override fun getDailyCompletionCounts(userId: String): Flow<Map<LocalDate, Int>> = flowOf(emptyMap())
                override suspend fun updatePhotoUrl(habitId: String, userId: String, date: LocalDate, photoUrl: String?) {}
                override suspend fun getEntryOnce(habitId: String, userId: String, date: LocalDate): HabitEntry? = null
            },
            challengeRepository = object : ChallengeRepository {
                override suspend fun insertChallenge(challenge: com.habittracker.domain.model.Challenge) {}
                override suspend fun updateChallenge(challenge: com.habittracker.domain.model.Challenge) {}
                override suspend fun deleteChallenge(challengeId: String) {}
                override suspend fun getChallengeById(challengeId: String): com.habittracker.domain.model.Challenge? = null
                override fun getAllChallenges(): Flow<List<com.habittracker.domain.model.Challenge>> = flowOf(emptyList())
                override fun getChallengesForUser(userId: String): Flow<List<com.habittracker.domain.model.Challenge>> = flowOf(emptyList())
                override fun getActiveChallenges(): Flow<List<com.habittracker.domain.model.Challenge>> = flowOf(emptyList())
                override fun getCompletedChallenges(): Flow<List<com.habittracker.domain.model.Challenge>> = flowOf(emptyList())
                override suspend fun getChallengesForHabit(habitId: String): List<com.habittracker.domain.model.Challenge> = emptyList()
                override suspend fun joinChallenge(challengeId: String, userId: String) {}
                override suspend fun leaveChallenge(challengeId: String, userId: String) {}
                override suspend fun getChallengeParticipants(challengeId: String): List<String> = emptyList()
                override suspend fun updateChallengeStatus(challengeId: String, status: String) {}
                override suspend fun getChallengeLeaderboard(challengeId: String): List<Pair<String, Int>> = emptyList()
                override suspend fun endChallenge(challengeId: String, winnerId: String?) {}
                override fun observeChallengesForUser(userId: String): Flow<List<com.habittracker.domain.model.Challenge>> = flowOf(emptyList())
                override suspend fun getCompletedDaysForUserInChallenge(challengeId: String, habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Int = 0
                override suspend fun getCurrentStreakForUserInChallenge(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Int = 0
                override suspend fun getCompletedDaysFromFirestore(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Int = 0

                override suspend fun getCurrentStreakFromFirestore(
                    habitId: String,
                    userId: String,
                    startDate: LocalDate,
                    endDate: LocalDate
                ): Int = 0
                override suspend fun acceptChallengeInvitation(challengeId: String, userId: String) {}
                override suspend fun declineChallengeInvitation(challengeId: String, userId: String) {}
                override suspend fun cancelChallenge(challengeId: String) {}
                override suspend fun addParticipantsToChallenge(challengeId: String, participantIds: List<String>) {}
                override suspend fun getTotalValueForUserInChallenge(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Int = 0
                override suspend fun getTotalValueFromFirestore(habitId: String, userId: String, startDate: LocalDate, endDate: LocalDate): Int = 0
            },
            socialRepository = object : SocialRepository {
                override suspend fun searchUsers(query: String, currentUserId: String): List<User> = emptyList()
                override suspend fun getUserById(userId: String): User? = null
                override suspend fun getUserByIdFromFirestore(userId: String): User? = getUserById(userId)
                override suspend fun updateUserFcmToken(userId: String, token: String) {}
                override suspend fun updateFriendsCount(userId: String, count: Int) {}
                override suspend fun recalculateFriendsCount(userId: String) {}

                override suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String?) =
                    Result.success(
                        com.habittracker.domain.model.createFriendRequest(
                            fromUserId = fromUserId,
                            toUserId = toUserId,
                            message = message
                        )
                    )

                override suspend fun acceptFriendRequest(requestId: String) = Result.success(
                    com.habittracker.domain.model.createFriendship(
                        userId1 = "u1",
                        userId2 = "u2",
                        requestedBy = "u1"
                    )
                )

                override suspend fun declineFriendRequest(requestId: String) = Result.success(
                    com.habittracker.domain.model.createFriendRequest(
                        fromUserId = "u1",
                        toUserId = "u2",
                        message = null
                    )
                )

                override suspend fun cancelFriendRequest(requestId: String) = Result.success(Unit)
                override suspend fun getFriendRequestById(requestId: String) = null
                override suspend fun getFriendRequestBetweenUsers(fromUserId: String, toUserId: String) = null
                override fun getIncomingFriendRequests(userId: String) = flowOf(emptyList<com.habittracker.domain.model.FriendRequestWithUsers>())
                override fun getOutgoingFriendRequests(userId: String) = flowOf(emptyList<com.habittracker.domain.model.FriendRequestWithUsers>())
                override fun getIncomingRequestsCount(userId: String) = flowOf(0)
                override suspend fun getFriendshipBetweenUsers(userId1: String, userId2: String) = null
                override suspend fun removeFriend(currentUserId: String, friendId: String) = Result.success(Unit)
                override suspend fun blockUser(currentUserId: String, userIdToBlock: String) = Result.success(
                    com.habittracker.domain.model.createFriendship(
                        userId1 = currentUserId,
                        userId2 = userIdToBlock,
                        requestedBy = currentUserId
                    )
                )
                override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String) = Result.success(
                    com.habittracker.domain.model.createFriendship(
                        userId1 = currentUserId,
                        userId2 = userIdToUnblock,
                        requestedBy = currentUserId
                    )
                )
                override fun getFriends(userId: String) = flowOf(emptyList<com.habittracker.domain.model.FriendshipWithUsers>())
                override fun getFriendsCount(userId: String) = flowOf(0)
                override suspend fun sendHabitInvitation(
                    habitId: String,
                    habitTitle: String,
                    fromUserId: String,
                    fromUserName: String,
                    toUserId: String,
                    message: String?
                ) = Result.success(
                    com.habittracker.domain.model.HabitInvitation(
                        id = "inv1",
                        habitId = habitId,
                        habitTitle = habitTitle,
                        fromUserId = fromUserId,
                        fromUserName = fromUserName,
                        toUserId = toUserId,
                        message = message,
                        status = com.habittracker.domain.model.HabitInvitationStatus.PENDING,
                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        respondedAt = null,
                        expiresAt = null
                    )
                )
                override suspend fun acceptHabitInvitation(invitationId: String) = Result.success(
                    com.habittracker.domain.model.HabitInvitation(
                        id = invitationId,
                        habitId = "h",
                        habitTitle = "title",
                        fromUserId = "u1",
                        fromUserName = "name",
                        toUserId = "u2",
                        message = null,
                        status = com.habittracker.domain.model.HabitInvitationStatus.ACCEPTED,
                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        expiresAt = null
                    )
                )
                override suspend fun declineHabitInvitation(invitationId: String) = Result.success(
                    com.habittracker.domain.model.HabitInvitation(
                        id = invitationId,
                        habitId = "h",
                        habitTitle = "title",
                        fromUserId = "u1",
                        fromUserName = "name",
                        toUserId = "u2",
                        message = null,
                        status = com.habittracker.domain.model.HabitInvitationStatus.DECLINED,
                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        respondedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        expiresAt = null
                    )
                )
                override suspend fun cancelHabitInvitation(invitationId: String) = Result.success(Unit)
                override suspend fun getHabitInvitationById(invitationId: String): com.habittracker.domain.model.HabitInvitation? = null
                override suspend fun getPendingHabitInvitation(habitId: String, userId: String): com.habittracker.domain.model.HabitInvitation? = null
                override fun getIncomingHabitInvitations(userId: String) = flowOf(emptyList<com.habittracker.domain.model.HabitInvitation>())
                override fun getOutgoingHabitInvitations(userId: String) = flowOf(emptyList<com.habittracker.domain.model.HabitInvitation>())
                override fun getHabitInvitations(habitId: String) = flowOf(emptyList<com.habittracker.domain.model.HabitInvitation>())
                override fun getPendingHabitInvitationsCount(userId: String) = flowOf(0)
                override suspend fun getActiveOutgoingInvitationsCount(userId: String) = 0
                override suspend fun expireOldHabitInvitations() {}
                override suspend fun areFriends(userId1: String, userId2: String) = false
                override suspend fun canSendFriendRequest(fromUserId: String, toUserId: String) = true
                override suspend fun canSendHabitInvitation(fromUserId: String, toUserId: String, habitId: String) = true
                override suspend fun expireOldFriendRequests() {}
                override suspend fun sendFriendRequestNotification(toUserId: String, fromUserName: String) {}
                override suspend fun sendFriendAcceptedNotification(toUserId: String, acceptedByName: String) {}
                override suspend fun sendHabitInvitationNotification(toUserId: String, fromUserName: String, habitTitle: String) {}
                override suspend fun sendHabitInvitationAcceptedNotification(toUserId: String, acceptedByName: String, habitTitle: String, habitId: String) {}
                override suspend fun sendParticipantJoinedNotification(toUserId: String, joinedUserName: String, habitTitle: String, habitId: String) {}
                override suspend fun sendNudgeNotification(toUserId: String, fromUserName: String, habitTitle: String, habitId: String) {}
                override suspend fun sendChallengeInvitationNotification(toUserId: String, fromUserName: String, challengeId: String, challengeTitle: String, habitTitle: String) {}
                override suspend fun sendChallengeJoinedNotification(toUserId: String, joinedUserName: String, challengeId: String, challengeTitle: String) {}
                override suspend fun sendChallengeLeftNotification(toUserId: String, leftUserName: String, challengeId: String, challengeTitle: String) {}
                override suspend fun sendChallengeCompletedNotification(toUserId: String, challengeId: String, challengeTitle: String, message: String, isWinner: Boolean) {}
                override suspend fun sendChallengeCancelledNotification(toUserId: String, challengeId: String, challengeTitle: String, reason: String) {}
                override suspend fun sendPushNotification(fcmToken: String, title: String, body: String, data: Map<String, String>) {}
            }
        )

        val stats: UserStats? = useCase.getUserStats(userId)
        assertNotNull(stats)
        val time = stats!!.timeStats

        // Утро: два выполнения в 6-м часу
        assertEquals(2, time.morningCompletions)
        // Вечер: одно выполнение в 21-м часу
        assertEquals(1, time.eveningCompletions)
        // Самый продуктивный час — 6
        assertEquals(6, time.mostProductiveHour)
        // Среднее время по трем выполнениями (06:30, 21:00, 06:15) = 11:15
        assertEquals("11:15", time.averageCompletionTime)
        // Выходные выполнения за 30 дней — 2 (субботние 21:00 и 06:15)
        assertEquals(2, time.weekendCompletionsLast30)
        // Консистентность: 2 различных дня из 30 ≈ 6.66% (проверяем округлённое до Int через toInt)
        // В use case возвращается Float (0..100). Проверим floor при преобразовании в метрику CONSISTENCY.
        // Здесь валидируем, что сам расчёт не нулевой и > 6.0f
        val consistency = time.consistencyScore
        assert(consistency > 6.0f)
    }
}
