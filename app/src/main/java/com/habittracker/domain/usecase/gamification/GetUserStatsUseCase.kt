package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.UserLevelInfo
import com.habittracker.domain.model.gamification.ChallengeStats
import com.habittracker.domain.model.gamification.HabitStats
import com.habittracker.domain.model.gamification.SocialStats
import com.habittracker.domain.model.gamification.StreakStats
import com.habittracker.domain.model.gamification.TimeStats
import com.habittracker.domain.model.gamification.UserStats
import com.habittracker.domain.model.getLevelProgress
import com.habittracker.domain.model.getLevelTitle
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.DayOfWeek
import javax.inject.Inject

/**
 * Use case для получения детальной статистики пользователя
 */
class GetUserStatsUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val habitRepository: HabitRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val challengeRepository: ChallengeRepository,
    private val socialRepository: SocialRepository
) {

    /**
     * Получить полную статистику пользователя
     */
    suspend fun getUserStats(userId: String): UserStats? {
        val user = userRepository.getUserById(userId) ?: return null
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        // Получаем базовые данные
        val habits = habitRepository.getHabitsByUserId(userId).firstOrNull() ?: emptyList()
        val allEntries = habitEntryRepository.getEntriesForUser(userId).firstOrNull() ?: emptyList()
        val challenges = (challengeRepository.getActiveChallenges().firstOrNull() ?: emptyList()).filter { challenge ->
            challenge.participants.contains(userId)
        }
        val friendsCount = socialRepository.getFriendsCount(userId).firstOrNull() ?: 0

        // Рассчитываем статистику
        val habitStats = calculateHabitStats(habits, allEntries, today)
        val challengeStats = calculateChallengeStats(challenges, userId)
        val streakStats = calculateStreakStats(allEntries, habits, today)
        val timeStats = calculateTimeStats(allEntries, today)
        val socialStats = SocialStats(
            friendsCount = friendsCount,
            groupHabitsCount = habits.count { it.participants.isNotEmpty() },
            challengesParticipated = challenges.size,
            challengesWon = challenges.count { it.winnerId == userId }
        )

        return UserStats(
            user = user,
            habitStats = habitStats,
            challengeStats = challengeStats,
            streakStats = streakStats,
            timeStats = timeStats,
            socialStats = socialStats,
            levelInfo = UserLevelInfo(
                currentLevel = user.level,
                currentPoints = user.points,
                pointsForNextLevel = user.level * 1000,
                pointsToNextLevel = ((user.level * 1000) - user.points).coerceAtLeast(0),
                progress = user.getLevelProgress(),
                levelTitle = user.getLevelTitle(),
                isMaxLevel = user.level >= 100
            )
        )
    }

    /**
     * Получить статистику привычек
     */
    private fun calculateHabitStats(
        habits: List<Habit>,
        entries: List<HabitEntry>,
        today: LocalDate
    ): HabitStats {
        val activeHabits = habits.filter { it.isActive }
        val completedEntries = entries.filter { it.completed }

        // Статистика за разные периоды
        val thisWeekStart = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        val thisMonthStart = today.minus(today.dayOfMonth - 1, DateTimeUnit.DAY)
        val thisYearStart = today.minus(today.dayOfYear - 1, DateTimeUnit.DAY)

        val thisWeekEntries = completedEntries.filter { it.date >= thisWeekStart }
        val thisMonthEntries = completedEntries.filter { it.date >= thisMonthStart }
        val thisYearEntries = completedEntries.filter { it.date >= thisYearStart }

        return HabitStats(
            totalHabits = habits.size,
            activeHabits = activeHabits.size,
            completedToday = completedEntries.count { it.date == today },
            totalCompletions = completedEntries.size,
            completionsThisWeek = thisWeekEntries.size,
            completionsThisMonth = thisMonthEntries.size,
            completionsThisYear = thisYearEntries.size,
            averageCompletionRate = calculateAverageCompletionRate(activeHabits, entries, today),
            mostCompletedHabit = findMostCompletedHabit(habits, completedEntries),
            perfectDays = calculatePerfectDays(activeHabits, entries, today)
        )
    }

    /**
     * Рассчитать статистику челленджей
     */
    private fun calculateChallengeStats(
        challenges: List<Challenge>,
        userId: String
    ): ChallengeStats {
        val activeChallenges = challenges.filter { it.status == ChallengeStatus.ACTIVE }
        val completedChallenges = challenges.filter { it.status == ChallengeStatus.COMPLETED }
        val wonChallenges = completedChallenges.filter { it.winnerId == userId }

        return ChallengeStats(
            totalChallenges = challenges.size,
            activeChallenges = activeChallenges.size,
            completedChallenges = completedChallenges.size,
            wonChallenges = wonChallenges.size,
            winRate = if (completedChallenges.isNotEmpty()) {
                (wonChallenges.size.toFloat() / completedChallenges.size * 100)
            } else 0f,
            createdChallenges = challenges.count { it.createdBy == userId }
        )
    }

    /**
     * Рассчитать статистику серий
     */
    private fun calculateStreakStats(
        entries: List<HabitEntry>,
        habits: List<Habit>,
        today: LocalDate
    ): StreakStats {
        val activeHabits = habits.filter { it.isActive }
        val streaks = activeHabits.map { habit ->
            calculateCurrentStreak(habit.id, entries, today)
        }

        val longestStreaks = activeHabits.map { habit ->
            calculateLongestStreak(habit.id, entries)
        }

        return StreakStats(
            currentStreak = streaks.maxOrNull() ?: 0,
            longestStreak = longestStreaks.maxOrNull() ?: 0,
            averageStreak = if (streaks.isNotEmpty()) streaks.average().toInt() else 0,
            activeStreaks = streaks.count { it > 0 },
            streaksByHabit = activeHabits.zip(streaks).associate { it.first.id to it.second }
        )
    }

    /**
     * Рассчитать временную статистику
     */
    private fun calculateTimeStats(
        entries: List<HabitEntry>,
        today: LocalDate
    ): TimeStats {
        val completedEntries = entries.filter { it.completed && it.completedAt != null }

        val morningEntries = completedEntries.filter { entry ->
            val hour = Instant.fromEpochMilliseconds(entry.completedAt!!)
                .toLocalDateTime(TimeZone.currentSystemDefault()).hour
            hour in 5..11
        }

        val afternoonEntries = completedEntries.filter { entry ->
            val hour = Instant.fromEpochMilliseconds(entry.completedAt!!)
                .toLocalDateTime(TimeZone.currentSystemDefault()).hour
            hour in 12..17
        }

        val eveningEntries = completedEntries.filter { entry ->
            val hour = Instant.fromEpochMilliseconds(entry.completedAt!!)
                .toLocalDateTime(TimeZone.currentSystemDefault()).hour
            hour in 18..22
        }

        val last30Days = today.minus(30, DateTimeUnit.DAY)
        val recentEntries = completedEntries.filter { it.date >= last30Days }
        val recentWeekendEntries = recentEntries.filter { e ->
            val dow = e.date.dayOfWeek
            dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
        }

        return TimeStats(
            morningCompletions = morningEntries.size,
            afternoonCompletions = afternoonEntries.size,
            eveningCompletions = eveningEntries.size,
            averageCompletionTime = calculateAverageCompletionTime(completedEntries),
            mostProductiveHour = findMostProductiveHour(completedEntries),
            consistencyScore = calculateConsistencyScore(recentEntries),
            weekendCompletionsLast30 = recentWeekendEntries.size
        )
    }

    /**
     * Рассчитать средний процент выполнения
     */
    private fun calculateAverageCompletionRate(
        habits: List<Habit>,
        entries: List<HabitEntry>,
        today: LocalDate
    ): Float {
        if (habits.isEmpty()) return 0f

        val last30Days = today.minus(30, DateTimeUnit.DAY)
        val recentEntries = entries.filter { it.date >= last30Days }

        val completionRates = habits.map { habit ->
            val habitEntries = recentEntries.filter { it.habitId == habit.id }
            val totalDays = 30
            val completedDays = habitEntries.count { it.completed }
            (completedDays.toFloat() / totalDays * 100)
        }

        return completionRates.average().toFloat()
    }

    /**
     * Найти самую выполняемую привычку
     */
    private fun findMostCompletedHabit(
        habits: List<Habit>,
        entries: List<HabitEntry>
    ): String? {
        val completionCounts = habits.associate { habit ->
            habit.id to entries.count { it.habitId == habit.id && it.completed }
        }

        val mostCompletedHabitId = completionCounts.maxByOrNull { it.value }?.key
        return habits.find { it.id == mostCompletedHabitId }?.title
    }

    /**
     * Рассчитать количество идеальных дней
     */
    private fun calculatePerfectDays(
        habits: List<Habit>,
        entries: List<HabitEntry>,
        today: LocalDate
    ): Int {
        if (habits.isEmpty()) return 0

        val last30Days = today.minus(30, DateTimeUnit.DAY)
        var perfectDays = 0

        for (i in 0..29) {
            val date = last30Days.plus(i, DateTimeUnit.DAY)
            val dayEntries = entries.filter { it.date == date }
            val completedHabits = dayEntries.count { it.completed }

            if (completedHabits == habits.size && habits.isNotEmpty()) {
                perfectDays++
            }
        }

        return perfectDays
    }

    /**
     * Рассчитать текущую серию для привычки
     */
    private fun calculateCurrentStreak(
        habitId: String,
        entries: List<HabitEntry>,
        today: LocalDate
    ): Int {
        val habitEntries = entries.filter { it.habitId == habitId }
            .sortedByDescending { it.date }

        var streak = 0
        var currentDate = today

        for (entry in habitEntries) {
            if (entry.date == currentDate && entry.completed) {
                streak++
                currentDate = currentDate.minus(1, DateTimeUnit.DAY)
            } else if (entry.date < currentDate) {
                break
            }
        }

        return streak
    }

    /**
     * Рассчитать самую длинную серию для привычки
     */
    private fun calculateLongestStreak(
        habitId: String,
        entries: List<HabitEntry>
    ): Int {
        val habitEntries = entries.filter { it.habitId == habitId && it.completed }
            .sortedBy { it.date }

        if (habitEntries.isEmpty()) return 0

        var maxStreak = 1
        var currentStreak = 1

        for (i in 1 until habitEntries.size) {
            val prevDate = habitEntries[i - 1].date
            val currentDate = habitEntries[i].date

            if (currentDate == prevDate.plus(1, DateTimeUnit.DAY)) {
                currentStreak++
                maxStreak = maxOf(maxStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return maxStreak
    }

    /**
     * Рассчитать среднее время выполнения
     */
    private fun calculateAverageCompletionTime(entries: List<HabitEntry>): String? {
        if (entries.isEmpty()) return null

        val times = entries.mapNotNull { it.completedAt }
        if (times.isEmpty()) return null

        val timeZone = TimeZone.currentSystemDefault()
        val minutesSinceMidnight = times.map { ts ->
            val ldt = Instant.fromEpochMilliseconds(ts).toLocalDateTime(timeZone)
            ldt.hour * 60 + ldt.minute
        }

        val avgMinutes = minutesSinceMidnight.average().toInt()
        val hours = (avgMinutes / 60).coerceIn(0, 23)
        val minutes = (avgMinutes % 60).coerceIn(0, 59)
        val hh = hours.toString().padStart(2, '0')
        val mm = minutes.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    /**
     * Найти самый продуктивный час
     */
    private fun findMostProductiveHour(entries: List<HabitEntry>): Int? {
        if (entries.isEmpty()) return null
        val timeZone = TimeZone.currentSystemDefault()
        val counts = IntArray(24)
        entries.forEach { e ->
            val ts = e.completedAt ?: return@forEach
            val hour = Instant.fromEpochMilliseconds(ts).toLocalDateTime(timeZone).hour
            counts[hour] += 1
        }
        val max = counts.maxOrNull() ?: return null
        if (max == 0) return null
        // выбрать самый ранний час при равенстве
        return counts.indexOfFirst { it == max }
    }

    /**
     * Рассчитать оценку постоянства (0-100)
     */
    private fun calculateConsistencyScore(entries: List<HabitEntry>): Float {
        if (entries.isEmpty()) return 0f
        // entries уже за последние 30 дней
        val daysWithCompletion = entries
            .filter { it.completed }
            .map { it.date }
            .toSet()

        val totalDays = 30f
        return if (totalDays > 0) (daysWithCompletion.size / totalDays * 100f) else 0f
    }
}
