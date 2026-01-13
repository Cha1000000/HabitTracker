package com.habittracker.data.local.seed

import com.habittracker.R
import com.habittracker.data.local.entity.AchievementEntity
import kotlinx.datetime.Clock

/**
 * Список всех достижений приложения.
 * Эмоциональные названия для вовлечения пользователей.
 */
object DefaultAchievements {

    fun getAll(): List<AchievementEntity> {
        val now = Clock.System.now().toEpochMilliseconds()
        return streakAchievements(now) +
                resilienceAchievements(now) +
                masteryAchievements(now) +
                socialAchievements(now) +
                challengeAchievements(now) +
                specialAchievements(now)
    }

    /**
     * Категория: Стойкость и Дисциплина (Streaks)
     * Цель: Удержание пользователя в приложении (Retention)
     */
    private fun streakAchievements(createdAt: Long) = listOf(
        AchievementEntity(
            id = "streak_3",
            title = "Хет-трик",
            description = "Выполняй привычку 3 дня подряд",
            icon = "🎯",
            category = "STREAKS",
            type = "SINGLE",
            conditionMetric = "CURRENT_STREAK",
            conditionTargetValue = 3,
            conditionTimeframe = null,
            pointsReward = 15,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_streak_3_title,
            descriptionResId = R.string.achievement_streak_3_desc
        ),
        AchievementEntity(
            id = "streak_7",
            title = "На орбите",
            description = "7 дней подряд — самое сложное позади!",
            icon = "🚀",
            category = "STREAKS",
            type = "SINGLE",
            conditionMetric = "CURRENT_STREAK",
            conditionTargetValue = 7,
            conditionTimeframe = null,
            pointsReward = 50,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_streak_7_title,
            descriptionResId = R.string.achievement_streak_7_desc
        ),
        AchievementEntity(
            id = "streak_21",
            title = "21 день спустя",
            description = "Говорят, привычка формируется за 21 день. Проверим?",
            icon = "🧠",
            category = "STREAKS",
            type = "SINGLE",
            conditionMetric = "CURRENT_STREAK",
            conditionTargetValue = 21,
            conditionTimeframe = null,
            pointsReward = 100,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_streak_21_title,
            descriptionResId = R.string.achievement_streak_21_desc
        ),
        AchievementEntity(
            id = "streak_30",
            title = "Железный человек",
            description = "30 дней идеального выполнения. Ты — легенда!",
            icon = "🦾",
            category = "STREAKS",
            type = "SINGLE",
            conditionMetric = "CURRENT_STREAK",
            conditionTargetValue = 30,
            conditionTimeframe = null,
            pointsReward = 200,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_streak_30_title,
            descriptionResId = R.string.achievement_streak_30_desc
        ),
        AchievementEntity(
            id = "streak_100",
            title = "Стойкий",
            description = "100 дней подряд. Дисциплина — твоё второе имя",
            icon = "💎",
            category = "STREAKS",
            type = "SINGLE",
            conditionMetric = "CURRENT_STREAK",
            conditionTargetValue = 100,
            conditionTimeframe = null,
            pointsReward = 500,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_streak_100_title,
            descriptionResId = R.string.achievement_streak_100_desc
        ),
        AchievementEntity(
            id = "streak_365",
            title = "Календарь Майя",
            description = "365 дней подряд. Ты прошёл год — это легендарно!",
            icon = "👑",
            category = "STREAKS",
            type = "SINGLE",
            conditionMetric = "LONGEST_STREAK",
            conditionTargetValue = 365,
            conditionTimeframe = null,
            pointsReward = 1000,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_streak_365_title,
            descriptionResId = R.string.achievement_streak_365_desc
        )
    )

    /**
     * Категория: Анти-выгорание (Resilience)
     * Цель: Вернуть пользователя после пропуска
     */
    private fun resilienceAchievements(createdAt: Long) = listOf(
        AchievementEntity(
            id = "phoenix",
            title = "Феникс",
            description = "Вернись после пропуска и выполни привычку",
            icon = "🔥",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "CONSECUTIVE_DAYS",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 25,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_phoenix_title,
            descriptionResId = R.string.achievement_phoenix_desc
        ),
        AchievementEntity(
            id = "comeback_kid",
            title = "Возвращение героя",
            description = "Вы вернулись в приложение после недели отсутствия",
            icon = "🦸",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "CONSECUTIVE_DAYS",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 30,
            isSecret = true,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_comeback_title,
            descriptionResId = R.string.achievement_comeback_desc
        )
    )

