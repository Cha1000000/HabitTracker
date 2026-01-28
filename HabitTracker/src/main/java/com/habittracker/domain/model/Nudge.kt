package com.habittracker.domain.model

/**
 * Модель "пинга" (напоминания) от одного участника другому
 * Используется для социального взаимодействия в групповых привычках
 */
data class Nudge(
    val id: String = "",
    val habitId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val sentAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val COOLDOWN_MILLIS = 60 * 60 * 1000L // 1 час
    }
    
    fun isOnCooldown(): Boolean {
        return System.currentTimeMillis() - sentAt < COOLDOWN_MILLIS
    }
}
