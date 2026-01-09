package com.habittracker.presentation.ui.discover

import androidx.compose.ui.graphics.vector.ImageVector
import com.habittracker.R
import com.habittracker.presentation.ui.theme.TahoeIcons

/**
 * Категории публичных привычек
 */
enum class HabitCategory(
    val icon: ImageVector,
    val emoji: String,
    val stringResId: Int
) {
    ALL(TahoeIcons.CategoryAll, "✨", R.string.public_habits_category_all),
    HEALTH(TahoeIcons.CategoryFitness, "💪", R.string.public_habits_category_health),
    SPORT(TahoeIcons.CategorySport, "⚽", R.string.public_habits_category_sport),
    EDUCATION(TahoeIcons.CategoryEducation, "📚", R.string.public_habits_category_education),
    PRODUCTIVITY(TahoeIcons.CategoryProductivity, "📈", R.string.public_habits_category_productivity),
    MINDFULNESS(TahoeIcons.CategoryMindfulness, "🧘", R.string.public_habits_category_mindfulness),
    CREATIVITY(TahoeIcons.CategoryCreativity, "🎨", R.string.public_habits_category_creativity),
    SOCIAL(TahoeIcons.CategorySocial, "👥", R.string.public_habits_category_social),
    OTHER(TahoeIcons.CategoryOther, "🔮", R.string.public_habits_category_other)
}