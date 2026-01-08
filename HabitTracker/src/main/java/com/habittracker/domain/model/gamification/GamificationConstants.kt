package com.habittracker.domain.model.gamification

object GamificationConstants {

    // --- Очки за действия ---
    const val POINTS_HABIT_COMPLETION = 10
    const val POINTS_CHALLENGE_PARTICIPATION = 25
    const val POINTS_CHALLENGE_VICTORY = 100
    const val POINTS_NEW_FRIEND = 50
    const val POINTS_FRIEND_INVITE = 15
    const val POINTS_GROUP_HABIT_CREATION = 25
    const val POINTS_PERFECT_DAY = 50 // Бонус за выполнение всех привычек за день

    // --- Бонусы за серии ---
    const val POINTS_STREAK_3_DAYS = 20
    const val POINTS_STREAK_7_DAYS = 50
    const val POINTS_STREAK_30_DAYS = 200

    // --- Формула для уровней (Линейная) ---
    const val POINTS_PER_LEVEL = 1000

    /**
     * Возвращает минимальное количество очков, необходимых для достижения указанного уровня.
     * Уровень 1: 0 очков
     * Уровень 2: 1000 очков
     * Уровень 3: 2000 очков
     */
    fun pointsForLevel(level: Int): Int {
        if (level <= 1) return 0
        return (level - 1) * POINTS_PER_LEVEL
    }

    /**
     * Рассчитывает текущий уровень на основе очков.
     * 0-999 -> 1
     * 1000-1999 -> 2
     */
    fun getLevelFromPoints(points: Int): Int {
        if (points < 0) return 1
        return (points / POINTS_PER_LEVEL) + 1
    }
}