    /**
     * Категория: Мастерство и Объёмы (Total count)
     * Цель: Накопительный эффект — прогресс не сгорает
     */
    private fun masteryAchievements(createdAt: Long) = listOf(
        AchievementEntity(
            id = "completions_10",
            title = "Новичок",
            description = "Лиха беда начало — 10 выполненных привычек",
            icon = "🌱",
            category = "MILESTONES",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_COMPLETED",
            conditionTargetValue = 10,
            conditionTimeframe = null,
            pointsReward = 20,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_completions_10_title,
            descriptionResId = R.string.achievement_completions_10_desc
        ),
        AchievementEntity(
            id = "completions_50",
            title = "Ученик",
            description = "50 выполнений — ты на верном пути!",
            icon = "📚",
            category = "MILESTONES",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_COMPLETED",
            conditionTargetValue = 50,
            conditionTimeframe = null,
            pointsReward = 50,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_completions_50_title,
            descriptionResId = R.string.achievement_completions_50_desc
        ),
        AchievementEntity(
            id = "completions_100",
            title = "Любитель",
            description = "100 выполнений — ты уже понимаешь, как это работает",
            icon = "⭐",
            category = "MILESTONES",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_COMPLETED",
            conditionTargetValue = 100,
            conditionTimeframe = null,
            pointsReward = 100,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_completions_100_title,
            descriptionResId = R.string.achievement_completions_100_desc
        ),
        AchievementEntity(
            id = "completions_500",
            title = "Профи",
            description = "500 выполнений — дисциплина твоё второе имя",
            icon = "🏅",
            category = "MILESTONES",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_COMPLETED",
            conditionTargetValue = 500,
            conditionTimeframe = null,
            pointsReward = 250,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_completions_500_title,
            descriptionResId = R.string.achievement_completions_500_desc
        ),
        AchievementEntity(
            id = "completions_1000",
            title = "Грандмастер",
            description = "1000 выполнений — 10,000 часов начинаются здесь",
            icon = "🏆",
            category = "MILESTONES",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_COMPLETED",
            conditionTargetValue = 1000,
            conditionTimeframe = null,
            pointsReward = 500,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_completions_1000_title,
            descriptionResId = R.string.achievement_completions_1000_desc
        ),
        AchievementEntity(
            id = "habits_created_1",
            title = "Первые шаги",
            description = "Создай свою первую привычку",
            icon = "👣",
            category = "HABITS",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_CREATED",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 10,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_first_steps_title,
            descriptionResId = R.string.achievement_first_steps_desc
        ),
        AchievementEntity(
            id = "habits_created_5",
            title = "Коллекционер",
            description = "Создай 5 привычек — разнообразие важно!",
            icon = "📦",
            category = "HABITS",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_CREATED",
            conditionTargetValue = 5,
            conditionTimeframe = null,
            pointsReward = 30,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_collector_title,
            descriptionResId = R.string.achievement_collector_desc
        ),
        AchievementEntity(
            id = "habits_created_10",
            title = "Архитектор жизни",
            description = "10 привычек — ты строишь свою лучшую версию",
            icon = "🏗️",
            category = "HABITS",
            type = "SINGLE",
            conditionMetric = "TOTAL_HABITS_CREATED",
            conditionTargetValue = 10,
            conditionTimeframe = null,
            pointsReward = 75,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_life_architect_title,
            descriptionResId = R.string.achievement_life_architect_desc
        ),
        AchievementEntity(
            id = "multitasker",
            title = "Мультизадачность",
            description = "Выполни 5 разных привычек за один день",
            icon = "🎪",
            category = "HABITS",
            type = "SINGLE",
            conditionMetric = "HABIT_VARIETY",
            conditionTargetValue = 5,
            conditionTimeframe = "DAILY",
            pointsReward = 40,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_multitasker_title,
            descriptionResId = R.string.achievement_multitasker_desc
        )
    )

