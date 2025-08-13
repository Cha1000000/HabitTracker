package com.habittracker.presentation.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.habittracker.util.FCMConfigStatus
import com.habittracker.util.FCMTestingUtil
import com.habittracker.util.TestNotificationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class FCMTestUiState(
    val isLoading: Boolean = false,
    val configStatus: String = "Не проверено",
    val results: List<String> = emptyList()
)

@HiltViewModel
class FCMTestViewModel @Inject constructor(
    private val fcmTestingUtil: FCMTestingUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(FCMTestUiState())
    val uiState: StateFlow<FCMTestUiState> = _uiState.asStateFlow()

    init {
        checkFCMConfiguration()
    }

    fun checkFCMConfiguration() {
        viewModelScope.launch {
            try {
                val status = fcmTestingUtil.checkFCMConfiguration()
                val statusText = when (status) {
                    FCMConfigStatus.CONFIGURED -> "✅ Настроено корректно"
                    FCMConfigStatus.KEY_NOT_CONFIGURED -> "⚠️ FCM Server Key не настроен"
                    FCMConfigStatus.ERROR -> "❌ Ошибка конфигурации"
                }
                
                _uiState.value = _uiState.value.copy(
                    configStatus = statusText
                )
                
                addResult("🔧 Проверка конфигурации: $statusText")
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    configStatus = "❌ Ошибка: ${e.message}"
                )
                addResult("❌ Ошибка проверки конфигурации: ${e.message}")
            }
        }
    }

    fun sendTestNotification(fcmToken: String, type: TestNotificationType) {
        if (fcmToken.isBlank()) {
            addResult("⚠️ FCM токен не указан")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                addResult("📤 Отправка теста: $type")
                
                val result = fcmTestingUtil.sendTestNotification(fcmToken, type)
                
                if (result.isSuccess) {
                    addResult("✅ Тест $type отправлен успешно")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    addResult("❌ Ошибка отправки $type: $error")
                }
                
            } catch (e: Exception) {
                addResult("❌ Критическая ошибка при отправке $type: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun sendAllTestNotifications(fcmToken: String) {
        if (fcmToken.isBlank()) {
            addResult("⚠️ FCM токен не указан")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                addResult("🚀 Начинаем отправку всех тестовых уведомлений...")
                
                fcmTestingUtil.sendAllTestNotifications(fcmToken, delayBetweenMs = 2000)
                
                addResult("🏁 Завершена отправка всех тестовых уведомлений")
                
            } catch (e: Exception) {
                addResult("❌ Ошибка при отправке пакета тестов: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun getDeviceFCMToken(context: Context): String? {
        return try {
            // В реальном приложении токен получается асинхронно
            // Здесь упрощенный пример для тестирования
            null // Возвращаем null, так как метод должен быть асинхронным
        } catch (e: Exception) {
            addResult("❌ Ошибка получения FCM токена: ${e.message}")
            null
        }
    }

    /**
     * Асинхронно получает FCM токен устройства
     */
    fun fetchDeviceFCMToken() {
        viewModelScope.launch {
            try {
                addResult("🔄 Получение FCM токена устройства...")
                
                val token = FirebaseMessaging.getInstance().token.await()
                addResult("📱 FCM токен получен: ${token.take(20)}...")
                
                // Можно добавить логику автозаполнения токена в UI
                
            } catch (e: Exception) {
                addResult("❌ Ошибка получения FCM токена: ${e.message}")
            }
        }
    }

    private fun addResult(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        val timestampedMessage = "[$timestamp] $message"
        
        _uiState.value = _uiState.value.copy(
            results = (_uiState.value.results + timestampedMessage).takeLast(20)
        )
    }
}
