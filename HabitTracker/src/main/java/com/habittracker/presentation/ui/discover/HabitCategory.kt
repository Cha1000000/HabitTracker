package com.habittracker.presentation.ui.discover

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.habittracker.R

/**
 * Категории публичных привычек
 */
enum class HabitCategory(
    @DrawableRes val iconResId: Int,
    val emoji: String,
    val stringResId: Int
) {
    ALL(R.drawable.ic_tahoe_squares_2x2, "✨", R.string.public_habits_category_all),
    HEALTH(R.drawable.ic_tahoe_heart, "💪", R.string.public_habits_category_health),
    SPORT(R.drawable.ic_tahoe_bolt, "⚽", R.string.public_habits_category_sport),
    EDUCATION(R.drawable.ic_tahoe_academic_cap, "📚", R.string.public_habits_category_education),
    PRODUCTIVITY(R.drawable.ic_tahoe_trending_up, "📈", R.string.public_habits_category_productivity),
    MINDFULNESS(R.drawable.ic_tahoe_sparkles, "🧘", R.string.public_habits_category_mindfulness),
    CREATIVITY(R.drawable.ic_tahoe_swatch, "🎨", R.string.public_habits_category_creativity),
    SOCIAL(R.drawable.ic_tahoe_users, "👥", R.string.public_habits_category_social),
    OTHER(R.drawable.ic_tahoe_tag, "🔮", R.string.public_habits_category_other)
}

@Composable
fun HabitCategory.getIcon(): ImageVector = ImageVector.vectorResource(iconResId)