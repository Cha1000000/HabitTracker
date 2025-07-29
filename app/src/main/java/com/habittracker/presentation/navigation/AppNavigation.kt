package com.habittracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.habittracker.presentation.ui.habits.HabitsScreen

/**
 * Запечатанный класс для определения маршрутов навигации.
 * Обеспечивает типобезопасность и централизованное управление маршрутами.
 */
sealed class Screen(val route: String) {
    object Habits : Screen("habits_screen")
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
            HabitsScreen()
        }
        // TODO: Определить навигацию для других экранов
        /*
        composable(route = Screen.AddHabit.route) {
            AddHabitScreen(navController = navController)
        }
        */
    }
}
