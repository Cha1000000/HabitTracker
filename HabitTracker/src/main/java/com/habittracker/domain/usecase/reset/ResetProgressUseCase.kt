package com.habittracker.domain.usecase.reset

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.domain.repository.AchievementRepository
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.ParticipantSettingsRepository
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

class ResetProgressUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository,
    private val habitRepository: HabitRepository,
    private val habitEntryRepository: HabitEntryRepository,
    private val participantSettingsRepository: ParticipantSettingsRepository
) {
    suspend fun resetUserSeasonProgress(userId: String) {
        val user = userRepository.getUserById(userId) ?: return
        val updated = user.copy(points = 0, level = 1)
        userRepository.updateUser(updated)
    }

    suspend fun resetUserFullProgress(userId: String) {
        val user = userRepository.getUserById(userId) ?: return
        val updated = user.copy(points = 0, level = 1)
        userRepository.updateUser(updated)
        achievementRepository.resetUserAchievements(userId)
    }

    suspend fun resetPersonalHabitsSeason(userId: String) {
        val today = today()
        val habits = habitRepository.getPersonalHabits(userId).firstOrNull() ?: emptyList()
        habits.forEach { habit ->
            upsertSeasonStart(habit, userId, today)
        }
    }

    suspend fun resetPersonalHabitSeason(habitId: String, userId: String) {
        val habit = habitRepository.getHabitById(habitId) ?: return
        if (habit.isGroupHabit()) return
        upsertSeasonStart(habit, userId, today())
    }

    suspend fun resetPersonalHabitsFull(userId: String) {
        val today = today()
        val habits = habitRepository.getPersonalHabits(userId).firstOrNull() ?: emptyList()
        habits.forEach { habit ->
            habitEntryRepository.deleteEntriesForUserAndHabit(userId, habit.id)
            upsertSeasonStart(habit, userId, today)
        }
    }

    suspend fun resetPersonalHabitFull(habitId: String, userId: String) {
        val habit = habitRepository.getHabitById(habitId) ?: return
        if (habit.isGroupHabit()) return
        habitEntryRepository.deleteEntriesForUserAndHabit(userId, habitId)
        upsertSeasonStart(habit, userId, today())
    }

    suspend fun resetGroupHabitSeasonForUser(habitId: String, userId: String) {
        val habit = habitRepository.getHabitById(habitId) ?: return
        upsertSeasonStart(habit, userId, today())
    }

    suspend fun resetGroupHabitFullForUser(habitId: String, userId: String) {
        val habit = habitRepository.getHabitById(habitId) ?: return
        habitEntryRepository.deleteEntriesForUserAndHabit(userId, habitId)
        upsertSeasonStart(habit, userId, today())
    }

    suspend fun resetGroupHabitFullForAll(habitId: String, userId: String) {
        val habit = habitRepository.getHabitById(habitId) ?: return
        if (habit.createdBy != userId) {
            throw IllegalAccessException("Only habit owner can reset for all participants")
        }

        habitEntryRepository.deleteEntriesForHabit(habitId)

        val today = today()
        val allParticipants = (habit.participants + habit.createdBy).distinct()
        allParticipants.forEach { participantId ->
            upsertSeasonStart(habit, participantId, today)
        }
    }

    private suspend fun upsertSeasonStart(habit: Habit, userId: String, date: kotlinx.datetime.LocalDate) {
        val existing = participantSettingsRepository.get(habit.id, userId)
        val base = existing ?: participantSettingsRepository.upsertDefaultsFromHabit(habit, userId)
        val updated = base.copy(seasonStartDate = date, updatedAt = System.currentTimeMillis())
        participantSettingsRepository.upsert(updated)
    }

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
