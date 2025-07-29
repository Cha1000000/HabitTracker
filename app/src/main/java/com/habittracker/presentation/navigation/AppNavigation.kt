package com.habittracker.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habittracker.presentation.ui.habits.AddEditHabitScreen
import com.habittracker.presentation.ui.habits.HabitsScreen

/**
 * Запечатанный класс для определения маршрутов навигации.
 * Обеспечивает типобезопасность и централизованное управление маршрутами.
 */
sealed class Screen(val route: String) {
    object Habits : Screen("habits_screen")
    object AddEditHabit : Screen("add_edit_habit_screen")
    // TODO: Добавить другие экраны по мере их создания
}

/**
 * Главный навигационный хост приложения.
 * Определяет все маршруты и соответствующие им экраны.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Habits.route) {
        composable(route = Screen.Habits.route) {
            HabitsScreen(navController = navController)
        }

        composable(
            route = Screen.AddEditHabit.route + "?habitId={habitId}",
            arguments = listOf(navArgument("habitId") {
                nullable = true
            })
        ) {
            AddEditHabitScreen(navController = navController)
        }
        // TODO: Определить навигацию для других экранов
    }
}
