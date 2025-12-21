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

    // --- Формула для уровней ---
    fun pointsForLevel(level: Int): Int {
        return 1000 * level * (level + 1) / 2
    }

    fun getLevelFromPoints(points: Int): Int {
        if (points < 0) return 1
        var level = 1
        while (true) {
            if (points < pointsForLevel(level + 1)) {
                return level
            }
            level++
        }
    }
}
