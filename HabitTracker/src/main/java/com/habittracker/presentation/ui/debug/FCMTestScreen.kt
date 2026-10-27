package com.habittracker.presentation.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.util.TestNotificationType

/**
 * Debug экран для тестирования FCM push-уведомлений
 * Доступен только в debug сборке
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FCMTestScreen(
    viewModel: FCMTestViewModel = hiltViewModel()
) {
    var fcmToken by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🧪 FCM Testing Utility",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Инструмент для тестирования push-уведомлений",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        // FCM Config Status
        FCMConfigStatusCard(
            configStatus = uiState.configStatus,
            onCheckConfig = { viewModel.checkFCMConfiguration() }
        )
        
        // FCM Token Input
        OutlinedTextField(
            value = fcmToken,
            onValueChange = { fcmToken = it },
            label = { Text("FCM Token") },
            placeholder = { Text("Введите FCM токен устройства") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        
        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { 
                    viewModel.fetchDeviceFCMToken { token ->
                        if (token != null) {
                            fcmToken = token
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Получить токен устройства")
            }
            
            Button(
                onClick = { fcmToken = "" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Очистить")
            }
        }
        
        // Test Buttons
        Text(
            text = "Тестовые уведомления:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        TestNotificationButtons(
            fcmToken = fcmToken,
            isLoading = uiState.isLoading,
            onSendTest = { type -> viewModel.sendTestNotification(fcmToken, type) },
            onSendAllTests = { viewModel.sendAllTestNotifications(fcmToken) }
        )
        
        // Results
        if (uiState.results.isNotEmpty()) {
            ResultsSection(results = uiState.results)
        }
    }
}

@Composable
private fun FCMConfigStatusCard(
    configStatus: String,
    onCheckConfig: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Статус конфигурации FCM:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = configStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            TextButton(onClick = onCheckConfig) {
                Text("Проверить")
            }
        }
    }
}

@Composable
private fun TestNotificationButtons(
    fcmToken: String,
    isLoading: Boolean,
    onSendTest: (TestNotificationType) -> Unit,
    onSendAllTests: () -> Unit
) {
    val isTokenEmpty = fcmToken.isBlank()
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Individual test buttons
        TestNotificationType.entries.forEach { type ->
            Button(
                onClick = { onSendTest(type) },
                enabled = !isTokenEmpty && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                val emoji = when (type) {
                    TestNotificationType.SIMPLE -> "🧪"
                    TestNotificationType.FRIEND_REQUEST -> "👥"
                    TestNotificationType.HABIT_INVITATION -> "🎯"
                    TestNotificationType.CHALLENGE -> "🏆"
                }
                
                val text = when (type) {
                    TestNotificationType.SIMPLE -> "Простое уведомление"
                    TestNotificationType.FRIEND_REQUEST -> "Запрос дружбы"
                    TestNotificationType.HABIT_INVITATION -> "Приглашение в привычку"
                    TestNotificationType.CHALLENGE -> "Челлендж"
                }
                
                Text("$emoji $text")
            }
        }
        
        // Send all tests button
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Button(
            onClick = onSendAllTests,
            enabled = !isTokenEmpty && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Text("🚀 Отправить все тесты")
        }
    }
}

@Composable
private fun ResultsSection(results: List<String>) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 Результаты тестирования:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            results.takeLast(10).forEach { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
