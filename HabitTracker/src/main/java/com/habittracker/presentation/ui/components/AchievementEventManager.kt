package com.habittracker.presentation.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Глобальный менеджер событий для показа toast при разблокировке достижений.
 * Используется для отображения анимированных уведомлений в любом месте приложения.
 */
@Singleton
class AchievementEventManager @Inject constructor() {
    
    private val _achievementUnlocked = MutableSharedFlow<AchievementToastData>(
        replay = 0,
        extraBufferCapacity = 3
    )
    val achievementUnlocked: SharedFlow<AchievementToastData> = _achievementUnlocked.asSharedFlow()
    
    /**
     * Отправляет событие о разблокировке достижения для показа toast
     */
    suspend fun emitAchievementUnlocked(achievement: AchievementToastData) {
        _achievementUnlocked.emit(achievement)
    }
}
