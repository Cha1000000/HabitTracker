package com.habittracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.habittracker.data.service.PendingNotificationManager
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import com.habittracker.presentation.navigation.AppNavHost
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.theme.HabitTrackerTheme
import com.habittracker.presentation.viewmodel.AuthState
import com.habittracker.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRepository: AuthRepository
    @Inject
    lateinit var socialRepository: SocialRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var achievementEventManager: com.habittracker.presentation.ui.components.AchievementEventManager

    private val authViewModel: AuthViewModel by viewModels()
    private var socialSyncJob: Job? = null
    private var navController: NavHostController? = null
    private var pendingNavigation: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Устанавливаем системный SplashScreen как можно раньше
        val splash = installSplashScreen()
        // Не задерживаем систему — сразу продолжаем рендер
        splash.setKeepOnScreenCondition { false }
        // Ранний захват deep link из push до инициализации UI/подписок
        intent?.getStringExtra("navigate_to")?.let { deep ->
            Log.d("MainActivity", "[push-nav] pre-UI handle navigate_to=$deep")
            handleIntentForNavigation(intent)
        }
        enableEdgeToEdge()
        setContent {
            HabitTrackerTheme {
                AppNavHost(
                    onNavControllerReady = { controller ->
                        navController = controller
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

        // Также доставляем при успешной аутентификации/переключении пользователя
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Success -> {
                            val userId = authRepository.getCurrentUserId()
                            Log.d("MainActivity", "[bootstrap] AuthState.Success observed. currentUserId=$userId")
                            launch(Dispatchers.IO) {
                                runCatching { PendingNotificationManager.deliverForCurrentUser(applicationContext, authRepository) }
                            }
                            if (userId != null) {
                                startSocialRealtimeSync(userId)
                                // Если есть отложенная навигация — выполним её
                                pendingNavigation?.let { nav ->
                                    Log.d(
                                        "MainActivity",
                                        "[push-nav] executing pendingNavigation after auth success"
                                    )
                                    nav()
                                    pendingNavigation = null
                                } ?: run {
                                    val deep = intent?.getStringExtra("navigate_to")
                                    if (deep.isNullOrEmpty()) {
                                        // Если push/deeplink не ставил отложенную навигацию, идём на Habits
                                        val action: () -> Unit = {
                                            navController?.let { controller ->
                                                Log.d("MainActivity", "[bootstrap] default navigate to Habits")
                                                runCatching {
                                                    controller.navigate(Screen.Habits.route) {
                                                        popUpTo(controller.graph.startDestinationId) { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        }
                                        if (navController != null) {
                                            Log.d("MainActivity", "[bootstrap] navController ready -> execute default nav")
                                            action()
                                        } else {
                                            Log.d("MainActivity", "[bootstrap] navController null -> set pending default nav to Habits")
                                            pendingNavigation = action
                                        }
                                    } else {
                                        Log.d("MainActivity", "[bootstrap] deep link present ($deep) -> skip default Habits nav")
                                    }
                                }
                            } else {
                                Log.w("MainActivity", "[bootstrap] Cannot start social realtime sync: currentUserId is null")
                            }
                        }
                        is AuthState.Unauthenticated, is AuthState.Error -> {
                            Log.d("MainActivity", "[bootstrap] AuthState=${state::class.simpleName}. Stopping social realtime sync if active…")
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
        Log.d(
            "MainActivity",
            "[push-nav] onNewIntent action=${intent.action} extras=${intent.extras?.keySet()?.joinToString()}"
        )
        // Обновляем текущий intent, чтобы Compose-экраны (LoginScreen) могли читать navigate_to
        setIntent(intent)
        handleIntentForNavigation(intent)
    }

    private fun handleIntentForNavigation(intent: android.content.Intent) {
        val navigateTo = intent.getStringExtra("navigate_to")
        val habitId = intent.getStringExtra("habit_id") ?: intent.getStringExtra("habitId")
        val challengeId = intent.getStringExtra("challenge_id") ?: intent.getStringExtra("challengeId")

        val route: String? = when (navigateTo) {
            "friends" -> Screen.Friends.route
            "friend_requests" -> Screen.Friends.route + "?tab=requests"
            "habit_invitations" -> Screen.Notifications.route
            "challenge_invitations" -> Screen.Challenges.createRoute("invitations")
            "notifications" -> Screen.Notifications.route
            "group_habit_detail" -> habitId?.let { Screen.GroupHabitDetail.createRoute(it) } ?: Screen.Habits.route
            "challenge_detail" -> challengeId?.let { Screen.ChallengeDetail.createRoute(it) } ?: Screen.Challenges.route
            "habits" -> Screen.Habits.route
            null -> {
                if (habitId != null) Screen.Habits.route else null
            }
            else -> null
        }

        Log.d(
            "MainActivity",
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
                    Log.d("MainActivity", "[push-nav] navigate('$route') success=${result.isSuccess} -> current=${currentRoute} tab=${currentTab}")
                    // После успешной навигации очищаем deep link extras, чтобы они не мешали дефолтной навигации позже
                    clearDeepLinkExtras()
                }
            }
            val isAuthenticated = (authViewModel.authState.value is AuthState.Success)
            if (navController != null && isAuthenticated) {
                Log.d("MainActivity", "[push-nav] navController ready & authed=true -> execute now")
                action()
            } else {
                Log.d(
                    "MainActivity",
                    "[push-nav] navController ready=${navController != null} authed=$isAuthenticated -> postpone"
                )
                // Очищаем extras сразу, чтобы не блокировать дефолтную навигацию после логина
                clearDeepLinkExtras()
                pendingNavigation = action
            }
        } else if (navigateTo != null) {
            // Если navigate_to пришёл, но наш роут не распознан — очищаем extras,
            // чтобы не блокировать дефолтную навигацию на Habits после авторизации
            Log.d(
                "MainActivity",
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
                Log.d("MainActivity", "[push-nav] cleared deep link extras (navigate_to/habit_id/challenge_id)")
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "[push-nav] failed to clear deep link extras: ${e.message}", e)
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
            Log.d("MainActivity", "[bootstrap] Social realtime sync already active. Skip start for user=$userId")
            return
        }
        socialSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.d("MainActivity", "[bootstrap] Start social realtime sync for user=$userId")
            try {
                coroutineScope {
                    // Эти collect лишь регистрируют snapshot listeners и наполняют Room. UI не используется здесь.
                    launch { socialRepository.getFriends(userId).collect { } }
                    launch { socialRepository.getIncomingFriendRequests(userId).collect { } }
                    launch { socialRepository.getOutgoingFriendRequests(userId).collect { } }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "[bootstrap] Social sync error: ${e.message}", e)
            } finally {
                Log.d("MainActivity", "[bootstrap] Social sync finished for user=$userId")
            }
        }
    }

    private fun stopSocialRealtimeSync() {
        socialSyncJob?.cancel()
        socialSyncJob = null
        Log.d("MainActivity", "[bootstrap] Social realtime sync stopped")
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
}