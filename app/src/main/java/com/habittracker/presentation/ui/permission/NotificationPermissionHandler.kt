package com.habittracker.presentation.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.habittracker.R

/**
 * Compose-компонент для обработки запроса разрешения на уведомления
 */
@Composable
fun NotificationPermissionHandler(
    onPermissionResult: (Boolean) -> Unit,
    shouldRequest: Boolean = false
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    // Launcher для запроса разрешения
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult(isGranted)
        if (!isGranted) {
            showRationale = true
        }
    }

    // Проверяем и запрашиваем разрешение при необходимости
    LaunchedEffect(shouldRequest) {
        if (shouldRequest && !permissionRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                permissionRequested = true
            } else {
                onPermissionResult(true)
            }
        }
    }

    // Диалог с объяснением необходимости разрешения
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { 
                Text(
                    stringResource(R.string.notification_permission_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showRationale = false }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

/**
 * Проверяет, есть ли разрешение на уведомления
 */
@Composable
fun hasNotificationPermission(): Boolean {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
