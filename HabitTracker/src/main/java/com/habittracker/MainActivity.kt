package com.habittracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.habittracker.data.service.FCMTokenManager
import com.habittracker.data.service.PendingNotificationManager
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserPreferencesRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.domain.scheduler.DailyReminderScheduler
import com.habittracker.presentation.navigation.AppNavHost
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.components.AchievementEventManager
import com.habittracker.presentation.ui.theme.HabitTrackerTheme
import com.habittracker.presentation.util.SoundManager
import com.habittracker.presentation.viewmodel.AuthState
import com.habittracker.presentation.viewmodel.AuthViewModel
import com.habittracker.domain.util.AnalyticsEvents
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRepository: AuthRepository
    @Inject
    lateinit var socialRepository: SocialRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject
    lateinit var dailyReminderScheduler: DailyReminderScheduler
    @Inject
    lateinit var achievementEventManager: AchievementEventManager
    @Inject
    lateinit var analyticsManager: AnalyticsManager
    @Inject
    lateinit var errorLogger: ErrorLogger
    @Inject
    lateinit var fcmTokenManager: FCMTokenManager

    private val authViewModel: AuthViewModel by viewModels()
    private var socialSyncJob: Job? = null
    private var navController: NavHostController? = null
    private var pendingNavigation: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SoundManager.preload(applicationContext)
        // Устанавливаем системный SplashScreen как можно раньше
        val splash = installSplashScreen()
        // Не задерживаем систему — сразу продолжаем рендер
        splash.setKeepOnScreenCondition { false }
        // Ранний захват deep link из push до инициализации UI/подписок
        intent?.getStringExtra("navigate_to")?.let { deep ->
            Timber.tag("MainActivity").d("[push-nav] pre-UI handle navigate_to=$deep")
            handleIntentForNavigation(intent)
        }
        enableEdgeToEdge()
        setContent {
            HabitTrackerTheme {
                AppNavHost(
                    onNavControllerReady = { controller ->
                        navController = controller
                        // Track screen changes
                        controller.addOnDestinationChangedListener { _, destination, _ ->
                            val screenName = mapRouteToScreenName(destination.route)
                            analyticsManager.logScreenView(
                                screenName = screenName,
                                screenClass = destination.label?.toString()
                            )
                        }
                        
                        // Если есть отложенная навигация (например, auth случился раньше чем UI готов)
                        pendingNavigation?.let { nav ->
                            Timber.tag("MainActivity").d("[bootstrap] executing pendingNavigation after navController ready")
                            nav()
                            pendingNavigation = null
                        }

                        // Обработка стартового интента из уведомления
                        handleIntentForNavigation(intent)
                    },
                    achievementEventManager = achievementEventManager
                )
            }
        }

        // Запрашиваем разрешение на уведомления при первом запуске (Android 13+)
        requestNotificationsPermissionIfNeeded()

        // Доставляем отложенные уведомления для текущего пользователя при старте приложения
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { PendingNotificationManager.deliverForCurrentUser(applicationContext, authRepository) }
        }

        // Обновляем время последней активности при старте
        updateUserLastActive()

        // Проверяем и планируем ежедневное напоминание при старте
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val prefs = userPreferencesRepository.userPreferences.firstOrNull()
                if (prefs?.dailyReminderEnabled == true) {
                    dailyReminderScheduler.scheduleIfNotSet(prefs.dailyReminderTime)
                }
            }.onFailure { e ->
                Timber.tag("MainActivity").e(e, "Error scheduling daily reminder on startup")
            }
        }

        // Также доставляем при успешной аутентификации/переключении пользователя
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Success -> {
                            val userId = authRepository.getCurrentUserId()
                            Timber.tag("MainActivity").d("[bootstrap] AuthState.Success observed. currentUserId=$userId")
                            launch(Dispatchers.IO) {
                                runCatching { PendingNotificationManager.deliverForCurrentUser(applicationContext, authRepository) }
                            }
                            // Проверяем, нужно ли показать сообщение о недоступности FCM
                            checkAndShowFcmUnavailableDialog()
                            if (userId != null) {
                                startSocialRealtimeSync(userId)
                                // Если есть отложенная навигация — выполним её
                                pendingNavigation?.let { nav ->
                                    Timber.tag("MainActivity").d("[push-nav] executing pendingNavigation after auth success")
                                    nav()
                                    pendingNavigation = null
                                } ?: run {
                                    val deep = intent?.getStringExtra("navigate_to")
                                    if (deep.isNullOrEmpty()) {
                                        // Если push/deeplink не ставил отложенную навигацию, идём на Habits
                                        val action: () -> Unit = {
                                            navController?.let { controller ->
                                                val currentRoute = controller.currentBackStackEntry?.destination?.route
                                                val isAtRoot = currentRoute == null ||
                                                        currentRoute == Screen.Splash.route ||
                                                        currentRoute == Screen.Login.route ||
                                                        currentRoute == Screen.Register.route

                                                // Don't navigate to Habits if we're still on the splash screen
                                                // The splash screen will handle the navigation after animation completes
                                                if (isAtRoot && currentRoute != Screen.Splash.route) {
                                                    Timber.tag("MainActivity").d("[bootstrap] default navigate to Habits (currentRoute=$currentRoute)")
                                                    runCatching {
                                                        controller.navigate(Screen.Habits.route) {
                                                            popUpTo(controller.graph.startDestinationId) { inclusive = true }
                                                            launchSingleTop = true
                                                        }
                                                    }
                                                } else {
                                                    Timber.tag("MainActivity").d("[bootstrap] already at $currentRoute, skipping default Habits nav")
                                                }
                                            }
                                        }
                                        if (navController != null) {
                                            Timber.tag("MainActivity").d("[bootstrap] navController ready -> execute default nav")
                                            action()
                                        } else {
                                            Timber.tag("MainActivity").d("[bootstrap] navController null -> set pending default nav to Habits")
                                            pendingNavigation = action
                                        }
                                    } else {
                                        Timber.tag("MainActivity").d("[bootstrap] deep link present ($deep) -> skip default Habits nav")
                                    }
                                }
                            } else {
                                Timber.tag("MainActivity").w("[bootstrap] Cannot start social realtime sync: currentUserId is null")
                            }
                        }
                        is AuthState.Unauthenticated, is AuthState.Error -> {
                            Timber.tag("MainActivity").d("[bootstrap] AuthState=${state::class.simpleName}. Stopping social realtime sync if active…")
                            stopSocialRealtimeSync()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        Timber.tag("MainActivity").d(
            "[push-nav] onNewIntent action=${intent.action} extras=${intent.extras?.keySet()?.joinToString()}"
        )
        // Обновляем текущий intent, чтобы Compose-экраны (LoginScreen) могли читать navigate_to
        setIntent(intent)
        handleIntentForNavigation(intent)
    }

    private fun handleIntentForNavigation(intent: android.content.Intent) {
        // Обработка deep links (habittracker://invite?code=XXX или https://habittracker.app/invite/XXX)
        val deepLinkUri = intent.data
        if (deepLinkUri != null) {
            val inviteCode = when {
                // habittracker://invite?code=XXXXXX
                deepLinkUri.scheme == "habittracker" && deepLinkUri.host == "invite" -> {
                    deepLinkUri.getQueryParameter("code")
                }
                // https://habittracker.app/invite/XXXXXX
                deepLinkUri.host == "habittracker.app" && deepLinkUri.path?.startsWith("/invite/") == true -> {
                    deepLinkUri.path?.removePrefix("/invite/")
                }
                else -> null
            }
            
            if (!inviteCode.isNullOrBlank()) {
                Timber.tag("MainActivity").d("[deep-link] Invite code detected: $inviteCode")
                intent.putExtra("navigate_to", "join_by_code")
                intent.putExtra("invite_code", inviteCode)
            }
        }
        
        val navigateTo = intent.getStringExtra("navigate_to")
        val habitId = intent.getStringExtra("habit_id") ?: intent.getStringExtra("habitId")
        val challengeId = intent.getStringExtra("challenge_id") ?: intent.getStringExtra("challengeId")
        val inviteCode = intent.getStringExtra("invite_code")

        val route: String? = when (navigateTo) {
            "friends" -> Screen.Friends.route
            "friend_requests" -> Screen.Friends.route + "?tab=requests"
            "habit_invitations" -> Screen.Notifications.route
            "challenge_invitations" -> Screen.Challenges.createRoute("invitations")
            "notifications" -> Screen.Notifications.route
            "group_habit_detail" -> habitId?.let { Screen.GroupHabitDetail.createRoute(it) } ?: Screen.Habits.route
            "self_habit_detail" -> habitId?.let { Screen.SelfHabitDetail.createRoute(it) } ?: Screen.Habits.route
            "edit_habit" -> habitId?.let { Screen.AddEditHabit.route + "?habitId=$it" } ?: Screen.Habits.route
            "challenge_detail" -> challengeId?.let { Screen.ChallengeDetail.createRoute(it) } ?: Screen.Challenges.route
            "habits" -> Screen.Habits.route
            "join_by_code" -> inviteCode?.let { Screen.Habits.route + "?inviteCode=$it" } ?: Screen.Habits.route
            null -> {
                if (habitId != null) Screen.Habits.route else null
            }
            else -> null
        }

        Timber.tag("MainActivity").d(
            "[push-nav] handleIntentForNavigation navigate_to=$navigateTo habitId=$habitId -> route=${route ?: "<null>"}"
        )

        if (route != null) {
            val action: () -> Unit = {
                navController?.let { controller ->
                    val result = runCatching {
                        controller.navigate(route) {
                            // Сносим стартовый (Login) экран, чтобы он не перезаписал наш переход
                            popUpTo(controller.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                    val currentRoute = controller.currentBackStackEntry?.destination?.route
                    val currentTab = controller.currentBackStackEntry?.arguments?.getString("tab")
                    Timber.tag("MainActivity").d("[push-nav] navigate('$route') success=${result.isSuccess} -> current=${currentRoute} tab=${currentTab}")
                    // После успешной навигации очищаем deep link extras, чтобы они не мешали дефолтной навигации позже
                    clearDeepLinkExtras()
                }
            }
            val isAuthenticated = (authViewModel.authState.value is AuthState.Success)
            if (navController != null && isAuthenticated) {
                Timber.tag("MainActivity").d("[push-nav] navController ready & authed=true -> execute now")
                action()
            } else {
                Timber.tag("MainActivity").d(
                    "[push-nav] navController ready=${navController != null} authed=$isAuthenticated -> postpone"
                )
                // Очищаем extras сразу, чтобы не блокировать дефолтную навигацию после логина
                clearDeepLinkExtras()
                pendingNavigation = action
            }
        } else if (navigateTo != null) {
            // Если navigate_to пришёл, но наш роут не распознан — очищаем extras,
            // чтобы не блокировать дефолтную навигацию на Habits после авторизации
            Timber.tag("MainActivity").d(
                "[push-nav] unknown navigate_to='$navigateTo' -> clear extras to allow default nav"
            )
            clearDeepLinkExtras()
        }
    }

    private fun clearDeepLinkExtras() {
        try {
            val hadNavigateTo = intent?.hasExtra("navigate_to") == true
            val hadHabitId = intent?.hasExtra("habit_id") == true || intent?.hasExtra("habitId") == true
            val hadChallengeId = intent?.hasExtra("challenge_id") == true || intent?.hasExtra("challengeId") == true
            intent?.removeExtra("navigate_to")
            intent?.removeExtra("habit_id")
            intent?.removeExtra("habitId")
            intent?.removeExtra("challenge_id")
            intent?.removeExtra("challengeId")
            if (hadNavigateTo || hadHabitId || hadChallengeId) {
                Timber.tag("MainActivity").d("[push-nav] cleared deep link extras (navigate_to/habit_id/challenge_id)")
            }
        } catch (e: Exception) {
            Timber.tag("MainActivity").w(e, "[push-nav] failed to clear deep link extras: ${e.message}")
        }
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationsPermissionLauncher.launch(permission)
        }
    }

    // Ранний запуск подписок на социальные данные после логина для регистрации Firestore listeners
    private fun startSocialRealtimeSync(userId: String) {
        if (socialSyncJob?.isActive == true) {
            Timber.tag("MainActivity").d("[bootstrap] Social realtime sync already active. Skip start for user=$userId")
            return
        }
        socialSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            Timber.tag("MainActivity").d("[bootstrap] Start social realtime sync for user=$userId")
            try {
                coroutineScope {
                    // Эти collect лишь регистрируют snapshot listeners и наполняют Room. UI не используется здесь.
                    launch { socialRepository.getFriends(userId).collect { } }
                    launch { socialRepository.getIncomingFriendRequests(userId).collect { } }
                    launch { socialRepository.getOutgoingFriendRequests(userId).collect { } }
                }
            } catch (e: Exception) {
                Timber.tag("MainActivity").w(e, "[bootstrap] Social sync error: ${e.message}")
            } finally {
                Timber.tag("MainActivity").d("[bootstrap] Social sync finished for user=$userId")
            }
        }
    }

    private fun stopSocialRealtimeSync() {
        socialSyncJob?.cancel()
        socialSyncJob = null
        Timber.tag("MainActivity").d("[bootstrap] Social realtime sync stopped")
    }

    override fun onResume() {
        super.onResume()
        // Обновляем время последней активности при возобновлении
        updateUserLastActive()
    }

    override fun onPause() {
        super.onPause()
        // Обновляем время последней активности при сворачивании/закрытии
        // чтобы статус "был X мин назад" отсчитывался от момента закрытия
        updateUserLastActive()
    }

    private fun updateUserLastActive() {
        val userId = authRepository.getCurrentUserId() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { userRepository.updateLastActive(userId) }
        }
    }

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Ничего не делаем: UI не зависит от этого разрешения немедленно
        }
    
    /**
     * Маппинг route навигации в константы AnalyticsEvents.Screens
     */
    private fun mapRouteToScreenName(route: String?): String {
        if (route == null) return "unknown"
        
        return when {
            route.startsWith("habits_screen") -> AnalyticsEvents.Screens.HABITS
            route.startsWith("add_edit_habit_screen") -> {
                if (route.contains("habitId")) AnalyticsEvents.Screens.EDIT_HABIT 
                else AnalyticsEvents.Screens.ADD_HABIT
            }
            route.startsWith("login_screen") -> AnalyticsEvents.Screens.AUTH
            route.startsWith("register_screen") -> AnalyticsEvents.Screens.AUTH
            route.startsWith("profile_screen") -> AnalyticsEvents.Screens.PROFILE
            route.startsWith("friends_screen") -> AnalyticsEvents.Screens.FRIENDS
            route.startsWith("notifications_screen") -> AnalyticsEvents.Screens.NOTIFICATIONS
            route.startsWith("stats_screen") -> AnalyticsEvents.Screens.HABIT_STATISTICS
            route.startsWith("achievements_screen") -> AnalyticsEvents.Screens.ACHIEVEMENTS
            route.startsWith("group_habit_detail_screen") -> AnalyticsEvents.Screens.GROUP_HABIT_DETAIL
            route.startsWith("self_habit_detail_screen") -> AnalyticsEvents.Screens.SELF_HABIT_DETAIL
            route.startsWith("group_habit_stat_screen") -> AnalyticsEvents.Screens.GROUP_HABIT_STATISTICS
            route.startsWith("self_habit_stat_screen") -> AnalyticsEvents.Screens.HABIT_STATISTICS
            route.startsWith("public_habits_screen") -> AnalyticsEvents.Screens.PUBLIC_HABITS
            route.startsWith("challenges_screen") -> AnalyticsEvents.Screens.CHALLENGES
            route.startsWith("create_challenge_screen") -> AnalyticsEvents.Screens.CREATE_CHALLENGE
            route.startsWith("challenge_detail_screen") -> AnalyticsEvents.Screens.CHALLENGE_DETAIL
            route.startsWith("notification_settings_screen") -> AnalyticsEvents.Screens.SETTINGS
            route.startsWith("habit_settings_screen") -> AnalyticsEvents.Screens.SETTINGS
            else -> route
        }
    }

    /**
     * Проверяет, нужно ли показать диалог о недоступности FCM,
     * и показывает его если необходимо
     */
    private fun checkAndShowFcmUnavailableDialog() {
        if (fcmTokenManager.shouldShowFcmUnavailableMessage()) {
            // Показываем диалог только один раз
            fcmTokenManager.markFcmUnavailableMessageShown()
            
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.fcm_unavailable_title))
                .setMessage(getString(R.string.fcm_unavailable_message))
                .setPositiveButton(getString(R.string.fcm_unavailable_ok)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }
}
