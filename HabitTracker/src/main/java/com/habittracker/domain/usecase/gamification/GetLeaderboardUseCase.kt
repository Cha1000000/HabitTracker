package com.habittracker.domain.usecase.gamification

import com.habittracker.domain.model.User
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Тип лидерборда
 */
enum class LeaderboardType {
    ALL_TIME,   // Общий рейтинг по всем очкам
    WEEKLY      // Рейтинг за последнюю неделю (по выполнениям привычек)
}

/**
 * Элемент лидерборда с дополнительной информацией
 */
data class LeaderboardEntry(
    val user: User,
    val rank: Int,
    val score: Int,           // Очки или количество выполнений
    val weeklyCompletions: Int = 0  // Выполнений за неделю
)

/**
 * Use case для получения таблицы лидеров (рейтинга) среди друзей.
 */
class GetLeaderboardUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val userRepository: UserRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val habitRepository: HabitRepository
) {

    /**
     * Получает список пользователей (друзья + текущий пользователь),
     * отсортированный по количеству очков.
     */
    operator fun invoke(currentUserId: String): Flow<List<User>> {
        return invoke(currentUserId, LeaderboardType.ALL_TIME).map { entries ->
            entries.map { it.user }
        }
    }

    /**
     * Получает лидерборд с расширенной информацией
     */
    fun invoke(currentUserId: String, type: LeaderboardType): Flow<List<LeaderboardEntry>> {
        val friendsFlow = socialRepository.getFriends(currentUserId)
        val currentUserFlow = userRepository.observeUser(currentUserId)

        return combine(friendsFlow, currentUserFlow) { friends, currentUser ->
            val users = mutableListOf<User>()
            
            if (currentUser != null) {
                users.add(currentUser)
            }
            
            friends.forEach { friendshipWithUsers ->
                val friend = friendshipWithUsers.getOtherUser(currentUserId)
                if (friend != null) {
                    users.add(friend)
                }
            }
            
            when (type) {
                LeaderboardType.ALL_TIME -> {
                    users.sortedByDescending { it.points }
                        .mapIndexed { index, user ->
                            LeaderboardEntry(
                                user = user,
                                rank = index + 1,
                                score = user.points
                            )
                        }
                }
                LeaderboardType.WEEKLY -> {
                    // Для еженедельного рейтинга считаем выполнения за последние 7 дней
                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val weekAgo = today.minus(7, DateTimeUnit.DAY)
                    
                    val userCompletions = users.map { user ->
                        val completions = try {
                            habitEntryRepository.getEntriesForUser(user.id).first()
                                .filter { it.completed && it.date >= weekAgo && it.date <= today }
                                .size
                        } catch (_: Exception) { 0 }
                        user to completions
                    }
                    
                    userCompletions.sortedByDescending { it.second }
                        .mapIndexed { index, (user, completions) ->
                            LeaderboardEntry(
                                user = user,
                                rank = index + 1,
                                score = completions,
                                weeklyCompletions = completions
                            )
                        }
                }
            }
        }
    }

    /**
     * Получает рейтинг участников конкретной групповой привычки
     */
    fun getHabitLeaderboard(habitId: String): Flow<List<LeaderboardEntry>> = flow {
        val habit = habitRepository.getHabitById(habitId)
        if (habit == null || habit.participants.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekAgo = today.minus(7, DateTimeUnit.DAY)
        
        // Получаем всех участников привычки
        val participantStats = habit.participants.mapNotNull { participantId ->
            val user = userRepository.getUserById(participantId) ?: return@mapNotNull null
            
            // Считаем выполнения за последнюю неделю для этой привычки
            val entries = habitEntryRepository.getEntriesForUserAndHabit(participantId, habitId).first()
            val weeklyCompletions = entries.filter { 
                it.completed && it.date >= weekAgo && it.date <= today 
            }.size
            val totalCompletions = entries.filter { it.completed }.size
            
            Triple(user, weeklyCompletions, totalCompletions)
        }
        
        // Сортируем по недельным выполнениям, затем по общим
        val sorted = participantStats.sortedWith(
            compareByDescending<Triple<User, Int, Int>> { it.second }
                .thenByDescending { it.third }
        )
        
        emit(sorted.mapIndexed { index, (user, weekly, total) ->
            LeaderboardEntry(
                user = user,
                rank = index + 1,
                score = total,
                weeklyCompletions = weekly
            )
        })
    }
}
