package com.habittracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habittracker.presentation.ui.ProfileScreen
import com.habittracker.presentation.ui.achievements.AchievementsScreen
import com.habittracker.presentation.ui.auth.LoginScreen
import com.habittracker.presentation.ui.auth.RegisterScreen
import com.habittracker.presentation.ui.debug.FCMTestScreen
import com.habittracker.presentation.ui.discover.PublicHabitsScreen
import com.habittracker.presentation.ui.habits.AddEditHabitScreen
import com.habittracker.presentation.ui.habits.GroupHabitDetailScreen
import com.habittracker.presentation.ui.habits.HabitsScreen
import com.habittracker.presentation.ui.notifications.NotificationsScreen
import com.habittracker.presentation.ui.social.FriendsScreen
import com.habittracker.presentation.ui.splash.SplashScreen
import com.habittracker.presentation.ui.stats.StatsScreen
import com.habittracker.presentation.ui.challenges.ChallengeDetailScreen
import com.habittracker.presentation.ui.challenges.ChallengesScreen
import com.habittracker.presentation.ui.challenges.CreateChallengeScreen

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
    object PublicHabits : Screen("public_habits_screen")
    // Challenges
    object Challenges : Screen("challenges_screen?tab={tab}") {
        fun createRoute(tab: String? = null) = if (tab != null) "challenges_screen?tab=$tab" else "challenges_screen"
    }
    object CreateChallenge : Screen("create_challenge_screen")
    object ChallengeDetail : Screen("challenge_detail_screen/{challengeId}") {
        fun createRoute(challengeId: String) = "challenge_detail_screen/$challengeId"
    }
    // Debug screens (только для debug сборки)
    object FCMTest : Screen("fcm_test_screen")
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
            route = Screen.AddEditHabit.route + "?habitId={habitId}&startDate={startDate}",
            arguments = listOf(
                navArgument("habitId") {
                    nullable = true
                },
                navArgument("startDate") {
                    nullable = true
                    defaultValue = null
                }
            )
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
        
        composable(route = Screen.PublicHabits.route) {
            PublicHabitsScreen(navController = navController)
        }
        
        composable(route = Screen.FCMTest.route) {
            FCMTestScreen()
        }
        
        // Challenges
        composable(
            route = Screen.Challenges.route,
            arguments = listOf(navArgument("tab") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab")
            ChallengesScreen(
                onNavigateToCreateChallenge = {
                    navController.navigate(Screen.CreateChallenge.route)
                },
                onNavigateToChallengeDetails = { challengeId ->
                    navController.navigate(Screen.ChallengeDetail.createRoute(challengeId))
                },
                initialTab = initialTab
            )
        }
        
        composable(route = Screen.CreateChallenge.route) {
            CreateChallengeScreen(
                onNavigateBack = { navController.popBackStack() },
                onChallengeCreated = { challengeId ->
                    navController.popBackStack()
                    navController.navigate(Screen.ChallengeDetail.createRoute(challengeId))
                }
            )
        }
        
        composable(
            route = Screen.ChallengeDetail.route,
            arguments = listOf(navArgument("challengeId") {
                type = NavType.StringType
                nullable = false
            })
        ) { backStackEntry ->
            val challengeId = backStackEntry.arguments?.getString("challengeId") ?: return@composable
            ChallengeDetailScreen(
                challengeId = challengeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
