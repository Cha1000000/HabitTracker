package com.habittracker.domain.util

import kotlinx.datetime.LocalDate

/**
 * Helper to build stable entityId for HabitEntry outbox records.
 * Format: "<habitId>_<userId>_<ISO_DATE>", e.g., "h1_u1_2025-09-03".
 */
object HabitEntryIds {
    fun make(habitId: String, userId: String, date: LocalDate): String =
        "${habitId}_${userId}_${date}"
}
