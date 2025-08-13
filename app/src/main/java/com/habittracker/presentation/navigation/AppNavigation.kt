package com.habittracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
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
import com.habittracker.presentation.ui.social.FriendRequestsScreen
import com.habittracker.presentation.ui.social.FriendsScreen
import com.habittracker.presentation.ui.social.SearchFriendsScreen
import com.habittracker.presentation.ui.ProfileScreen

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
    object Friends : Screen("friends_screen")
    object SearchFriends : Screen("search_friends_screen")
    object FriendRequests : Screen("friend_requests_screen")
    object Notifications : Screen("notifications_screen")
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
        
        composable(route = Screen.Friends.route) {
            FriendsScreen(
                onNavigateToSearch = {
                    navController.navigate(Screen.SearchFriends.route)
                },
                onNavigateToRequests = {
                    navController.navigate(Screen.FriendRequests.route)
                }
            )
        }
        
        composable(route = Screen.SearchFriends.route) {
            SearchFriendsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(route = Screen.FriendRequests.route) {
            FriendRequestsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
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
        
        composable(route = Screen.FCMTest.route) {
            FCMTestScreen()
        }
        // TODO: Определить навигацию для других экранов
    }
}
