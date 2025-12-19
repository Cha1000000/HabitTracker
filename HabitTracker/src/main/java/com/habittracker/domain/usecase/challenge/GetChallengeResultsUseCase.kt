package com.habittracker.domain.usecase.challenge

import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.ChallengeRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.SocialRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import javax.inject.Inject

/**
 * Результат участника в челлендже
 */
data class ChallengeParticipantResult(
    val user: User,
    val score: Int,
    val completionRate: Float,
    val currentStreak: Int,
    val totalDays: Int,
    val position: Int,
    val isWinner: Boolean = false
)

/**
 * Полные результаты челленджа
 */
data class ChallengeResults(
    val challenge: Challenge,
    val participants: List<ChallengeParticipantResult>,
    val isCompleted: Boolean,
    val winner: ChallengeParticipantResult?,
    val daysRemaining: Int,
    val totalDays: Int
)

class GetChallengeResultsUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val socialRepository: SocialRepository
) {
    /**
     * Получает результаты челленджа с подсчетом статистики участников
     * @param challengeId ID челленджа
     * @return Result с результатами челленджа или ошибкой
     */
    suspend operator fun invoke(challengeId: String): Result<ChallengeResults> {
        return try {
            // Получаем челлендж
            val challenge = challengeRepository.getChallengeById(challengeId)
                ?: return Result.failure(Exception("Челлендж не найден"))
            
            // Получаем информацию об участниках
            val participantResults = mutableListOf<ChallengeParticipantResult>()
            
            for (participantId in challenge.participants) {
                val user = socialRepository.getUserById(participantId)
                    ?: continue // Пропускаем удаленных пользователей
                
                // Получаем записи привычки для участника в период челленджа
                val entries = habitEntryRepository.getEntriesForUserAndPeriod(
                    habitId = challenge.habitId,
                    userId = participantId,
                    startDate = challenge.startDate,
                    endDate = challenge.endDate
                )
                
                // Вычисляем статистику в зависимости от типа челленджа
                val stats = calculateParticipantStats(challenge, entries)
                
                participantResults.add(
                    ChallengeParticipantResult(
                        user = user,
                        score = stats.score,
                        completionRate = stats.completionRate,
                        currentStreak = stats.currentStreak,
                        totalDays = stats.totalDays,
                        position = 0 // Будет установлено позже
                    )
                )
            }
            
            // Сортируем участников по результатам и устанавливаем позиции
            val sortedResults = when (challenge.rules.type) {
                ChallengeType.COMPLETION_RATE -> participantResults.sortedByDescending { it.completionRate }
                ChallengeType.STREAK -> participantResults.sortedByDescending { it.currentStreak }
                ChallengeType.TOTAL_DAYS -> participantResults.sortedByDescending { it.totalDays }
                ChallengeType.FIRST_TO_TARGET -> participantResults.sortedByDescending { it.score }
            }.mapIndexed { index, result ->
                result.copy(
                    position = index + 1,
                    isWinner = index == 0 && (challenge.winnerId == result.user.id || challenge.winnerId == null)
                )
            }
            
            // Определяем победителя
            val winner = sortedResults.firstOrNull()
            
            // Вычисляем оставшиеся дни
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val daysRemaining = maxOf(0, (challenge.endDate.toEpochDays() - today.toEpochDays()).toInt())
            val totalDays = (challenge.endDate.toEpochDays() - challenge.startDate.toEpochDays()).toInt() + 1
            
            val results = ChallengeResults(
                challenge = challenge,
                participants = sortedResults,
                isCompleted = challenge.status == ChallengeStatus.COMPLETED || challenge.status == ChallengeStatus.EXPIRED || challenge.endDate <= today,
                winner = winner,
                daysRemaining = daysRemaining,
                totalDays = totalDays
            )
            
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Вычисляет статистику участника в зависимости от типа челленджа
     */
    private fun calculateParticipantStats(
        challenge: Challenge,
        entries: List<com.habittracker.domain.model.HabitEntry>
    ): ParticipantStats {
        val completedEntries = entries.filter { it.completed }
        val totalDays = (challenge.endDate.toEpochDays() - challenge.startDate.toEpochDays()).toInt() + 1
        val completionRate = if (totalDays > 0) completedEntries.size.toFloat() / totalDays else 0f
        
        // Вычисляем текущую серию
        val currentStreak = calculateCurrentStreak(entries, challenge.endDate)
        
        // Вычисляем очки в зависимости от типа челленджа
        val score = when (challenge.rules.type) {
            ChallengeType.COMPLETION_RATE -> (completionRate * 100).toInt()
            ChallengeType.STREAK -> currentStreak
            ChallengeType.TOTAL_DAYS -> completedEntries.size
            ChallengeType.FIRST_TO_TARGET -> {
                if (completionRate >= challenge.rules.targetValue / 100f) {
                    challenge.rules.targetValue
                } else {
                    (completionRate * 100).toInt()
                }
            }
        }
        
        return ParticipantStats(
            score = score,
            completionRate = completionRate,
            currentStreak = currentStreak,
            totalDays = completedEntries.size
        )
    }
    
    /**
     * Вычисляет текущую серию выполнений
     */
    private fun calculateCurrentStreak(
        entries: List<com.habittracker.domain.model.HabitEntry>,
        endDate: LocalDate
    ): Int {
        val entriesByDate = entries.associateBy { it.date }
        var streak = 0
        var currentDate = endDate
        
        // Идем назад от конечной даты, пока находим выполненные дни
        while (true) {
            val entry = entriesByDate[currentDate]
            if (entry?.completed == true) {
                streak++
                currentDate = currentDate.minus(1, DateTimeUnit.DAY)
            } else {
                break
            }
        }
        
        return streak
    }
    
    /**
     * Внутренний класс для хранения статистики участника
     */
    private data class ParticipantStats(
        val score: Int,
        val completionRate: Float,
        val currentStreak: Int,
        val totalDays: Int
    )
}
