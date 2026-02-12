package com.habittracker.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.habittracker.presentation.ui.challenges.ChallengeDetailScreen
import com.habittracker.presentation.ui.challenges.ChallengesScreen
import com.habittracker.presentation.ui.challenges.CreateChallengeScreen
import com.habittracker.presentation.ui.components.AchievementEventManager
import com.habittracker.presentation.ui.components.AchievementToastData
import com.habittracker.presentation.ui.components.AchievementUnlockedToast
import com.habittracker.presentation.ui.debug.FCMTestScreen
import com.habittracker.presentation.ui.discover.PublicHabitsScreen
import com.habittracker.presentation.ui.habits.AddEditHabitScreen
import com.habittracker.presentation.ui.habits.GroupHabitDetailScreen
import com.habittracker.presentation.ui.habits.HabitsScreen
import com.habittracker.presentation.ui.notifications.NotificationsScreen
import com.habittracker.presentation.ui.settings.AboutScreen
import com.habittracker.presentation.ui.settings.NotificationSettingsScreen
import com.habittracker.presentation.ui.settings.PrivacyPolicyScreen
import com.habittracker.presentation.ui.social.FriendsScreen
import com.habittracker.presentation.ui.splash.SplashScreen
import com.habittracker.presentation.ui.stats.GroupHabitStatScreen
import com.habittracker.presentation.ui.stats.SelfHabitStatScreen
import com.habittracker.presentation.ui.stats.StatsScreen
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

/**
 * Запечатанный класс для определения маршрутов навигации.
 * Обеспечивает типобезопасность и централизованное управление маршрутами.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Habits : Screen("habits_screen?inviteCode={inviteCode}") {
        fun createRoute(inviteCode: String? = null) = 
            if (inviteCode != null) "habits_screen?inviteCode=$inviteCode" else "habits_screen"
    }
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
    object CreateChallenge : Screen("create_challenge_screen?challengeId={challengeId}") {
        fun createRoute(challengeId: String? = null) =
            if (challengeId != null) "create_challenge_screen?challengeId=$challengeId" else "create_challenge_screen"
    }
    object ChallengeDetail : Screen("challenge_detail_screen/{challengeId}") {
        fun createRoute(challengeId: String) = "challenge_detail_screen/$challengeId"
    }
    // Settings
    object NotificationSettings : Screen("notification_settings_screen")
    object HabitSettings : Screen("habit_settings_screen")
    object ResetProgress : Screen("reset_progress_screen")
    object About : Screen("about_screen")
    object PrivacyPolicy : Screen("privacy_policy_screen")
    // Debug screens (только для debug сборки)
    object FCMTest : Screen("fcm_test_screen")
    // Habit Statistics
    object SelfHabitStat : Screen("self_habit_stat_screen/{habitId}?isGroup={isGroup}") {
        fun createRoute(habitId: String, isGroup: Boolean = false) = "self_habit_stat_screen/$habitId?isGroup=$isGroup"
    }
    object GroupHabitStat : Screen("group_habit_stat_screen/{habitId}?isGroup={isGroup}") {
        fun createRoute(habitId: String, isGroup: Boolean = true) = "group_habit_stat_screen/$habitId?isGroup=$isGroup"
    }
}

/**
 * Главный навигационный хост приложения.
 * Определяет все маршруты и соответствующие им экраны.
 */
@Composable
fun AppNavHost(
    onNavControllerReady: (NavHostController) -> Unit = {},
    achievementEventManager: AchievementEventManager? = null
) {
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        onNavControllerReady(navController)
    }
    
    // Состояние для отображения toast о достижении
    var achievementToast by remember { mutableStateOf<AchievementToastData?>(null) }
    
    // Подписываемся на события разблокировки достижений
    LaunchedEffect(achievementEventManager) {
        achievementEventManager?.achievementUnlocked?.collectLatest { achievement ->
            achievementToast = achievement
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Start with splash screen
        NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(route = Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        
        composable(
            route = Screen.Habits.route,
            arguments = listOf(
                navArgument("inviteCode") {
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val inviteCode = backStackEntry.arguments?.getString("inviteCode")
            HabitsScreen(navController = navController, inviteCode = inviteCode)
        }

        composable(
            route = Screen.AddEditHabit.route + "?habitId={habitId}&startDate={startDate}&goalType={goalType}",
            arguments = listOf(
                navArgument("habitId") {
                    nullable = true
                },
                navArgument("startDate") {
                    nullable = true
                    defaultValue = null
                },
                navArgument("goalType") {
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
                },
                onSettingsClick = {
                    navController.navigate(Screen.NotificationSettings.route)
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
                    safePopBackStack(navController)
                }
            )
        }
        
        composable(route = Screen.Achievements.route) {
            AchievementsScreen(
                onNavigateBack = {
                    safePopBackStack(navController)
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateChallenge = {
                    navController.navigate(Screen.CreateChallenge.createRoute())
                },
                onNavigateToChallengeDetails = { challengeId ->
                    navController.navigate(Screen.ChallengeDetail.createRoute(challengeId))
                },
                onNavigateToEditChallenge = { challengeId ->
                    navController.navigate(Screen.CreateChallenge.createRoute(challengeId))
                },
                initialTab = initialTab
            )
        }
        
        composable(
            route = Screen.CreateChallenge.route,
            arguments = listOf(navArgument("challengeId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val challengeId = backStackEntry.arguments?.getString("challengeId")
            CreateChallengeScreen(
                onNavigateBack = { navController.popBackStack() },
                onChallengeCreated = { challengeId ->
                    navController.popBackStack()
                    navController.navigate(Screen.ChallengeDetail.createRoute(challengeId))
                },
                challengeId = challengeId
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
        
        composable(route = Screen.NotificationSettings.route) {
            NotificationSettingsScreen(navController = navController)
        }

        composable(route = Screen.HabitSettings.route) {
            com.habittracker.presentation.ui.settings.HabitSettingsScreen(navController = navController)
        }

        composable(route = Screen.ResetProgress.route) {
            com.habittracker.presentation.ui.settings.ResetProgressScreen(navController = navController)
        }

        composable(route = Screen.About.route) {
            AboutScreen(navController = navController)
        }

        composable(route = Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController)
        }

        // Habit Statistics Screens
        composable(
            route = Screen.SelfHabitStat.route,
            arguments = listOf(
                navArgument("habitId") {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument("isGroup") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            SelfHabitStatScreen(
                onNavigateBack = { safePopBackStack(navController) }
            )
        }

        composable(
            route = Screen.GroupHabitStat.route,
            arguments = listOf(
                navArgument("habitId") {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument("isGroup") {
                    type = NavType.BoolType
                    defaultValue = true
                }
            )
        ) {
            GroupHabitStatScreen(
                onNavigateBack = { safePopBackStack(navController) }
            )
        }
    }
        
        // Глобальный toast для разблокировки достижений
        AchievementUnlockedToast(
            achievement = achievementToast,
            onDismiss = { achievementToast = null }
        )
    }
}

private fun safePopBackStack(navController: NavHostController) {
    try {
        navController.popBackStack()
    } catch (e: IllegalStateException) {
        Timber.tag("Navigation").w(e, "popBackStack failed")
        runCatching { navController.navigateUp() }
    }
}
