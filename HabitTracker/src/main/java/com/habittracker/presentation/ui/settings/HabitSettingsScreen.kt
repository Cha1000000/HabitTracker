package com.habittracker.presentation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicDialog
import com.habittracker.presentation.ui.habits.components.GlassmorphicSectionHeader
import com.habittracker.presentation.ui.habits.components.GlassmorphicSwitch
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.HabitSettingsViewModel
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSettingsScreen(
    navController: NavController,
    viewModel: HabitSettingsViewModel = hiltViewModel()
) {
    val userPreferences by viewModel.userPreferences.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    TahoeGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
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
                // Notifications Section
                GlassmorphicSectionHeader(
                    text = stringResource(R.string.settings_notifications_header),
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                )

                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_daily_reminder),
                            summary = stringResource(R.string.settings_daily_reminder_summary),
                            checked = userPreferences?.dailyReminderEnabled == true,
                            onCheckedChange = { viewModel.updateDailyReminderEnabled(it) }
                        )

                        if (userPreferences?.dailyReminderEnabled == true) {
                            SettingsClickableItem(
                                title = stringResource(R.string.settings_daily_reminder_time),
                                value = userPreferences?.dailyReminderTime.toString(),
                                icon = Icons.Default.AccessTime,
                                onClick = { showTimePicker = true }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Display Section
                GlassmorphicSectionHeader(
                    text = stringResource(R.string.settings_display_header),
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                )

                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_sort_completed_bottom),
                            summary = stringResource(R.string.settings_sort_completed_bottom_summary),
                            checked = userPreferences?.sortCompletedHabitsToBottom == true,
                            onCheckedChange = { viewModel.updateSortCompletedHabitsToBottom(it) },
                            icon = Icons.AutoMirrored.Filled.Sort
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker && userPreferences != null) {
        val currentTime = userPreferences!!.dailyReminderTime
        val timeState = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = true
        )

        GlassmorphicDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.settings_daily_reminder_time)) },
            content = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timeState)
                }
            },
            actions = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(onClick = {
                    viewModel.updateDailyReminderTime(LocalTime(timeState.hour, timeState.minute))
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        )
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
        
        GlassmorphicSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            trackWidth = 48.dp,
            trackHeight = 28.dp,
            thumbSize = 22.dp
        )
    }
}

@Composable
fun SettingsClickableItem(
    title: String,
    value: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
