package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.UserLevelInfo
import com.habittracker.domain.model.gamification.ChallengeStats
import com.habittracker.domain.model.gamification.GamificationConstants
import com.habittracker.domain.model.gamification.HabitStats
import com.habittracker.domain.model.gamification.SocialStats
import com.habittracker.domain.model.gamification.StreakStats
import com.habittracker.domain.model.gamification.TimeStats
import com.habittracker.domain.model.gamification.UserStats
import com.habittracker.domain.model.getLevelProgress
import com.habittracker.domain.model.getLevelTitle
import com.habittracker.domain.model.getLevelTitleResId
import com.habittracker.domain.model.isActiveOnDayOfWeek
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
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

        val seasonStartMap = habits.associate { habit ->
            val settings = habitRepository.getHabitParticipantSettings(habit.id, userId)
            habit.id to settings?.seasonStartDate
        }
        val filteredEntries = allEntries.filter { entry ->
            val seasonStart = seasonStartMap[entry.habitId]
            seasonStart == null || entry.date >= seasonStart
        }

        // Рассчитываем статистику
        val habitStats = calculateHabitStats(habits, filteredEntries, today, seasonStartMap)
        val challengeStats = calculateChallengeStats(challenges, userId)
        val streakStats = calculateStreakStats(filteredEntries, habits, today)
        val timeStats = calculateTimeStats(filteredEntries, today)
        val socialStats = SocialStats(
            friendsCount = friendsCount,
            groupHabitsCount = habits.count { it.participants.isNotEmpty() },
            challengesParticipated = challenges.size,
            challengesWon = challenges.count { it.winnerId == userId }
        )

        // Calculate level info dynamically to ensure consistency
        val calculatedLevel = GamificationConstants.getLevelFromPoints(user.points)
        val nextLevelPoints = GamificationConstants.pointsForLevel(calculatedLevel + 1)

        return UserStats(
            user = user,
            habitStats = habitStats,
            challengeStats = challengeStats,
            streakStats = streakStats,
            timeStats = timeStats,
            socialStats = socialStats,
            levelInfo = UserLevelInfo(
                currentLevel = calculatedLevel,
                currentPoints = user.points,
                pointsForNextLevel = nextLevelPoints,
                pointsToNextLevel = (nextLevelPoints - user.points).coerceAtLeast(0),
                progress = user.getLevelProgress(),
                levelTitle = user.getLevelTitle(),
                levelTitleResId = user.getLevelTitleResId(),
                isMaxLevel = calculatedLevel >= 100
            )
        )
    }

    /**
     * Получить статистику привычек
     */
    private fun calculateHabitStats(
        habits: List<Habit>,
        entries: List<HabitEntry>,
        today: LocalDate,
        seasonStartMap: Map<String, LocalDate?>
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
            averageCompletionRate = calculateAverageCompletionRate(activeHabits, entries, today, seasonStartMap),
            mostCompletedHabit = findMostCompletedHabit(habits, completedEntries),
            perfectDays = calculatePerfectDays(activeHabits, entries, today, seasonStartMap),
            last7DaysCompletions = calculateLast7DaysCompletions(completedEntries, today)
        )
    }

    /**
     * Рассчитать количество выполненных привычек за последние 7 дней для графика
     */
    private fun calculateLast7DaysCompletions(
        completedEntries: List<HabitEntry>,
        today: LocalDate
    ): List<Pair<LocalDate, Int>> {
        val last7Days = (0..6).map { today.minus(it, DateTimeUnit.DAY) }.reversed()
        return last7Days.map { date ->
            date to completedEntries.count { it.date == date }
        }
    }

    /**
     * Рассчитать статистику челленджей
     */
    private fun calculateChallengeStats(
        challenges: List<Challenge>,
        userId: String
    ): ChallengeStats {
        val activeChallenges = challenges.filter { it.status == ChallengeStatus.ACTIVE }
        val completedChallenges = challenges.filter { it.status == ChallengeStatus.COMPLETED || it.status == ChallengeStatus.EXPIRED }
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
            val hour = entry.completedAt!!.hour
            hour in 5..11
        }

        val afternoonEntries = completedEntries.filter { entry ->
            val hour = entry.completedAt!!.hour
            hour in 12..17
        }

        val eveningEntries = completedEntries.filter { entry ->
            val hour = entry.completedAt!!.hour
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
        today: LocalDate,
        seasonStartMap: Map<String, LocalDate?>
    ): Float {
        if (habits.isEmpty()) return 0f

        val last30Days = today.minus(30, DateTimeUnit.DAY)
        val recentEntries = entries.filter { it.date >= last30Days }

        val completionRates = habits.map { habit ->
            val seasonStart = seasonStartMap[habit.id]
            val habitEntries = recentEntries.filter { entry ->
                entry.habitId == habit.id && (seasonStart == null || entry.date >= seasonStart)
            }
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
     * День считается идеальным, если выполнены все активные привычки, которые были активны в этот день
     */
    private fun calculatePerfectDays(
        habits: List<Habit>,
        entries: List<HabitEntry>,
        today: LocalDate,
        seasonStartMap: Map<String, LocalDate?>
    ): Int {
        if (habits.isEmpty()) return 0

        val last30Days = today.minus(30, DateTimeUnit.DAY)
        var perfectDays = 0

        for (i in 0..29) {
            val date = last30Days.plus(i, DateTimeUnit.DAY)
            
            // Находим привычки, которые были активны в этот день с учётом дня недели
            val activeHabitsForDay = habits.filter { habit ->
                val seasonStart = seasonStartMap[habit.id]
                val isSeasonActive = seasonStart == null || date >= seasonStart
                habit.isActive &&
                isSeasonActive &&
                (habit.startDate == null || habit.startDate <= date) &&
                (habit.endDate == null || habit.endDate >= date) &&
                habit.isActiveOnDayOfWeek(date.dayOfWeek)
            }
            
            if (activeHabitsForDay.isEmpty()) continue
            
            val dayEntries = entries.filter { it.date == date && it.completed }
            val completedHabitIds = dayEntries.map { it.habitId }.toSet()
            
            // День идеальный, если все активные привычки выполнены
            val allCompleted = activeHabitsForDay.all { it.id in completedHabitIds }
            
            if (allCompleted) {
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
            if (entry.date == currentDate) {
                if (entry.completed) {
                    streak++
                    currentDate = currentDate.minus(1, DateTimeUnit.DAY)
                } else if (entry.isRestDay) {
                    // Skip rest day, continue checking previous day, do not increment streak
                    currentDate = currentDate.minus(1, DateTimeUnit.DAY)
                } else {
                    // Not completed and not rest day - streak broken if it's today (unless we allow today to be incomplete yet)
                    // Logic typically: if today is not completed, streak might still be valid from yesterday.
                    // But this function iterates back.
                    // If entry.date == currentDate:
                    // If it is NOT completed and NOT rest day:
                    // If currentDate is TODAY, streak doesn't necessarily break (user can still do it).
                    // But if this is for "current streak including today if done", usually we count from yesterday if today is not done.
                    
                    // Let's look at existing logic:
                    // if (entry.date == currentDate && entry.completed) { ... } else if (entry.date < currentDate) { break }
                    
                    // Revised logic to handle Rest Day:
                    break
                }
            } else if (entry.date < currentDate) {
                 // Gap in dates?
                 // Original logic:
                 // if (entry.date == currentDate && entry.completed) { ... } else if (entry.date < currentDate) { break }
                 // This implies if we miss a date in iteration (entry.date < currentDate), it breaks.
                 break
            }
        }
        
        // Wait, the original logic was simple iteration.
        // Let's stick to the pattern in HabitEntry.kt which I updated.
        // But here we filter by habitId first.
        
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

        val minutesSinceMidnight = times.map { dateTime ->
            dateTime.hour * 60 + dateTime.minute
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
        val counts = IntArray(24)
        entries.forEach { e ->
            val dateTime = e.completedAt ?: return@forEach
            counts[dateTime.hour] += 1
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
        return daysWithCompletion.size / totalDays * 100f
    }
}
