package com.habittracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habittracker.presentation.ui.ProfileScreen
import com.habittracker.presentation.ui.auth.LoginScreen
import com.habittracker.presentation.ui.auth.RegisterScreen
import com.habittracker.presentation.ui.habits.AddEditHabitScreen
import com.habittracker.presentation.ui.habits.HabitsScreen

/**
 * Запечатанный класс для определения маршрутов навигации.
 * Обеспечивает типобезопасность и централизованное управление маршрутами.
 */
sealed class Screen(val route: String) {
    object Habits : Screen("habits_screen")
    object AddEditHabit : Screen("add_edit_habit_screen")
    object Login : Screen("login_screen")
    object Register : Screen("register_screen")
    object Profile : Screen("profile_screen")
    // TODO: Добавить другие экраны по мере их создания
}

/**
 * Главный навигационный хост приложения.
 * Определяет все маршруты и соответствующие им экраны.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    // TODO: Add logic to check if user is logged in and set start destination accordingly
    NavHost(navController = navController, startDestination = Screen.Login.route) {
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

        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(route = Screen.Register.route) {
            RegisterScreen(navController = navController)
        }
        
        composable(route = Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }
        // TODO: Определить навигацию для других экранов
    }
}
