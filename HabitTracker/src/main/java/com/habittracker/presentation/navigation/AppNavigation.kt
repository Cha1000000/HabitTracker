package com.habittracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habittracker.presentation.ui.auth.LoginScreen
import com.habittracker.presentation.ui.auth.RegisterScreen
import com.habittracker.presentation.ui.debug.FCMTestScreen
import com.habittracker.presentation.ui.habits.AddEditHabitScreen
import com.habittracker.presentation.ui.habits.GroupHabitDetailScreen
import com.habittracker.presentation.ui.habits.HabitsScreen
import com.habittracker.presentation.ui.notifications.NotificationsScreen
import com.habittracker.presentation.ui.social.FriendsScreen
import com.habittracker.presentation.ui.ProfileScreen
import com.habittracker.presentation.ui.achievements.AchievementsScreen
import com.habittracker.presentation.ui.splash.SplashScreen
import com.habittracker.presentation.ui.stats.StatsScreen

/**
 * Запечатанный класс для определения маршрутов навигации.
 * Обеспечивает типобезопасность и централизованное управление маршрутами.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Habits : Screen("habits_screen")
    object AddEditHabit : Screen("add_edit_habit_screen")
    object Login : Screen("login_screen")
    object Register : Screen("register_screen")
    object Profile : Screen("profile_screen")
    object ProfileUser : Screen("profile_screen/{userId}") {
        fun createRoute(userId: String) = "profile_screen/$userId"
    }
    object Friends : Screen("friends_screen")
    object Notifications : Screen("notifications_screen")
    object Stats : Screen("stats_screen")
    object Achievements : Screen("achievements_screen")
    object GroupHabitDetail : Screen("group_habit_detail_screen/{habitId}") {
        fun createRoute(habitId: String) = "group_habit_detail_screen/$habitId"
    }
    // Debug screens (только для debug сборки)
    object FCMTest : Screen("fcm_test_screen")
    // TODO: Добавить другие экраны по мере их создания
}

/**
 * Главный навигационный хост приложения.
 * Определяет все маршруты и соответствующие им экраны.
 */
@Composable
fun AppNavHost(
    onNavControllerReady: (NavHostController) -> Unit = {}
) {
    val navController = rememberNavController()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        onNavControllerReady(navController)
    }
    // Start with splash screen
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(route = Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        
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
        composable(
            route = Screen.ProfileUser.route,
            arguments = listOf(navArgument("userId") {
                type = NavType.StringType
                nullable = false
            })
        ) { backStackEntry ->
            // Пока используем тот же экран профиля текущего пользователя.
            // В будущем можно читать userId = backStackEntry.arguments?.getString("userId")
            // и отображать профиль другого пользователя.
            ProfileScreen(navController = navController)
        }
        
        composable(
            route = Screen.Friends.route + "?tab={tab}",
            arguments = listOf(navArgument("tab") { nullable = true })
        ) { backStackEntry ->
            val tabArg = backStackEntry.arguments?.getString("tab")
            val initialTab = if (tabArg == "requests") 1 else 0
            FriendsScreen(
                onNavigateToProfile = { userId ->
                    navController.navigate(Screen.ProfileUser.createRoute(userId))
                },
                onNavigateBack = { navController.popBackStack() },
                initialSelectedTab = initialTab
            )
        }
        
        composable(route = Screen.Notifications.route) {
            NotificationsScreen(
                navController = navController,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.GroupHabitDetail.route,
            arguments = listOf(navArgument("habitId") {
                type = NavType.StringType
                nullable = false
            })
        ) { backStackEntry ->
            val habitId = backStackEntry.arguments?.getString("habitId") ?: ""
            GroupHabitDetailScreen(
                habitId = habitId,
                navController = navController
            )
        }
        
        composable(route = Screen.Stats.route) {
            StatsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(route = Screen.Achievements.route) {
            AchievementsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(route = Screen.FCMTest.route) {
            FCMTestScreen()
        }
        // TODO: Определить навигацию для других экранов
    }
}
