package com.habittracker.domain.model

/**
 * Глобальные настройки уведомлений пользователя
 */
data class NotificationPreferences(
    val habitRemindersEnabled: Boolean = true,
    val friendActivityEnabled: Boolean = true,
    val groupHabitUpdatesEnabled: Boolean = true,
    val challengeUpdatesEnabled: Boolean = true,
    val achievementUnlockedEnabled: Boolean = true,
    val dailySummaryEnabled: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22, // 22:00
    val quietHoursEnd: Int = 8     // 08:00
)
