package com.habittracker.presentation.ui.discover

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.ui.graphics.vector.ImageVector
import com.habittracker.R

/**
 * Категории публичных привычек
 */
enum class HabitCategory(
    val icon: ImageVector,
    val emoji: String,
    val stringResId: Int
) {
    ALL(Icons.Default.Apps, "✨", R.string.public_habits_category_all),
    HEALTH(Icons.Default.FitnessCenter, "💪", R.string.public_habits_category_health),
    SPORT(Icons.Default.SportsTennis, "⚽", R.string.public_habits_category_sport),
    EDUCATION(Icons.Default.School, "📚", R.string.public_habits_category_education),
    PRODUCTIVITY(Icons.AutoMirrored.Filled.TrendingUp, "📈", R.string.public_habits_category_productivity),
    MINDFULNESS(Icons.Default.SelfImprovement, "🧘", R.string.public_habits_category_mindfulness),
    CREATIVITY(Icons.Default.Palette, "🎨", R.string.public_habits_category_creativity),
    SOCIAL(Icons.Default.Groups, "👥", R.string.public_habits_category_social),
    OTHER(Icons.Default.Category, "🔮", R.string.public_habits_category_other)
}