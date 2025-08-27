package com.habittracker.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.habittracker.BuildConfig
import com.habittracker.R
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    
    // Handle navigation after successful sign out
    LaunchedEffect(authState) {
        if (authState is com.habittracker.presentation.viewmodel.AuthState.Unauthenticated) {
            // Navigate to login screen and clear back stack
            navController.navigate("login_screen") {
                popUpTo(0) { inclusive = true }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // User information would go here
            Text(
                stringResource(R.string.profile_welcome),
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Кнопки геймификации
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Кнопка статистики
                Card(
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.Stats.route) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = stringResource(R.string.cd_stats),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.profile_stats_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                
                // Кнопка достижений
                Card(
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.Achievements.route) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = stringResource(R.string.cd_achievements),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.profile_achievements_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Sign out button
            Button(
                onClick = { viewModel.onEvent(com.habittracker.presentation.viewmodel.AuthEvent.SignOut) },
                enabled = authState !is com.habittracker.presentation.viewmodel.AuthState.Loading
            ) {
                if (authState is com.habittracker.presentation.viewmodel.AuthState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.action_sign_out))
                }
            }
            
            // Display error if any
            if (authState is com.habittracker.presentation.viewmodel.AuthState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (authState as com.habittracker.presentation.viewmodel.AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // Debug button for FCM testing
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navController.navigate(Screen.FCMTest.route) }) {
                    Text("🧪 FCM Test")
                }
            }
        }
    }
}
