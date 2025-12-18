package com.habittracker.presentation.ui.habits

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.habittracker.R
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.presentation.state.AddEditHabitUiState
import com.habittracker.presentation.ui.components.FieldValidationFeedback
import com.habittracker.presentation.ui.components.LiveValidationIndicator
import com.habittracker.presentation.ui.habits.components.ConfirmDialog
import com.habittracker.presentation.ui.habits.components.EmojiPickerDialog
import com.habittracker.presentation.ui.habits.components.FriendsSelectDialog
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicDropdownMenu
import com.habittracker.presentation.ui.habits.components.GlassmorphicDropdownMenuItem
import com.habittracker.presentation.ui.habits.components.GlassmorphicExposedDropdownMenuBox
import com.habittracker.presentation.ui.habits.components.GlassmorphicFAB
import com.habittracker.presentation.ui.habits.components.GlassmorphicOutlinedButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicSectionHeader
import com.habittracker.presentation.ui.habits.components.GlassmorphicSwitch
import com.habittracker.presentation.ui.habits.components.GlassmorphicTextField
import com.habittracker.presentation.ui.habits.components.InviteCodeCard
import com.habittracker.presentation.ui.habits.components.TahoeDatePicker
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.habits.components.TimePickerDialog
import com.habittracker.presentation.ui.permission.NotificationPermissionHandler
import com.habittracker.presentation.ui.permission.hasNotificationPermission
import com.habittracker.presentation.ui.theme.TahoeElectricBlue
import com.habittracker.presentation.ui.theme.TahoeLightBlue
import com.habittracker.presentation.utils.GroupHabitConstants
import com.habittracker.presentation.viewmodel.AddEditHabitViewModel
import com.habittracker.presentation.viewmodel.IAddEditHabitViewModel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime


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

