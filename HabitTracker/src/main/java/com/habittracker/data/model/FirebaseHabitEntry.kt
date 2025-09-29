package com.habittracker.data.model

import com.habittracker.domain.model.HabitEntry
import kotlinx.datetime.LocalDate

/**
 * Модель HabitEntry для Firebase Firestore
 * Firebase не поддерживает kotlinx.datetime.LocalDate, поэтому используем String
 */
data class FirebaseHabitEntry(
    val habitId: String = "",
    val userId: String = "",
    val date: String = "", // Дата в формате "yyyy-MM-dd"
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val value: Int = 1,
    val note: String? = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Конструктор без аргументов требуется для Firebase
    constructor() : this("", "", "", false, null, 1, "", System.currentTimeMillis(), System.currentTimeMillis())
    
    companion object {
        /**
         * Конвертация из domain модели в Firebase модель
         */
        fun fromDomain(entry: HabitEntry): FirebaseHabitEntry {
            return FirebaseHabitEntry(
                habitId = entry.habitId,
                userId = entry.userId,
                date = entry.date.toString(), // LocalDate -> String
                completed = entry.completed,
                completedAt = entry.completedAt,
                value = entry.value,
                note = entry.note,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt
            )
        }
        
        /**
         * Конвертация из Firebase модели в domain модель
         */
        fun FirebaseHabitEntry.toDomain(): HabitEntry {
            return HabitEntry(
                habitId = habitId,
                userId = userId,
                date = if (date.isNotEmpty()) {
                    try {
                        LocalDate.parse(date)
                    } catch (e: Exception) {
                        LocalDate(2024, 1, 1) // fallback значение
                    }
                } else {
                    LocalDate(2024, 1, 1)
                },
                completed = completed,
                completedAt = completedAt,
                value = value,
                note = note,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
