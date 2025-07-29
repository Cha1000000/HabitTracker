package com.habittracker.presentation.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate away on successful login
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            navController.navigate(Screen.Habits.route) {
                // Очищаем стек до экрана логина, чтобы пользователь не мог вернуться назад
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Вход", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = { viewModel.onEmailChange(it) },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.onPasswordChange(it) },
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.login() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Войти")
            }
        }

        uiState.error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
            Text("Нет аккаунта? Зарегистрируйтесь")
        }
    }
}
