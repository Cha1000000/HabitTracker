package com.habittracker.data.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер состояния приложения для отслеживания текущего экрана
 */
@Singleton
class AppStateManager @Inject constructor() {
    
    @Volatile
    private var currentScreen: String? = null

    @Volatile
    private var hasBottomSystemNavigationBar: Boolean = false

    @Volatile
    private var bottomSystemNavigationBarHeightPx: Int = 0
    
    /**
     * Устанавливает текущий активный экран
     */
    fun setCurrentScreen(screenRoute: String?) {
        currentScreen = screenRoute
    }

    fun setHasBottomSystemNavigationBar(value: Boolean) {
        hasBottomSystemNavigationBar = value
    }

    fun setBottomSystemNavigationBarHeightPx(value: Int) {
        bottomSystemNavigationBarHeightPx = value
    }

    /**
     * Проверяет, открыт ли экран привычек (главный экран)
     */
    fun isHabitsScreenActive(): Boolean {
        return currentScreen == "habits_screen"
    }

    /**
     * Проверяет, открыт ли экран уведомлений
     */
    fun isNotificationsScreenActive(): Boolean {
        return currentScreen == "notifications_screen"
    }
}
