package com.habittracker.presentation.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.presentation.ui.habits.components.GlassmorphicSwitch
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.NotificationSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    
    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.notification_settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Секция: Напоминания о привычках
                SettingsSection(title = stringResource(R.string.notification_section_habits)) {
                    SettingsSwitch(
                        title = stringResource(R.string.notification_habit_reminders),
                        subtitle = stringResource(R.string.notification_habit_reminders_desc),
                        checked = preferences.habitRemindersEnabled,
                        onCheckedChange = { viewModel.setHabitRemindersEnabled(it) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Секция: Социальные уведомления
                SettingsSection(title = stringResource(R.string.notification_section_social)) {
                    SettingsSwitch(
                        title = stringResource(R.string.notification_friend_activity),
                        subtitle = stringResource(R.string.notification_friend_activity_desc),
                        checked = preferences.friendActivityEnabled,
                        onCheckedChange = { viewModel.setFriendActivityEnabled(it) }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    SettingsSwitch(
                        title = stringResource(R.string.notification_group_habits),
                        subtitle = stringResource(R.string.notification_group_habits_desc),
                        checked = preferences.groupHabitUpdatesEnabled,
                        onCheckedChange = { viewModel.setGroupHabitUpdatesEnabled(it) }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    SettingsSwitch(
                        title = stringResource(R.string.notification_challenges),
                        subtitle = stringResource(R.string.notification_challenges_desc),
                        checked = preferences.challengeUpdatesEnabled,
                        onCheckedChange = { viewModel.setChallengeUpdatesEnabled(it) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Секция: Достижения
                SettingsSection(title = stringResource(R.string.notification_section_achievements)) {
                    SettingsSwitch(
                        title = stringResource(R.string.notification_achievements),
                        subtitle = stringResource(R.string.notification_achievements_desc),
                        checked = preferences.achievementUnlockedEnabled,
                        onCheckedChange = { viewModel.setAchievementUnlockedEnabled(it) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Секция: Режим "Не беспокоить"
                SettingsSection(title = stringResource(R.string.notification_section_quiet_hours)) {
                    SettingsSwitch(
                        title = stringResource(R.string.notification_quiet_hours),
                        subtitle = stringResource(
                            R.string.notification_quiet_hours_desc,
                            preferences.quietHoursStart,
                            preferences.quietHoursEnd
                        ),
                        checked = preferences.quietHoursEnabled,
                        onCheckedChange = { viewModel.setQuietHoursEnabled(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GlassmorphicSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            isDarkTheme = isDarkTheme,
        )
    }
}