@Composable
private fun DaysOfWeekSelector(
    selectedDays: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val days = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            val selected = selectedDays.contains(day)
            FilterChip(
                selected = selected,
                onClick = { if (enabled) onToggle(day) },
                label = { Text(dayShortName(day)) },
                enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (isDarkTheme)
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f) else
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.48f),
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
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
    val isDarkTheme = isSystemInDarkTheme()

    GlassmorphicExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        GlassmorphicTextField(
            value = when (currentType) {
                HabitType.PERSONAL -> stringResource(R.string.habit_type_personal)
                HabitType.GROUP -> stringResource(R.string.habit_type_group)
            },
            onValueChange = {},
            label = { Text(stringResource(R.string.habit_type_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            singleLine = true,
            readOnly = true
        )

        GlassmorphicDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            isDarkTheme = isDarkTheme
        ) {
            items.forEach { type ->
                GlassmorphicDropdownMenuItem(
                    text = {
                        Text(
                            text = when (type) {
                                HabitType.PERSONAL -> stringResource(R.string.habit_type_personal)
                                HabitType.GROUP -> stringResource(R.string.habit_type_group)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                    },
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSettings(
    uiState: AddEditHabitUiState,
    viewModel: IAddEditHabitViewModel
) {
    val isDarkTheme = isSystemInDarkTheme()
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.15f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassmorphicSectionHeader(
                text = stringResource(R.string.group_habit_settings),
                isDarkTheme = isDarkTheme
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

                Text(
                    text = stringResource(R.string.max_participants),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                run {
                    val valueOrNull = uiState.maxParticipantsInput.toIntOrNull()
                    val exceeds =
                        valueOrNull?.let { it > GroupHabitConstants.MAX_PARTICIPANTS } == true
                    val tooLow = valueOrNull == 1

                    GlassmorphicTextField(
                        value = uiState.maxParticipantsInput,
                        onValueChange = { input ->
                            // Разрешаем только цифры и ограничиваем длину до 2 символов
                            val filtered = input.filter { it.isDigit() }.take(2)
                            if (filtered != uiState.maxParticipantsInput) {
                                viewModel.onMaxParticipantsInputChange(filtered)
                            }
                        },
                        placeholder = {
                            Text(
                                text = "2",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                ),
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .width(60.dp)
                            .height(46.dp),
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    )

                    Text(
                        text = stringResource(R.string.participants_limit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp)
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

            InviteCodeCard(
                inviteCode = uiState.inviteCode,
                isPublic = uiState.isPublic,
                isGenerating = uiState.isGeneratingInviteCode,
                onGenerateCode = {
                    if (uiState.habitId != null) {
                        viewModel.generateInviteCode()
                    } else {
                        // Для новой привычки показываем информационное сообщение
                        (viewModel as? AddEditHabitViewModel)?.showMessage("Код приглашения можно создать после сохранения привычки")
                    }
                },
                onTogglePublic = { viewModel.onIsPublicChange(it) },
                onShareCode = { viewModel.shareInviteCode(it) },
                onDisableCode = { viewModel.disableInviteCode() },
                isNewHabit = uiState.habitId == null
            )

            // Кнопка приглашения друзей
            GlassmorphicOutlinedButton(
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
                    text = stringResource(
                        R.string.selected_friends,
                        uiState.selectedFriendsToInvite.size
                    ),
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
                val enabled =
                    uiState.selectedFriendsToInvite.isNotEmpty() && !uiState.isSendingInvitations
                val wouldExceedLimit =
                    (uiState.participants.size + uiState.selectedFriendsToInvite.size) > uiState.maxParticipants

                GlassmorphicButton(
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
                    Text(
                        stringResource(
                            R.string.invite_selected,
                            uiState.selectedFriendsToInvite.size
                        )
                    )
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

    GlassmorphicExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        GlassmorphicTextField(
            value = when (currentFrequency) {
                HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
            },
            onValueChange = {},
            label = { Text(stringResource(R.string.habit_frequency_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled),
            singleLine = true,
            readOnly = true
        )

        GlassmorphicDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            isDarkTheme = isSystemInDarkTheme()
        ) {
            items.forEach { frequency ->
                GlassmorphicDropdownMenuItem(
                    text = {
                        Text(
                            text = when (frequency) {
                                HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                                HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                                HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                                HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                                HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                                HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
        Log.d(
            "AddEditHabitScreen",
            "[compose] reminder: enabled=${uiState.reminderEnabled}, time=${uiState.reminderTime}"
        )
    }

    LaunchedEffect(uiState.isHabitSaved) {
        if (uiState.isHabitSaved) {
            navController.popBackStack()
        }
    }

    // Показываем диалог выбора иконки (Tahoe EmojiPickerDialog)
    if (showIconPicker) {
        val icons = listOf(
            // Fitness & Health
            "🏃", "🏋️", "🧘", "🚴", "🏊", "🧗", "🤸", "🚶", "🛼", "⛹️",
            // Food & Drink
            "🍎", "🥗", "🥦", "🥛", "☕", "💧", "🍵", "🥑", "🥜", "🥝",
            "🍌", "🍔", "🥕", "🌯", "🍼", "🫖", "☕️", "🥃", "🍻", "🍽",
            // Personal Growth
            "📚", "✍️", "🎯", "🎨", "🎵", "🎸", "📖", "📝", "🧠", "🎓",
            "🎧", "🎤", "🏓", "🎹", "🎳", "🛹", "🛠️", "✈️", "🏕️", "💻",
            // Productivity
            "⏰", "📅", "✅", "📊", "📈", "📱", "💡", "🔔", "🎮", "📫",
            // Well-being
            "😴", "💤", "🧘", "🌞", "🌙", "🌱", "🌿", "🌸", "🌍", "🌈",
            // Social & Family
            "👨‍👩‍👧‍👦", "👥", "❤️", "🤝", "👨‍👧", "👩‍👧", "👨‍👩‍👧", "🐕", "🐈", "🐦",
            "🐹", "🐢", "🦷", "💊", "👛", "🎒", "🥊", "🐠", "💐", "🎱",
            // Work & Study
            "💼", "📊", "📝", "📌", "📋", "✂️", "📎", "📏", "📐", "📚",
            // Other
            "🎲", "🧩", "🧵", "🧶", "🛁", "🧼", "🛌", "🧹", "🧺", "🧸",
        )
        EmojiPickerDialog(
            emojis = icons,
            onSelect = { emoji -> viewModel.onIconChange(emoji); showIconPicker = false },
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
        ConfirmDialog(
            title = stringResource(R.string.confirm_apply_reminder_title),
            message = stringResource(R.string.confirm_apply_reminder_message),
            confirmLabel = stringResource(R.string.apply),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showApplyAllConfirm = false
                (viewModel as? AddEditHabitViewModel)?.applyReminderToAllParticipants()
            },
            onDismiss = { showApplyAllConfirm = false }
        )
    }

    // Показываем диалог выбора друзей
    if (uiState.showFriendSelector) {
        FriendsSelectDialog(
            friends = uiState.availableFriends,
            selectedIds = uiState.selectedFriendsToInvite.map { it.id }.toSet(),
            onToggle = { userId ->
                val friend = uiState.availableFriends.firstOrNull { it.id == userId }
                if (friend != null) {
                    if (uiState.selectedFriendsToInvite.any { it.id == userId }) {
                        viewModel.deselectFriend(friend)
                    } else {
                        viewModel.selectFriend(friend)
                    }
                }
            },
            onConfirm = { viewModel.onShowFriendSelector(false) },
            onDismiss = { viewModel.onShowFriendSelector(false) }
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

    // Оборачиваем весь экран в градиентный фон Tahoe
    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent, // Делаем фон Scaffold прозрачным
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (uiState.habitId == null) {
                                stringResource(R.string.habit_new_title)
                            } else {
                                "Редактировать привычку"
                            }
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    actions = {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                // Анимированная кнопка сохранения
                val isSaveEnabled =
                    uiState.title.isNotBlank() && !uiState.isLoading && !uiState.isHabitSaved
                val fabScale by animateFloatAsState(
                    targetValue = if (uiState.isLoading) 0.8f else 1f,
                    animationSpec = tween(300),
                    label = stringResource(R.string.animation_fab_scale)
                )

                if (isSaveEnabled) {
                    GlassmorphicFAB(
                        onClick = { viewModel.saveHabit() },
                        isDarkTheme = isSystemInDarkTheme(),
                        modifier = Modifier
                            .padding(bottom = 62.dp)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
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
                    GlassmorphicTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.onTitleChange(it) },
                        label = { Text(stringResource(R.string.habit_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Title, null) },
                    )
                    FieldValidationFeedback(
                        errorMessage = uiState.titleError
                            ?: if (uiState.title.isBlank()) stringResource(R.string.habit_name_required_hint) else null
                    )
                }

                item {
                    // Описание
                    GlassmorphicTextField(
                        value = uiState.description,
                        onValueChange = { viewModel.onDescriptionChange(it) },
                        label = { Text(stringResource(R.string.habit_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        leadingIcon = { Icon(Icons.Default.Description, null) },
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
                            viewModel = viewModel
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
                        ConfirmDialog(
                            title = stringResource(R.string.confirm_change_frequency_title),
                            message = stringResource(R.string.confirm_change_frequency_message),
                            confirmLabel = stringResource(R.string.apply),
                            cancelLabel = stringResource(R.string.action_cancel),
                            onConfirm = {
                                pendingFrequency?.let { viewModel.onFrequencyChange(it) }
                                showFrequencyConfirm = false
                                pendingFrequency = null
                            },
                            onDismiss = { showFrequencyConfirm = false; pendingFrequency = null }
                        )
                    }
                }

                // Выбор дней недели для WEEKLY/TWICE_A_WEEK/CUSTOM
                item {
                    when (uiState.frequency) {
                        HabitFrequency.WEEKLY, HabitFrequency.TWICE_A_WEEK, HabitFrequency.CUSTOM -> {
                            Spacer(modifier = Modifier.height(6.dp))

                            val hint = when (uiState.frequency) {
                                HabitFrequency.WEEKLY -> stringResource(R.string.active_days_hint_weekly)
                                HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.active_days_hint_twice)
                                else -> stringResource(R.string.active_days_hint_custom)
                            }

                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            DaysOfWeekSelector(
                                selectedDays = uiState.reminderDaysInput,
                                onToggle = { day -> viewModel.onReminderDayToggle(day) },
                                enabled = !uiState.isLoading && !uiState.isHabitSaved
                            )

                            // Мягкая валидация выбора дней
                            val warnText = when (uiState.frequency) {
                                HabitFrequency.WEEKLY -> if (uiState.reminderDaysInput.size != 1) stringResource(
                                    R.string.reminder_days_hint_weekly
                                ) else null

                                HabitFrequency.TWICE_A_WEEK -> if (uiState.reminderDaysInput.size != 2) stringResource(
                                    R.string.reminder_days_hint_twice
                                ) else null

                                HabitFrequency.CUSTOM -> if (uiState.reminderDaysInput.isEmpty()) stringResource(
                                    R.string.reminder_days_hint_custom
                                ) else null

                                else -> null
                            }

                            if (warnText != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = warnText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.active_days_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        else -> { /* no-op */
                        }
                    }
                }

                item {
                    // Целевое значение
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassmorphicTextField(
                            value = uiState.targetValueInput,
                            onValueChange = { input ->
                                viewModel.onTargetValueInputChange(input)
                            },
                            label = { Text(stringResource(R.string.habit_target_value_label)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Timeline, null) },
                        )

                        GlassmorphicTextField(
                            value = uiState.unit,
                            onValueChange = { viewModel.onUnitChange(it) },
                            label = { Text(stringResource(R.string.habit_unit_label)) },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                        )
                    }
                    FieldValidationFeedback(uiState.targetValueError)
                }

                // Секция выбора дат привычки
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    val isDarkTheme = isSystemInDarkTheme()
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        isDarkTheme = isDarkTheme,
                        cornerRadius = 16.dp,
                        glassAlpha = if (isDarkTheme) 0.15f else 0.85f,
                        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
                        elevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) TahoeLightBlue else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                                GlassmorphicSectionHeader(
                                    text = stringResource(R.string.habit_dates_section),
                                    isDarkTheme = isDarkTheme
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Дата начала
                            TahoeDatePicker(
                                date = uiState.startDate,
                                onDateSelected = viewModel::onStartDateChange,
                                label = stringResource(R.string.habit_start_date_label),
                                placeholder = stringResource(R.string.habit_start_date_placeholder),
                                isEnabled = uiState.canEditStartDate,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!uiState.canEditStartDate) {
                                Text(
                                    text = stringResource(R.string.habit_start_date_locked_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Дата окончания
                            TahoeDatePicker(
                                date = uiState.endDate,
                                onDateSelected = viewModel::onEndDateChange,
                                label = stringResource(R.string.habit_end_date_label),
                                placeholder = stringResource(R.string.habit_end_date_placeholder),
                                isEnabled = true,
                                minDate = uiState.startDate ?: kotlinx.datetime.Clock.System.now()
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                                    .date,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = stringResource(R.string.habit_end_date_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Напоминание
                    val isDarkTheme = isSystemInDarkTheme()
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        isDarkTheme = isDarkTheme,
                        cornerRadius = 16.dp,
                        glassAlpha = if (isDarkTheme) 0.15f else 0.85f,
                        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
                        elevation = 2.dp
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
                                    tint = if (isDarkTheme) TahoeLightBlue else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 16.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    GlassmorphicSectionHeader(
                                        text = stringResource(R.string.habit_reminder_label),
                                        isDarkTheme = isDarkTheme
                                    )
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
                                                val timePart = stringResource(
                                                    R.string.at_time_format,
                                                    time.hour,
                                                    time.minute
                                                )
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
                                                        ).associateWith {
                                                            stringResource(
                                                                dayShortNameRes(it)
                                                            )
                                                        }
                                                        val daysText = uiState.reminderDaysInput
                                                            .sortedBy { it.value }
                                                            .joinToString(", ") { day ->
                                                                dayNames[day] ?: ""
                                                            }
                                                        if (daysText.isNotBlank()) "$daysText $timePart" else stringResource(
                                                            R.string.habit_reminder_not_set
                                                        )
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
                                        GlassmorphicSwitch(
                                            checked = uiState.reminderEnabled,
                                            onCheckedChange = { isChecked ->
                                                if (isChecked) {
                                                    if (!hasPermission) {
                                                        shouldRequestNotificationPermission = true
                                                    }
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
                                            isDarkTheme = isDarkTheme
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
                                    Text(
                                        text = stringResource(R.string.habit_change_reminder_time),
                                        color = TahoeElectricBlue
                                    )
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
                                            modifier = Modifier
                                                .size(18.dp)
                                                .padding(end = 6.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.reminder_personal_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (uiState.canApplyReminderToAll) {
                                        GlassmorphicButton(
                                            onClick = { showApplyAllConfirm = true },
                                            enabled = !uiState.isLoading && !uiState.isHabitSaved,
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
                    uiState.groupHabitValidationResult?.let { validationResult ->
                        // Не показываем карточку:
                        // 1) когда все поля корректны (нет варнингов)
                        // 2) когда единственная ошибка — это ошибка заголовка, уже показанная под полем
                        val onlyTitleError = validationResult.errors.size == 1 &&
                                uiState.titleError != null &&
                                validationResult.errors.first() == uiState.titleError

                        val shouldShowValidationCard =
                            (!validationResult.isValid || validationResult.warnings.isNotEmpty()) && !onlyTitleError

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
        val viewModel = remember { PreviewAddEditHabitViewModel() }

        // Set initial state
        LaunchedEffect(Unit) {
            viewModel.onTitleChange("Бег по утрам")
            viewModel.onDescriptionChange("Пробежка в парке")
            viewModel.onIconChange("🏃")
            viewModel.onTypeChange(HabitType.PERSONAL)
        }

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
        val viewModel = remember { PreviewAddEditHabitViewModel() }

        // Set initial state for editing
        LaunchedEffect(Unit) {
            viewModel.onTitleChange("Медитация")
            viewModel.onDescriptionChange("Утренняя медитация 10 минут")
            viewModel.onIconChange("🧘")
            viewModel.onTypeChange(HabitType.GROUP)
            viewModel.onFrequencyChange(HabitFrequency.DAILY)
        }

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
        val viewModel = remember {
            PreviewAddEditHabitViewModel(
                testState = AddEditHabitUiState(
                    isLoading = true,
                    title = "Загрузка...",
                    description = "Загружаем данные привычки"
                )
            )
        }

        AddEditHabitScreen(
            navController = navController,
            viewModel = viewModel
        )
    }
}