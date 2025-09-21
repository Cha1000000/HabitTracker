package com.habittracker.domain.model

/**
 * Объект, содержащий константы и логику, связанную с геймификацией.
 */
object Gamification {

    /**
     * Константы для начисления очков.
     */
    object Points {
        const val PER_HABIT_COMPLETION = 10
        const val PER_STREAK_DAY_BONUS = 5 // Дополнительные очки за каждый день стрика
    }

    /**
     * Логика и константы для системы уровней.
     */
    object Levels {
        const val POINTS_PER_LEVEL = 1000 // Количество очков для перехода на следующий уровень

        /**
         * Рассчитывает уровень пользователя на основе общего количества очков.
         */
        fun getLevelFromPoints(points: Int): Int {
            return (points / POINTS_PER_LEVEL) + 1
        }

        /**
         * Возвращает общее количество очков, необходимое для достижения следующего уровня.
         */
        fun getPointsForNextLevel(currentLevel: Int): Int {
            return currentLevel * POINTS_PER_LEVEL
        }
    }
}
