package com.habittracker.domain.model.gamification

/**
 * Запечатанный класс, представляющий различные события в системе геймификации,
 * которые могут приводить к начислению очков.
 */
sealed class GamificationEvent {
    // События, связанные с привычками
    object HabitCompleted : GamificationEvent()
    object HabitUncompleted : GamificationEvent() // Снятие отметки выполнения
    data class StreakBonus(val days: Int) : GamificationEvent() // Бонус за серию (3, 7, 30 дней)
    object PerfectDay : GamificationEvent() // Все привычки на день выполнены

    // События, связанные с челленджами
    object ChallengeJoined : GamificationEvent()
    object ChallengeWon : GamificationEvent()

    // Социальные события
    object NewFriendAdded : GamificationEvent()
}