    /**
     * Категория: Социальные достижения
     * Цель: Вовлечение через социальные механики
     */
    private fun socialAchievements(createdAt: Long) = listOf(
        AchievementEntity(
            id = "first_friend",
            title = "Первый друг",
            description = "Добавь первого друга — вместе веселее!",
            icon = "🤝",
            category = "SOCIAL",
            type = "SINGLE",
            conditionMetric = "FRIENDS_COUNT",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 20,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_first_friend_title,
            descriptionResId = R.string.achievement_first_friend_desc
        ),
        AchievementEntity(
            id = "social_circle",
            title = "Свой круг",
            description = "5 друзей — у тебя отличная компания!",
            icon = "👥",
            category = "SOCIAL",
            type = "SINGLE",
            conditionMetric = "FRIENDS_COUNT",
            conditionTargetValue = 5,
            conditionTimeframe = null,
            pointsReward = 50,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_social_circle_title,
            descriptionResId = R.string.achievement_social_circle_desc
        ),
        AchievementEntity(
            id = "social_butterfly",
            title = "Социальная бабочка",
            description = "10 друзей — ты душа компании!",
            icon = "🦋",
            category = "SOCIAL",
            type = "SINGLE",
            conditionMetric = "FRIENDS_COUNT",
            conditionTargetValue = 10,
            conditionTimeframe = null,
            pointsReward = 100,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_social_butterfly_title,
            descriptionResId = R.string.achievement_social_butterfly_desc
        ),
        AchievementEntity(
            id = "team_player",
            title = "Командный игрок",
            description = "Присоединись к групповой привычке",
            icon = "🎯",
            category = "SOCIAL",
            type = "SINGLE",
            conditionMetric = "MENTOR",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 25,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_team_player_title,
            descriptionResId = R.string.achievement_team_player_desc
        ),
        AchievementEntity(
            id = "group_leader",
            title = "Лидер группы",
            description = "Создай групповую привычку и пригласи друзей",
            icon = "👑",
            category = "SOCIAL",
            type = "SINGLE",
            conditionMetric = "MENTOR",
            conditionTargetValue = 3,
            conditionTimeframe = null,
            pointsReward = 75,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_group_leader_title,
            descriptionResId = R.string.achievement_group_leader_desc
        )
    )

    /**
     * Категория: Челленджи
     * Цель: Соревновательный дух
     */
    private fun challengeAchievements(createdAt: Long) = listOf(
        AchievementEntity(
            id = "first_challenge",
            title = "Первый вызов",
            description = "Прими участие в своём первом челлендже",
            icon = "⚔️",
            category = "CHALLENGES",
            type = "SINGLE",
            conditionMetric = "CHALLENGES_PARTICIPATED",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 25,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_first_challenge_title,
            descriptionResId = R.string.achievement_first_challenge_desc
        ),
        AchievementEntity(
            id = "first_win",
            title = "Первая победа",
            description = "Выиграй свой первый челлендж!",
            icon = "🥇",
            category = "CHALLENGES",
            type = "SINGLE",
            conditionMetric = "CHALLENGES_WON",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 50,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_first_win_title,
            descriptionResId = R.string.achievement_first_win_desc
        ),
        AchievementEntity(
            id = "champion",
            title = "Чемпион",
            description = "5 побед в челленджах — ты настоящий чемпион!",
            icon = "🏆",
            category = "CHALLENGES",
            type = "SINGLE",
            conditionMetric = "CHALLENGES_WON",
            conditionTargetValue = 5,
            conditionTimeframe = null,
            pointsReward = 150,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_champion_title,
            descriptionResId = R.string.achievement_champion_desc
        ),
        AchievementEntity(
            id = "veteran",
            title = "Ветеран",
            description = "Участвуй в 10 челленджах",
            icon = "🎖️",
            category = "CHALLENGES",
            type = "SINGLE",
            conditionMetric = "CHALLENGES_PARTICIPATED",
            conditionTargetValue = 10,
            conditionTimeframe = null,
            pointsReward = 100,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_veteran_title,
            descriptionResId = R.string.achievement_veteran_desc
        ),
        AchievementEntity(
            id = "undefeated",
            title = "Непобедимый",
            description = "10 побед — тебя не остановить!",
            icon = "💪",
            category = "CHALLENGES",
            type = "SINGLE",
            conditionMetric = "CHALLENGES_WON",
            conditionTargetValue = 10,
            conditionTimeframe = null,
            pointsReward = 300,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_undefeated_title,
            descriptionResId = R.string.achievement_undefeated_desc
        )
    )

