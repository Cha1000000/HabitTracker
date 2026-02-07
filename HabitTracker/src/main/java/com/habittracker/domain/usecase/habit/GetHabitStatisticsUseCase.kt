package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.DayCompletionStatus
import com.habittracker.domain.model.DayStatistics
import com.habittracker.domain.model.GroupHabitStatisticsData
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.HabitStatisticsData
import com.habittracker.domain.model.HabitStatisticsSummary
import com.habittracker.domain.model.isActiveOnDate
import com.habittracker.domain.model.isActiveOnDayOfWeek
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.FirestoreRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import javax.inject.Inject

/**
 * UseCase для получения детальной статистики выполнения привычки за период.
 * Поддерживает как личные, так и групповые привычки.
 */
class GetHabitStatisticsUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val firestoreRepository: FirestoreRepository,
    private val userRepository: UserRepository
) {
    private val today: LocalDate
        get() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /**
     * Получает статистику личной привычки за период
     */
    suspend fun getPersonalStatistics(
        habitId: String,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): HabitStatisticsData? = coroutineScope {
        val habit = getHabit(habitId) ?: return@coroutineScope null
        val settings = habitRepository.getHabitParticipantSettings(habitId, userId)
        calculateStatistics(habit, userId, startDate, endDate, settings?.seasonStartDate)
    }

    /**
     * Получает статистику групповой привычки с данными всех участников
     */
    suspend fun getGroupStatistics(
        habitId: String,
        currentUserId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): GroupHabitStatisticsData? = coroutineScope {
        val habit = getHabit(habitId) ?: return@coroutineScope null
        
        // Проверяем, является ли привычка групповой (по типу или наличию участников)
        if (!habit.isGroupHabit() && habit.participants.isEmpty()) {
            return@coroutineScope null
        }

        // Получаем статистику текущего пользователя
        val currentUserSettings = habitRepository.getHabitParticipantSettings(habitId, currentUserId)
        val currentUserStats = calculateStatistics(
            habit,
            currentUserId,
            startDate,
            endDate,
            currentUserSettings?.seasonStartDate
        )

        // Получаем список всех участников (включая создателя)
        val allParticipantIds = (habit.participants + habit.createdBy).distinct()
        
        // Получаем информацию о пользователях
        val participantsInfo = allParticipantIds.map { participantId ->
            async { userRepository.getUserById(participantId) }
        }.awaitAll().filterNotNull()

        // Получаем статистику для каждого участника
        val participantsStatistics = allParticipantIds
            .filter { it != currentUserId }
            .map { participantId ->
                async {
                    val participantSettings = habitRepository.getHabitParticipantSettings(habitId, participantId)
                    val stats = calculateStatistics(
                        habit,
                        participantId,
                        startDate,
                        endDate,
                        participantSettings?.seasonStartDate
                    )
                    participantId to stats
                }
            }
            .awaitAll()
            .toMap()

        GroupHabitStatisticsData(
            habit = habit,
            startDate = startDate,
            endDate = endDate,
            currentUserStatistics = currentUserStats,
            participantsStatistics = participantsStatistics,
            participantsInfo = participantsInfo
        )
    }

    private suspend fun getHabit(habitId: String): Habit? {
        return try {
            habitRepository.getHabitById(habitId)
                ?: firestoreRepository.getHabitById(habitId)
        } catch (_: Exception) {
            try {
                firestoreRepository.getHabitById(habitId)
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun calculateStatistics(
        habit: Habit,
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        seasonStartDate: LocalDate?
    ): HabitStatisticsData {
        // Генерируем список всех дат в периоде
        val dates = generateDateRange(startDate, endDate)
        
        val effectiveStart = if (seasonStartDate != null && seasonStartDate > startDate) {
            seasonStartDate
        } else startDate

        // Получаем все записи за период
        val entries = try {
            habitEntryRepository.getEntriesForUserAndPeriod(
                habitId = habit.id,
                userId = userId,
                startDate = effectiveStart,
                endDate = endDate
            )
        } catch (_: Exception) {
            emptyList()
        }
        
        val entriesMap = entries.associateBy { it.date }
        
        // Вычисляем статистику для каждого дня
        val dailyStatistics = dates.map { date ->
            val entry = entriesMap[date]
            val isActiveByDate = habit.isActiveOnDate(date, habit.createdAt)
            val isActiveByDayOfWeek = habit.isActiveOnDayOfWeek(date.dayOfWeek)
            val isSeasonActive = seasonStartDate == null || date >= seasonStartDate
            val isActive = isSeasonActive && isActiveByDate && isActiveByDayOfWeek
            
            val status = when {
                date > today -> DayCompletionStatus.FUTURE
                !isActive -> DayCompletionStatus.NOT_ACTIVE
                entry?.isRestDay == true -> DayCompletionStatus.REST_DAY
                entry?.completed == true -> DayCompletionStatus.COMPLETED
                habit.goalType == HabitGoalType.MEASURABLE && 
                    (entry?.value ?: 0) > 0 && 
                    (entry?.value ?: 0) < habit.targetValue -> DayCompletionStatus.PARTIAL
                else -> DayCompletionStatus.MISSED
            }
            
            DayStatistics(
                date = date,
                status = status,
                value = entry?.value ?: 0,
                targetValue = habit.targetValue,
                note = entry?.note,
                entry = entry
            )
        }
        
        // Вычисляем агрегированную статистику
        val summary = calculateSummary(dailyStatistics)
        
        return HabitStatisticsData(
            habit = habit,
            startDate = startDate,
            endDate = endDate,
            dailyStatistics = dailyStatistics,
            summary = summary
        )
    }

    private fun calculateSummary(
        dailyStatistics: List<DayStatistics>
    ): HabitStatisticsSummary {
        val totalDays = dailyStatistics.size
        val activeDays = dailyStatistics.count { 
            it.status !in listOf(DayCompletionStatus.NOT_ACTIVE, DayCompletionStatus.FUTURE) 
        }
        val completedDays = dailyStatistics.count { it.status == DayCompletionStatus.COMPLETED }
        val missedDays = dailyStatistics.count { it.status == DayCompletionStatus.MISSED }
        val restDays = dailyStatistics.count { it.status == DayCompletionStatus.REST_DAY }
        val partialDays = dailyStatistics.count { it.status == DayCompletionStatus.PARTIAL }
        
        // Процент выполнения (учитываем только активные дни, исключая выходные)
        val effectiveActiveDays = activeDays - restDays
        val completionRate = if (effectiveActiveDays > 0) {
            completedDays.toFloat() / effectiveActiveDays.toFloat()
        } else {
            0f
        }
        
        // Вычисляем серии
        val (currentStreak, bestStreak) = calculateStreaks(dailyStatistics)
        
        // Для измеримых привычек
        val totalValue = dailyStatistics.sumOf { it.value }
        val completedOrPartialDays = dailyStatistics.filter { 
            it.status in listOf(DayCompletionStatus.COMPLETED, DayCompletionStatus.PARTIAL) 
        }
        val averageValue = if (completedOrPartialDays.isNotEmpty()) {
            totalValue.toFloat() / completedOrPartialDays.size
        } else {
            0f
        }
        
        return HabitStatisticsSummary(
            totalDays = totalDays,
            activeDays = activeDays,
            completedDays = completedDays,
            missedDays = missedDays,
            restDays = restDays,
            partialDays = partialDays,
            completionRate = completionRate,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            totalValue = totalValue,
            averageValue = averageValue
        )
    }

    private fun calculateStreaks(dailyStatistics: List<DayStatistics>): Pair<Int, Int> {
        var currentStreak = 0
        var bestStreak = 0
        var tempStreak = 0
        var foundFirstNonFuture = false
        
        // Идём от конца к началу для подсчёта текущей серии
        val reversedStats = dailyStatistics.reversed()
        
        for (stat in reversedStats) {
            when (stat.status) {
                DayCompletionStatus.FUTURE -> continue
                DayCompletionStatus.NOT_ACTIVE, DayCompletionStatus.REST_DAY -> {
                    // Не прерываем серию для неактивных дней и выходных
                    foundFirstNonFuture = true
                }
                DayCompletionStatus.COMPLETED -> {
                    foundFirstNonFuture = true
                    tempStreak++
                }
                DayCompletionStatus.MISSED, DayCompletionStatus.PARTIAL -> {
                    if (foundFirstNonFuture && currentStreak == 0) {
                        currentStreak = tempStreak
                    }
                    bestStreak = maxOf(bestStreak, tempStreak)
                    tempStreak = 0
                    foundFirstNonFuture = true
                }
            }
        }
        
        // Обновляем серии в конце
        if (currentStreak == 0) {
            currentStreak = tempStreak
        }
        bestStreak = maxOf(bestStreak, tempStreak)
        
        return currentStreak to bestStreak
    }

    private fun generateDateRange(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = startDate
        while (current <= endDate) {
            dates.add(current)
            current = current.plus(1, DateTimeUnit.DAY)
        }
        return dates
    }
}
