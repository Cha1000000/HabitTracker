package com.habittracker.presentation.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.habittracker.R
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.User
import com.habittracker.presentation.ui.components.FieldValidationFeedback
import com.habittracker.presentation.ui.habits.components.InviteCodeCard
import com.habittracker.presentation.ui.permission.NotificationPermissionHandler
import com.habittracker.presentation.ui.permission.hasNotificationPermission
import com.habittracker.presentation.ui.theme.GlassmorphicFAB
import com.habittracker.presentation.ui.theme.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.AddEditHabitUiState
import com.habittracker.presentation.viewmodel.AddEditHabitViewModel
import com.habittracker.presentation.viewmodel.IAddEditHabitViewModel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    // Initialize time state with the provided initial time
    val timeState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.habit_select_time),
                style = MaterialTheme.typography.titleLarge
            ) 
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                TimePicker(
                    state = timeState,
                    modifier = Modifier.padding(8.dp),
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Selected time preview
                Text(
                    text = "%02d:%02d".format(timeState.hour, timeState.minute),
                    style = MaterialTheme.typography.displaySmall
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = {
                        val selectedTime = LocalTime(
                            hour = timeState.hour,
                            minute = timeState.minute
                        )
                        onTimeSelected(selectedTime)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    )
}

@Composable
private fun dayShortName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.weekday_mon)
    DayOfWeek.TUESDAY -> stringResource(R.string.weekday_tue)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.weekday_wed)
    DayOfWeek.THURSDAY -> stringResource(R.string.weekday_thu)
    DayOfWeek.FRIDAY -> stringResource(R.string.weekday_fri)
    DayOfWeek.SATURDAY -> stringResource(R.string.weekday_sat)
    DayOfWeek.SUNDAY -> stringResource(R.string.weekday_sun)
}