    /**
     * Категория: Особые достижения
     * Цель: Уникальные паттерны поведения
     */
    private fun specialAchievements(createdAt: Long) = listOf(
        AchievementEntity(
            id = "early_bird",
            title = "Ранняя пташка",
            description = "Выполни 10 привычек до 8 утра",
            icon = "🌅",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "EARLY_BIRD",
            conditionTargetValue = 10,
            conditionTimeframe = null,
            pointsReward = 50,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_early_bird_title,
            descriptionResId = R.string.achievement_early_bird_desc
        ),
        AchievementEntity(
            id = "night_owl",
            title = "Ночная сова",
            description = "Выполни 10 привычек после 22:00",
            icon = "🦉",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "NIGHT_OWL",
            conditionTargetValue = 10,
            conditionTimeframe = null,
            pointsReward = 50,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_night_owl_title,
            descriptionResId = R.string.achievement_night_owl_desc
        ),
        AchievementEntity(
            id = "weekend_warrior",
            title = "Воин выходных",
            description = "Выполняй привычки каждые выходные месяц подряд",
            icon = "⚡",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "WEEKEND_WARRIOR",
            conditionTargetValue = 8,
            conditionTimeframe = "MONTHLY",
            pointsReward = 75,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_weekend_warrior_title,
            descriptionResId = R.string.achievement_weekend_warrior_desc
        ),
        AchievementEntity(
            id = "perfect_week",
            title = "Идеальная неделя",
            description = "Выполни все привычки за неделю",
            icon = "✨",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "PERFECT_WEEKS",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 75,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_perfect_week_title,
            descriptionResId = R.string.achievement_perfect_week_desc
        ),
        AchievementEntity(
            id = "perfect_month",
            title = "Идеальный месяц",
            description = "Выполни все привычки за месяц — ты просто космос!",
            icon = "🌟",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "PERFECT_MONTHS",
            conditionTargetValue = 1,
            conditionTimeframe = null,
            pointsReward = 200,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_perfect_month_title,
            descriptionResId = R.string.achievement_perfect_month_desc
        ),
        AchievementEntity(
            id = "consistency_master",
            title = "Мастер постоянства",
            description = "Достигни 90% выполнения привычек за месяц",
            icon = "🎯",
            category = "SPECIAL",
            type = "SINGLE",
            conditionMetric = "CONSISTENCY",
            conditionTargetValue = 90,
            conditionTimeframe = "MONTHLY",
            pointsReward = 150,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_consistency_master_title,
            descriptionResId = R.string.achievement_consistency_master_desc
        ),
        AchievementEntity(
            id = "level_5",
            title = "Опытный",
            description = "Достигни 5 уровня",
            icon = "📈",
            category = "MILESTONES",
            type = "SINGLE",
            conditionMetric = "TOTAL_POINTS",
            conditionTargetValue = 500,
            conditionTimeframe = null,
            pointsReward = 50,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_level_5_title,
            descriptionResId = R.string.achievement_level_5_desc
        ),
        AchievementEntity(
            id = "level_10",
            title = "Мастер",
            description = "Достигни 10 уровня — ты настоящий мастер!",
            icon = "🔮",
            category = "MILESTONES",
            type = "SINGLE",
            conditionMetric = "TOTAL_POINTS",
            conditionTargetValue = 1500,
            conditionTimeframe = null,
            pointsReward = 100,
            isSecret = false,
            isActive = true,
            createdAt = createdAt,
            titleResId = R.string.achievement_level_10_title,
            descriptionResId = R.string.achievement_level_10_desc
        )
    )
}
