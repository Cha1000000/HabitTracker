package com.habittracker.presentation.ui.auth

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicTextField
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.AuthEvent
import com.habittracker.presentation.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Навигация после успешной регистрации выполняется централизованно в MainActivity
    // (учитывает deep link из push). Здесь ничего не делаем, чтобы избежать гонок навигации.
    
    val isDarkTheme = isSystemInDarkTheme()
    
    TahoeGradientBackground(
        isDarkTheme = isDarkTheme
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                isDarkTheme = isDarkTheme
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Эмодзи или иконка
                    Text(
                        text = "✨",
                        style = MaterialTheme.typography.displayMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.auth_register_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Создайте аккаунт для отслеживания привычек",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))

                    // Email поле
                    GlassmorphicTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEvent(AuthEvent.EmailChanged(it)) },
                        label = { Text(stringResource(R.string.auth_email_label)) },
                        placeholder = { Text("example@email.com") },
                        leadingIcon = { 
                            Icon(
                                imageVector = Icons.Default.Email, 
                                contentDescription = null
                            ) 
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        isDarkTheme = isDarkTheme,
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Password поле
                    var passwordVisible by remember { mutableStateOf(false) }
                    GlassmorphicTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.onEvent(AuthEvent.PasswordChanged(it)) },
                        label = { Text(stringResource(R.string.auth_password_label)) },
                        placeholder = { Text("Минимум 6 символов") },
                        leadingIcon = { 
                            Icon(
                                imageVector = Icons.Default.Lock, 
                                contentDescription = null
                            ) 
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        isDarkTheme = isDarkTheme,
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))

                    // Кнопка регистрации
                    GlassmorphicButton(
                        onClick = { viewModel.onEvent(AuthEvent.Register) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        isDarkTheme = isDarkTheme
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.action_register),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Сообщение об ошибке
                    uiState.error?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Ссылка на логин
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(
                            text = stringResource(R.string.auth_have_account),
                            color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