private fun dayShortNameRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.weekday_mon
    DayOfWeek.TUESDAY -> R.string.weekday_tue
    DayOfWeek.WEDNESDAY -> R.string.weekday_wed
    DayOfWeek.THURSDAY -> R.string.weekday_thu
    DayOfWeek.FRIDAY -> R.string.weekday_fri
    DayOfWeek.SATURDAY -> R.string.weekday_sat
    DayOfWeek.SUNDAY -> R.string.weekday_sun
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysOfWeekSelector(
    selectedDays: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
): Unit = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
){
    val days = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )
    days.forEach { day ->
        val selected = selectedDays.contains(day)
        FilterChip(
            selected = selected,
            onClick = { if (enabled) onToggle(day) },
            label = { Text(dayShortName(day)) },
            enabled = enabled,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.48f),
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IconPickerDialog(
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val icons = listOf(
        // Health & Fitness
        "🏃‍♂️", "💪", "🧘‍♀️", "🏋️‍♀️", "🤸", "🚴", "🏊", "🧗", "🤸‍♂️", "🚶‍♂️",
        // Food & Drink
        "🍎", "🥗", "🥦", "🥛", "☕", "💧", "🍵", "🥑", "🥜", "🥝",
        // Personal Growth
        "📚", "✍️", "🎯", "🎨", "🎵", "🎸", "📖", "📝", "🧠", "🎓",
        // Productivity
        "⏰", "📅", "✅", "📊", "📈", "📱", "💡", "🔔", "🎮", "📱",
        // Well-being
        "😴", "💤", "🧘", "🌞", "🌙", "🌱", "🌿", "🌸", "🌍", "🌈",
        // Social & Family
        "👨‍👩‍👧‍👦", "👥", "❤️", "🤝", "👨‍👧", "👩‍👧", "👨‍👩‍👧", "🐕", "🐈", "🐦",
        // Work & Study
        "💼", "📊", "📝", "📌", "📋", "✂️", "📎", "📏", "📐", "📚",
        // Other
        "🎲", "🧩", "🧵", "🧶", "🛁", "🧼", "🛌", "🧹", "🧺", "🧸"
    )
    
    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .wrapContentHeight(),
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.habit_select_icon),
                style = MaterialTheme.typography.headlineSmall
            ) 
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 48.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(icons.size) { index ->
                    val icon = icons[index]
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                onIconSelected(icon)
                                onDismiss()
                            },
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun FriendSelectorDialog(
    friends: List<User>,
    selectedFriends: List<User>,
    onFriendToggle: (User) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.select_friends_to_invite))
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (friends.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_friends_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(friends) { friend ->
                        val isSelected = selectedFriends.contains(friend)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFriendToggle(friend) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onFriendToggle(friend) }
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = friend.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (friend.email.isNotEmpty()) {
                                    Text(
                                        text = friend.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitTypeSelector(
    currentType: HabitType,
    onTypeSelected: (HabitType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val items = HabitType.entries
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = when (currentType) {
                HabitType.PERSONAL -> stringResource(R.string.habit_type_personal)
                HabitType.GROUP -> stringResource(R.string.habit_type_group)
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.habit_type_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { type ->
                DropdownMenuItem(
                    text = { 
                        Text(when (type) {
                            HabitType.PERSONAL -> stringResource(R.string.habit_type_personal)
                            HabitType.GROUP -> stringResource(R.string.habit_type_group)
                        })
                    },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (type) {
                                HabitType.PERSONAL -> Icons.Default.Person
                                HabitType.GROUP -> Icons.Default.Group
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSettings(
    uiState: AddEditHabitUiState,
    viewModel: IAddEditHabitViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.group_habit_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Максимальное количество участников
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                run {
                    val valueOrNull = uiState.maxParticipantsInput.toIntOrNull()
                    val exceeds = valueOrNull?.let { it > com.habittracker.presentation.utils.GroupHabitConstants.MAX_PARTICIPANTS } == true
                    val tooLow = valueOrNull == 1

                    OutlinedTextField(
                        value = uiState.maxParticipantsInput,
                        onValueChange = { input ->
                            // Разрешаем только цифры и ограничиваем длину до 2 символов
                            val filtered = input.filter { it.isDigit() }.take(2)
                            if (filtered != uiState.maxParticipantsInput) {
                                viewModel.onMaxParticipantsInputChange(filtered)
                            }
                        },
                        label = { Text(stringResource(R.string.max_participants)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                        enabled = !uiState.isLoading && !uiState.isHabitSaved,
                        isError = exceeds || tooLow
                    )

                    Text(
                        text = stringResource(R.string.participants_limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Компактное сообщение валидации справа, чтобы не тянуть высоту строки
                    val validationText = when {
                        exceeds -> stringResource(R.string.participants_exceeds_limit)
                        tooLow -> stringResource(R.string.participants_min_limit)
                        else -> null
                    }
                    if (validationText != null) {
                        Text(
                            text = validationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            
            // Публичная привычка
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.public_habit),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.public_habit_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = uiState.isPublic,
                    onCheckedChange = { viewModel.onIsPublicChange(it) },
                    enabled = !uiState.isLoading && !uiState.isHabitSaved
                )
            }
            
            InviteCodeCard(
                inviteCode = uiState.inviteCode,
                isPublic = uiState.isPublic,
                isGenerating = uiState.isLoading,
                onGenerateCode = { viewModel.generateInviteCode() },
                onTogglePublic = { viewModel.onIsPublicChange(it) },
                onShareCode = { viewModel.shareInviteCode(it) },
                onDisableCode = { viewModel.disableInviteCode() }
            )
            
            // Кнопка приглашения друзей
            OutlinedButton(
                onClick = { viewModel.onShowFriendSelector(true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && !uiState.isHabitSaved
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.invite_friends))
            }
            
            // Показываем выбранных друзей
            if (uiState.selectedFriendsToInvite.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.selected_friends, uiState.selectedFriendsToInvite.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(uiState.selectedFriendsToInvite) { friend ->
                        AssistChip(
                            onClick = { viewModel.deselectFriend(friend) },
                            label = { Text(friend.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Кнопка отправки приглашений (Variant B)
            run {
                val enabled = uiState.selectedFriendsToInvite.isNotEmpty() && !uiState.isSendingInvitations
                val wouldExceedLimit = (uiState.participants.size + uiState.selectedFriendsToInvite.size) > uiState.maxParticipants

                Button(
                    onClick = { viewModel.requestInvitations() },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSendingInvitations) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(stringResource(R.string.invite_selected, uiState.selectedFriendsToInvite.size))
                }

                if (wouldExceedLimit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.participants_exceeds_limit),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencySelector(
    currentFrequency: HabitFrequency,
    onFrequencySelected: (HabitFrequency) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = HabitFrequency.entries
    
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = when (currentFrequency) {
                HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
            },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.habit_frequency_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
        )
        
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { frequency ->
                DropdownMenuItem(
                    text = { 
                        Text(when (frequency) {
                            HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                            HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                            HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                            HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                            HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                            HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                        })
                    },
                    onClick = {
                        if (enabled) {
                            onFrequencySelected(frequency)
                            expanded = false
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHabitScreen(
    navController: NavController,
    viewModel: IAddEditHabitViewModel = hiltViewModel<AddEditHabitViewModel>(),
    onBackClick: () -> Unit = { navController.popBackStack() }
) {
    val uiState by viewModel.uiState.collectAsState()
    var showIconPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var timeSelectedDuringDialog by remember { mutableStateOf(false) }
    var pendingEnableFromSwitch by remember { mutableStateOf(false) }
    var shouldRequestNotificationPermission by remember { mutableStateOf(false) }
    val hasPermission = hasNotificationPermission()
    val snackbarHostState = remember { SnackbarHostState() }

    // Диагностируем текущее состояние напоминаний на уровне UI-композиции
    LaunchedEffect(uiState.reminderEnabled, uiState.reminderTime) {
        android.util.Log.d(
            "AddEditHabitScreen",
            "[compose] reminder: enabled=${uiState.reminderEnabled}, time=${uiState.reminderTime}"
        )
    }

    LaunchedEffect(uiState.isHabitSaved) {
        if (uiState.isHabitSaved) {
            navController.popBackStack()
        }
    }

    // Показываем диалог выбора иконки
    if (showIconPicker) {
        IconPickerDialog(
            onIconSelected = { viewModel.onIconChange(it) },
            onDismiss = { showIconPicker = false }
        )
    }

    // Показываем диалог выбора цвета
    if (showColorPicker) {
        ColorPickerDialog(
            selectedColor = String.format("#%08X", uiState.color),
            onColorSelected = { colorHex -> 
                viewModel.onColorChange(Color(colorHex.toColorInt()).toArgb())
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // Сброс флага выбора при каждом открытии диалога
    LaunchedEffect(showTimePicker) {
        if (showTimePicker) timeSelectedDuringDialog = false
    }

    // Показываем диалог выбора времени
    if (showTimePicker) {
        val currentTime = uiState.reminderTime ?: LocalTime(12, 0)
        TimePickerDialog(
            initialTime = currentTime,
            onTimeSelected = { 
                timeSelectedDuringDialog = true
                viewModel.onReminderTimeChange(it) 
            },
            onDismiss = {
                showTimePicker = false
                // Если пользователь закрыл пикер без выбора времени — откатываем тумблер
                if (pendingEnableFromSwitch) {
                    if (!timeSelectedDuringDialog) {
                        viewModel.onReminderEnabledChange(false)
                    }
                    pendingEnableFromSwitch = false
                }
            }
        )
    }

    // Подтверждение применения напоминаний для всех
    var showApplyAllConfirm by remember { mutableStateOf(false) }
    if (showApplyAllConfirm) {
        AlertDialog(
            onDismissRequest = { showApplyAllConfirm = false },
            title = { Text(stringResource(R.string.confirm_apply_reminder_title)) },
            text = { Text(stringResource(R.string.confirm_apply_reminder_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showApplyAllConfirm = false
                    (viewModel as? AddEditHabitViewModel)?.applyReminderToAllParticipants()
                }) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyAllConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Показываем диалог выбора друзей
    if (uiState.showFriendSelector) {
        FriendSelectorDialog(
            friends = uiState.availableFriends,
            selectedFriends = uiState.selectedFriendsToInvite,
            onFriendToggle = { friend ->
                if (uiState.selectedFriendsToInvite.contains(friend)) {
                    viewModel.deselectFriend(friend)
                } else {
                    viewModel.selectFriend(friend)
                }
            },
            onDismiss = { 
                viewModel.onShowFriendSelector(false)
            }
        )
    }

    // Показываем ошибку через Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Long
            )
            viewModel.onErrorShown()
        }
    }

    // Показываем сообщение об успехе через Snackbar
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { successMessage ->
            snackbarHostState.showSnackbar(
                message = successMessage,
                duration = SnackbarDuration.Short
            )
            viewModel.onSuccessMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.isLoading) "" 
                        else if (uiState.isHabitSaved) stringResource(R.string.habit_saving) 
                        else uiState.title.ifBlank { stringResource(R.string.habit_new_title) }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        enabled = !uiState.isLoading && !uiState.isHabitSaved
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Анимированная кнопка сохранения
            val isSaveEnabled = uiState.title.isNotBlank() && !uiState.isLoading && !uiState.isHabitSaved
            val fabScale by animateFloatAsState(
                targetValue = if (uiState.isLoading) 0.8f else 1f,
                animationSpec = tween(300),
                label = stringResource(R.string.animation_fab_scale)
            )
            
            val fabColor by animateColorAsState(
                targetValue = if (!isSaveEnabled || uiState.isLoading)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primary,
                animationSpec = tween(300),
                label = stringResource(R.string.animation_fab_color)
            )
            
            if (isSaveEnabled) {
                GlassmorphicFAB(
                    onClick = { viewModel.saveHabit() },
                    isDarkTheme = isSystemInDarkTheme(),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                            alpha = 1f
                        }
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.cd_save),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Оборачиваем весь контент в градиентный фон Tahoe
        TahoeGradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            isDarkTheme = isSystemInDarkTheme()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp, 6.dp, 12.dp, 56.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            item {
                // Выбор иконки и цвета
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // Иконка с цветным фоном
                    Text(
                        text = uiState.icon,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable { showIconPicker = true }
                            .background(Color(uiState.color))
                            .padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.habit_click_to_change_icon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    //Spacer(modifier = Modifier.height(4.dp))
                    
                    // Выбор цвета
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.habit_color_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(uiState.color))
                                .clickable { showColorPicker = true }
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                        
                        TextButton(
                            onClick = { showColorPicker = true }
                        ) {
                            Text(stringResource(R.string.habit_change_color))
                        }
                    }
                }
            }

            item {
                // Название
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.onTitleChange(it) },
                    label = { Text(stringResource(R.string.habit_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.title.isBlank(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Title, null) },
                    enabled = !uiState.isLoading && !uiState.isHabitSaved
                )
                FieldValidationFeedback(
                    errorMessage = uiState.titleError
                        ?: if (uiState.title.isBlank()) stringResource(R.string.habit_name_required_hint) else null
                )
            }

            item {
                // Описание
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    label = { Text(stringResource(R.string.habit_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    leadingIcon = { Icon(Icons.Default.Description, null) },
                    enabled = !uiState.isLoading && !uiState.isHabitSaved
                )
                FieldValidationFeedback(uiState.descriptionError)
            }

            item {
                // Тип привычки
                HabitTypeSelector(
                    currentType = uiState.type,
                    onTypeSelected = { viewModel.onTypeChange(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Настройки групповой привычки
                if (uiState.type == HabitType.GROUP) {
                    GroupSettings(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                // Частота
                val isGroup = uiState.type == HabitType.GROUP
                val isExisting = uiState.habitId != null
                val isAuthor = uiState.canApplyReminderToAll
                val canEditFrequency = !isGroup || !isExisting || isAuthor

                var showFrequencyConfirm by remember { mutableStateOf(false) }
                var pendingFrequency by remember { mutableStateOf<HabitFrequency?>(null) }

                FrequencySelector(
                    currentFrequency = uiState.frequency,
                    onFrequencySelected = { newFreq ->
                        val shouldConfirm = isGroup && isExisting && isAuthor
                        if (shouldConfirm) {
                            pendingFrequency = newFreq
                            showFrequencyConfirm = true
                        } else {
                            viewModel.onFrequencyChange(newFreq)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEditFrequency,
                )

                if (!canEditFrequency) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.frequency_global_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showFrequencyConfirm && pendingFrequency != null) {
                    AlertDialog(
                        onDismissRequest = { showFrequencyConfirm = false; pendingFrequency = null },
                        title = { Text(stringResource(R.string.confirm_change_frequency_title)) },
                        text = { Text(stringResource(R.string.confirm_change_frequency_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingFrequency?.let { viewModel.onFrequencyChange(it) }
                                showFrequencyConfirm = false
                                pendingFrequency = null
                            }) { Text(stringResource(R.string.apply)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showFrequencyConfirm = false; pendingFrequency = null }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }
            }

            item {
                // Целевое значение
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.targetValueInput,
                        onValueChange = { input ->
                            viewModel.onTargetValueInputChange(input)
                        },
                        label = { Text(stringResource(R.string.habit_target_value_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Timeline, null) },
                        enabled = !uiState.isLoading && !uiState.isHabitSaved
                    )
                    
                    OutlinedTextField(
                        value = uiState.unit,
                        onValueChange = { viewModel.onUnitChange(it) },
                        label = { Text(stringResource(R.string.habit_unit_label)) },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        enabled = !uiState.isLoading && !uiState.isHabitSaved
                    )
                }
                FieldValidationFeedback(uiState.targetValueError)
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Напоминание
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 16.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.habit_reminder_label))
                                if (!uiState.isInitialized) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (uiState.reminderEnabled && uiState.reminderTime != null) {
                                            val time = uiState.reminderTime!!
                                            val timePart = stringResource(R.string.at_time_format, time.hour, time.minute)
                                            when (uiState.frequency) {
                                                HabitFrequency.DAILY -> "${stringResource(R.string.frequency_daily)} $timePart"
                                                HabitFrequency.WEEKDAYS -> "${stringResource(R.string.frequency_weekdays)} $timePart"
                                                HabitFrequency.WEEKENDS -> "${stringResource(R.string.frequency_weekends)} $timePart"
                                                HabitFrequency.WEEKLY, HabitFrequency.TWICE_A_WEEK, HabitFrequency.CUSTOM -> {
                                                    val dayNames = listOf(
                                                        DayOfWeek.MONDAY,
                                                        DayOfWeek.TUESDAY,
                                                        DayOfWeek.WEDNESDAY,
                                                        DayOfWeek.THURSDAY,
                                                        DayOfWeek.FRIDAY,
                                                        DayOfWeek.SATURDAY,
                                                        DayOfWeek.SUNDAY
                                                    ).associateWith { stringResource(dayShortNameRes(it)) }
                                                    val daysText = uiState.reminderDaysInput
                                                        .sortedBy { it.value }
                                                        .joinToString(", ") { day -> dayNames[day] ?: "" }
                                                    if (daysText.isNotBlank()) "$daysText $timePart" else stringResource(R.string.habit_reminder_not_set)
                                                }
                                            }
                                        } else {
                                            stringResource(R.string.habit_reminder_disabled)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!uiState.isInitialized) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Switch(
                                        checked = uiState.reminderEnabled,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                if (!hasPermission) { shouldRequestNotificationPermission = true }
                                                viewModel.onReminderEnabledChange(true)
                                                if (uiState.reminderTime == null) {
                                                    pendingEnableFromSwitch = true
                                                    showTimePicker = true
                                                }
                                            } else {
                                                viewModel.onReminderEnabledChange(false)
                                            }
                                        },
                                        enabled = !uiState.isLoading && !uiState.isHabitSaved,
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        if (uiState.reminderEnabled && uiState.reminderTime != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading && !uiState.isHabitSaved
                            ) {
                                Text(stringResource(R.string.habit_change_reminder_time))
                            }
                            // Выбор дней недели для WEEKLY/TWICE_A_WEEK/CUSTOM
                            when (uiState.frequency) {
                                HabitFrequency.WEEKLY, HabitFrequency.TWICE_A_WEEK, HabitFrequency.CUSTOM -> {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val hint = when (uiState.frequency) {
                                        HabitFrequency.WEEKLY -> stringResource(R.string.reminder_days_hint_weekly)
                                        HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.reminder_days_hint_twice)
                                        else -> stringResource(R.string.reminder_days_hint_custom)
                                    }
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DaysOfWeekSelector(
                                        selectedDays = uiState.reminderDaysInput,
                                        onToggle = { day -> viewModel.onReminderDayToggle(day) },
                                        enabled = !uiState.isLoading && !uiState.isHabitSaved
                                    )
                                    // Мягкая валидация выбора дней
                                    val warnText = when (uiState.frequency) {
                                        HabitFrequency.WEEKLY -> if (uiState.reminderDaysInput.size != 1) stringResource(R.string.reminder_days_hint_weekly) else null
                                        HabitFrequency.TWICE_A_WEEK -> if (uiState.reminderDaysInput.size != 2) stringResource(R.string.reminder_days_hint_twice) else null
                                        HabitFrequency.CUSTOM -> if (uiState.reminderDaysInput.isEmpty()) stringResource(R.string.reminder_days_hint_custom) else null
                                        else -> null
                                    }
                                    if (warnText != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = warnText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                else -> { /* no-op */ }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Лёгкая подпись о персональности напоминания и кнопка "Применить для всех"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.reminder_personal_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (uiState.canApplyReminderToAll) {
                                    Button(
                                        onClick = { showApplyAllConfirm = true },
                                        enabled = !uiState.isLoading && !uiState.isHabitSaved,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Group,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Text(stringResource(R.string.apply_reminder_to_all))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Временно закомментировано для исправления компиляции
                // TODO: Восстановить после добавления groupHabitValidationResult в uiState
                /*
                uiState.groupHabitValidationResult?.let { validationResult ->
                    // Не показываем карточку:
                    // 1) когда все поля корректны (нет варнингов)
                    // 2) когда единственная ошибка — это ошибка заголовка, уже показанная под полем
                    val onlyTitleError = validationResult.errors.size == 1 &&
                            uiState.titleError != null &&
                            validationResult.errors.first() == uiState.titleError

                    val shouldShowValidationCard = (!validationResult.isValid || validationResult.warnings.isNotEmpty()) && !onlyTitleError

                    if (shouldShowValidationCard) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (validationResult.isValid) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiveValidationIndicator(
                                        isValid = validationResult.isValid,
                                        hasWarnings = validationResult.warnings.isNotEmpty()
                                    )
                                }

                                // Отображение всех ошибок
                                validationResult.errors.forEach { error ->
                                    FieldValidationFeedback(error)
                                }

                                // Отображение предупреждений
                                validationResult.warnings.forEach { warning ->
                                    FieldValidationFeedback(warning, isWarning = true)
                                }
                            }
                        }
                    }
                }
                */
            }
        }
        }
    }
    
    // Обработчик запроса разрешения на уведомления
    NotificationPermissionHandler(
        shouldRequest = shouldRequestNotificationPermission,
        onPermissionResult = { isGranted ->
            shouldRequestNotificationPermission = false
            if (!isGranted) {
                // Если разрешение не получено, отключаем напоминания
                viewModel.onReminderEnabledChange(false)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AddEditHabitScreenPreview() {
    MaterialTheme {
        val navController = rememberNavController()
        val viewModel = PreviewAddEditHabitViewModel()
        
        // Set initial state
        viewModel.onTitleChange("Бег по утрам")
        viewModel.onDescriptionChange("Пробежка в парке")
        viewModel.onIconChange("🏃")
        viewModel.onTypeChange(HabitType.PERSONAL)
        
        AddEditHabitScreen(
            navController = navController,
            viewModel = viewModel
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AddEditHabitScreenEditModePreview() {
    MaterialTheme {
        val navController = rememberNavController()
        val viewModel = PreviewAddEditHabitViewModel()
        
        // Set initial state for editing
        viewModel.onTitleChange("Медитация")
        viewModel.onDescriptionChange("Утренняя медитация 10 минут")
        viewModel.onIconChange("🧘")
        viewModel.onTypeChange(HabitType.GROUP)
        viewModel.onFrequencyChange(HabitFrequency.DAILY)
        
        AddEditHabitScreen(
            navController = navController,
            viewModel = viewModel
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AddEditHabitScreenLoadingPreview() {
    MaterialTheme {
        val navController = rememberNavController()
        val viewModel = PreviewAddEditHabitViewModel(
            testState = AddEditHabitUiState(
                isLoading = true,
                title = "Загрузка...",
                description = "Загружаем данные привычки"
            )
        )
        
        AddEditHabitScreen(
            navController = navController,
            viewModel = viewModel
        )
    }
}