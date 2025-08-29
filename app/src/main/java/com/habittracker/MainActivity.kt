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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.habittracker.data.service.PendingNotificationManager
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.presentation.navigation.AppNavHost
import com.habittracker.presentation.ui.theme.HabitTrackerTheme
import com.habittracker.presentation.viewmodel.AuthState
import com.habittracker.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRepository: AuthRepository

    private val authViewModel: AuthViewModel by viewModels()

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
                    if (state is AuthState.Success) {
                        launch(Dispatchers.IO) {
                            runCatching { PendingNotificationManager.deliverForCurrentUser(applicationContext, authRepository) }
                        }
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

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Ничего не делаем: UI не зависит от этого разрешения немедленно
        }
}