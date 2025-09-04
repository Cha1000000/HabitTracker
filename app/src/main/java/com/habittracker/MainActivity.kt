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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.habittracker.data.service.PendingNotificationManager
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.presentation.navigation.AppNavHost
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

    private val authViewModel: AuthViewModel by viewModels()
    private var socialSyncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitTrackerTheme {
                AppNavHost()
            }
        }

        // Запрашиваем разрешение на уведомления при первом запуске (Android 13+)
        requestNotificationsPermissionIfNeeded()

        // Доставляем отложенные уведомления для текущего пользователя при старте приложения
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { PendingNotificationManager.deliverForCurrentUser(applicationContext, authRepository) }
        }

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

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Ничего не делаем: UI не зависит от этого разрешения немедленно
        }
}