package com.habittracker.presentation.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.habittracker.R
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.presentation.ui.permission.NotificationPermissionHandler
import com.habittracker.presentation.ui.permission.hasNotificationPermission
import com.habittracker.presentation.viewmodel.AddEditHabitUiState
import com.habittracker.presentation.viewmodel.AddEditHabitViewModel
import com.habittracker.presentation.viewmodel.IAddEditHabitViewModel
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
                .menuAnchor()
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
private fun FrequencySelector(
    currentFrequency: HabitFrequency,
    onFrequencySelected: (HabitFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val items = HabitFrequency.entries
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = when (currentFrequency) {
                HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.habit_frequency_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { frequency ->
                DropdownMenuItem(
                    text = { 
                        Text(when (frequency) {
                            HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                            HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                            HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                            HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                            HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                        })
                    },
                    onClick = {
                        onFrequencySelected(frequency)
                        expanded = false
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
    var shouldRequestNotificationPermission by remember { mutableStateOf(false) }
    val hasPermission = hasNotificationPermission()
    val snackbarHostState = remember { SnackbarHostState() }

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
            selectedColor = uiState.color,
            onColorSelected = { viewModel.onColorChange(it) },
            onDismiss = { showColorPicker = false }
        )
    }

    // Показываем диалог выбора времени
    if (showTimePicker) {
        val currentTime = uiState.reminderTime ?: LocalTime(12, 0)
        TimePickerDialog(
            initialTime = currentTime,
            onTimeSelected = { viewModel.onReminderTimeChange(it) },
            onDismiss = { showTimePicker = false }
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
            val fabScale by animateFloatAsState(
                targetValue = if (uiState.isLoading) 0.8f else 1f,
                animationSpec = tween(300),
                label = stringResource(R.string.animation_fab_scale)
            )
            
            val fabColor by animateColorAsState(
                targetValue = if (uiState.isLoading) 
                    MaterialTheme.colorScheme.surfaceVariant 
                else 
                    MaterialTheme.colorScheme.primary,
                animationSpec = tween(300),
                label = stringResource(R.string.animation_fab_color)
            )
            
            FloatingActionButton(
                onClick = { 
                    if (!uiState.isLoading && !uiState.isHabitSaved) {
                        viewModel.saveHabit()
                    }
                },
                containerColor = fabColor,
                contentColor = if (uiState.isLoading) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                else 
                    MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                ),
                modifier = Modifier
                    //.padding(16.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
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
                            .background(Color(uiState.color.toColorInt()))
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
                                .background(Color(uiState.color.toColorInt()))
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
                // Частота
                FrequencySelector(
                    currentFrequency = uiState.frequency,
                    onFrequencySelected = { viewModel.onFrequencyChange(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Целевое значение
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.targetValue.toString(),
                        onValueChange = { 
                            val value = it.toIntOrNull()
                            if (value != null && value > 0) {
                                viewModel.onTargetValueChange(value)
                            }
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
                                Text(
                                    text = if (uiState.isReminderEnabled) {
                                        uiState.reminderTime?.let { time ->
                                            stringResource(R.string.habit_reminder_time_format, time.hour, time.minute)
                                        } ?: stringResource(R.string.habit_reminder_not_set)
                                    } else {
                                        stringResource(R.string.habit_reminder_disabled)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = uiState.isReminderEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!hasPermission) {
                                            shouldRequestNotificationPermission = true
                                        }
                                    }
                                    viewModel.onReminderEnabledChange(isChecked)
                                    if (isChecked && uiState.reminderTime == null) {
                                        showTimePicker = true
                                    }
                                },
                                enabled = !uiState.isLoading && !uiState.isHabitSaved
                            )
                        }

                        if (uiState.isReminderEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading && !uiState.isHabitSaved
                            ) {
                                Text(stringResource(R.string.habit_change_reminder_time))
                            }
                        }
                    }
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