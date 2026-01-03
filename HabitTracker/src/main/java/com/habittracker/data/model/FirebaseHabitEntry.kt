package com.habittracker.data.model

import com.habittracker.domain.model.HabitEntry
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Модель HabitEntry для Firebase Firestore
 * Firebase не поддерживает kotlinx.datetime.LocalDate, поэтому используем String
 */
data class FirebaseHabitEntry(
    val habitId: String = "",
    val userId: String = "",
    val date: String = "", // Дата в формате "yyyy-MM-dd"
    val completed: Boolean = false,
    val completedAt: String? = null, // LocalDateTime в ISO формате
    val value: Int = 1,
    val note: String? = "",
    val isRestDay: Boolean = false,
    val createdAt: String = "", // LocalDate в ISO формате
    val updatedAt: String = "" // LocalDate в ISO формате
) {
    // Конструктор без аргументов требуется для Firebase
    constructor() : this("", "", "", false, null, 1, "", false, "", "")
    
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
                completedAt = entry.completedAt?.toString(), // LocalDateTime -> String
                value = entry.value,
                note = entry.note,
                isRestDay = entry.isRestDay,
                createdAt = entry.createdAt.toString(), // LocalDate -> String
                updatedAt = entry.updatedAt.toString() // LocalDate -> String
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
                    } catch (_: Exception) {
                        LocalDate(2024, 1, 1) // fallback значение
                    }
                } else {
                    LocalDate(2024, 1, 1)
                },
                completed = completed,
                completedAt = completedAt?.let { 
                    try { LocalDateTime.parse(it) } 
                    catch (_: Exception) { null }
                },
                value = value,
                note = note,
                isRestDay = isRestDay,
                createdAt = try { LocalDate.parse(createdAt) } catch (_: Exception) {
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date 
                },
                updatedAt = try { LocalDate.parse(updatedAt) } catch (_: Exception) {
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date 
                }
            )
        }
    }
}
