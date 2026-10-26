package com.habittracker.data.model

import com.google.firebase.firestore.PropertyName
import com.habittracker.domain.model.HabitEntry
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Модель HabitEntry для Firebase Firestore
 * Firebase не поддерживает kotlinx.datetime.LocalDate, поэтому используем String
 */
data class FirebaseHabitEntry(
    var habitId: String = "",
    var userId: String = "",
    var date: String = "", // Дата в формате "yyyy-MM-dd"
    var completed: Boolean = false,
    var completedAt: String? = null, // LocalDateTime в ISO формате
    var value: Int = 1,
    var note: String? = "",
    @get:PropertyName("isRestDay") @set:PropertyName("isRestDay")
    var restDay: Boolean = false,
    var streakBonusAwardedDays: Int? = null,
    var photoUrl: String? = null, // URL фото-подтверждения
    var createdAt: String = "", // LocalDate в ISO формате
    var updatedAt: String = "" // LocalDate в ISO формате
) {
    // Конструктор без аргументов требуется для Firebase
    constructor() : this("", "", "", false, null, 1, "", false, null, null, "", "")
    
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
                restDay = entry.isRestDay,
                streakBonusAwardedDays = entry.streakBonusAwardedDays,
                photoUrl = entry.photoUrl,
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
                isRestDay = restDay,
                streakBonusAwardedDays = streakBonusAwardedDays,
                photoUrl = photoUrl,
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
