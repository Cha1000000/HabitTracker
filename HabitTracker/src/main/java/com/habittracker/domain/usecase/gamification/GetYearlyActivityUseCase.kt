package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.gamification.ActivityLevel
import com.habittracker.domain.model.gamification.DailyActivityLevel
import com.habittracker.domain.repository.HabitEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import javax.inject.Inject

class GetYearlyActivityUseCase @Inject constructor(
    private val habitEntryRepository: HabitEntryRepository
) {
    operator fun invoke(userId: String): Flow<List<DailyActivityLevel>> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        // Берем данные за последний год (365 дней)
        val oneYearAgo = today.minus(365, DateTimeUnit.DAY)

        return habitEntryRepository.getDailyCompletionCounts(userId)
            .map { countsMap ->
                val activityList = mutableListOf<DailyActivityLevel>()
                
                // Генерируем список дней за последний год
                for (i in 0..365) {
                    val date = oneYearAgo.plus(i, DateTimeUnit.DAY)
                    if (date > today) break // На всякий случай
                    
                    val count = countsMap[date] ?: 0
                    val level = when (count) {
                        0 -> ActivityLevel.NONE
                        in 1..2 -> ActivityLevel.LOW
                        in 3..5 -> ActivityLevel.MEDIUM
                        in 6..9 -> ActivityLevel.HIGH
                        else -> ActivityLevel.VERY_HIGH
                    }
                    
                    activityList.add(DailyActivityLevel(date, count, level))
                }
                
                activityList
            }
    }
}
