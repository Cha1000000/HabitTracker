package com.habittracker.data.model

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.domain.model.HabitParticipantSettings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

/**
 * Личные настройки участника привычки в Firestore: `habits/{habitId}/participants/{userId}/meta/settings`.
 */
object FirebaseParticipantSettings {

    fun document(firestore: FirebaseFirestore, habitId: String, userId: String): DocumentReference =
        firestore.collection("habits")
            .document(habitId)
            .collection("participants")
            .document(userId)
            .collection("meta")
            .document("settings")

    fun toDomain(habitId: String, userId: String, data: Map<String, Any?>): HabitParticipantSettings {
        val rt = data["reminderTime"] as? Map<*, *>
        val reminderTime = if (rt != null) {
            val hour = (rt["hour"] as? Number)?.toInt()
            val minute = (rt["minute"] as? Number)?.toInt()
            if (hour != null && minute != null) LocalTime(hour, minute) else null
        } else null
        val reminderDays = (data["reminderDays"] as? List<*>)
            ?.filterIsInstance<Number>()
            ?.mapNotNull { d -> DayOfWeek.entries.firstOrNull { it.isoDayNumber == d.toInt() } }
            ?.toSet()
            ?: emptySet()
        return HabitParticipantSettings(
            habitId = habitId,
            userId = userId,
            reminderTime = reminderTime,
            reminderEnabled = (data["reminderEnabled"] as? Boolean) ?: (reminderTime != null),
            reminderDays = reminderDays,
            notificationsMuted = data["notificationsMuted"] as? Boolean ?: false,
            seasonStartDate = (data["seasonStartDate"] as? String)?.let { LocalDate.parse(it) },
            timeZone = data["timeZone"] as? String,
            // Без времени правки документ не новее локального — решит сравнение содержимого
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
