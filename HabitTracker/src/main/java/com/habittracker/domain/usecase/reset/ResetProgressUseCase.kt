package com.habittracker.domain.usecase.reset

import javax.inject.Inject

class ResetProgressUseCase @Inject constructor() {
    suspend fun resetUserSeasonProgress(userId: String) {
        // TODO: implement
    }

    suspend fun resetUserFullProgress(userId: String) {
        // TODO: implement
    }

    suspend fun resetPersonalHabitsSeason(userId: String) {
        // TODO: implement
    }

    suspend fun resetPersonalHabitsFull(userId: String) {
        // TODO: implement
    }

    suspend fun resetGroupHabitSeasonForUser(habitId: String, userId: String) {
        // TODO: implement
    }

    suspend fun resetGroupHabitFullForUser(habitId: String, userId: String) {
        // TODO: implement
    }

    suspend fun resetGroupHabitFullForAll(habitId: String, userId: String) {
        // TODO: implement
    }
}
