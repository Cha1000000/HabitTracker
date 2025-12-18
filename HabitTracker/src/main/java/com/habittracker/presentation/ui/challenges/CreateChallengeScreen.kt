package com.habittracker.presentation.ui.challenges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import com.habittracker.presentation.state.DatePickerType
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicDialog
import com.habittracker.presentation.ui.habits.components.GlassmorphicTextField
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.CreateChallengeViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChallengeScreen(
    onNavigateBack: () -> Unit,
    onChallengeCreated: (String) -> Unit,
    challengeId: String? = null,
    viewModel: CreateChallengeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val isEditMode = challengeId != null

    LaunchedEffect(challengeId) {
        if (challengeId != null) {
            viewModel.startEdit(challengeId)
        }
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEditMode) {
                                if (uiState.title.isNotBlank()) {
                                    stringResource(R.string.edit_challenge_title_format, uiState.title)
                                } else {
                                    stringResource(R.string.edit_challenge_title_fallback)
                                }
                            } else {
                                stringResource(R.string.create_challenge_title)
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // Title
                    item {
                        GlassmorphicTextField(
                            value = uiState.title,
                            onValueChange = viewModel::onTitleChange,
                            label = { Text(stringResource(R.string.challenge_name)) },
                            placeholder = { Text(stringResource(R.string.challenge_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // Description
                    item {
                        GlassmorphicTextField(
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChange,
                            label = { Text(stringResource(R.string.challenge_description)) },
                            placeholder = { Text(stringResource(R.string.challenge_description_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false
                        )
                    }

                    // Habit Selection
                    item {
                        HabitSelectionCard(
                            selectedHabit = uiState.selectedHabit,
                            enabled = !isEditMode,
                            onClick = { viewModel.showHabitSelector() }
                        )
                    }

                    // Participants Selection
                    item {
                        ParticipantsSelectionCard(
                            selectedParticipants = uiState.selectedParticipants,
                            onClick = { viewModel.showParticipantSelector() }
                        )
                    }

                    // Dates
                    item {
                        DateSelectionSection(
                            startDate = uiState.startDate,
                            endDate = uiState.endDate,
                            onStartDateClick = { viewModel.showDatePicker(DatePickerType.START_DATE) },
                            onEndDateClick = { viewModel.showDatePicker(DatePickerType.END_DATE) }
                        )
                    }

                    if (!isEditMode) {
                        // Challenge Rules
                        item {
                            ChallengeRulesSection(
                                challengeType = uiState.challengeType,
                                targetValue = uiState.targetValue,
                                allowMissedDays = uiState.allowMissedDays,
                                requireConsecutive = uiState.requireConsecutive,
                                onChallengeTypeChange = viewModel::onChallengeTypeSelected,
                                onTargetValueChange = viewModel::onTargetValueChange,
                                onAllowMissedDaysChange = viewModel::onAllowMissedDaysChange,
                                onRequireConsecutiveToggle = viewModel::onRequireConsecutiveToggle
                            )
                        }

                        // Reward
                        item {
                            GlassmorphicTextField(
                                value = uiState.reward,
                                onValueChange = viewModel::onRewardChange,
                                label = { Text(stringResource(R.string.reward)) },
                                placeholder = { Text(stringResource(R.string.reward_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false
                            )
                        }
                    }

                    // Save/Create Button
                    item {
                        GlassmorphicButton(
                            onClick = {
                                if (isEditMode) {
                                    viewModel.updateChallenge { challenge ->
                                        onChallengeCreated(challenge.id)
                                    }
                                } else {
                                    viewModel.createChallenge { challenge ->
                                        onChallengeCreated(challenge.id)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isCreating
                        ) {
                            if (uiState.isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (isEditMode) stringResource(R.string.save) else stringResource(R.string.create_challenge_button))
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Error Snackbar
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Dialogs
    if (uiState.showHabitSelector) {
        HabitSelectorDialog(
            habits = uiState.availableHabits,
            onHabitSelected = viewModel::onHabitSelected,
            onDismiss = { viewModel.hideHabitSelector() }
        )
    }

    if (uiState.showParticipantSelector) {
        ParticipantSelectorDialog(
            friends = uiState.availableFriends,
            selectedParticipants = uiState.selectedParticipants,
            onParticipantToggle = viewModel::onParticipantToggle,
            onDismiss = { viewModel.hideParticipantSelector() }
        )
    }

    // DatePicker Dialog
    if (uiState.showDatePicker) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val isStartDate = uiState.datePickerType == DatePickerType.START_DATE
        val minStartDate = if (isEditMode) minOf(today, uiState.startDate) else today
        
        DatePickerDialogWrapper(
            initialDate = if (isStartDate) uiState.startDate else uiState.endDate,
            minDate = if (isStartDate) minStartDate else uiState.startDate,
            onDateSelected = { date ->
                if (isStartDate) {
                    viewModel.onStartDateSelected(date)
                } else {
                    viewModel.onEndDateSelected(date)
                }
            },
            onDismiss = { viewModel.hideDatePicker() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogWrapper(
    initialDate: LocalDate,
    minDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDays() * 24 * 60 * 60 * 1000L
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val epochDays = millis / (24 * 60 * 60 * 1000L)
                        val selectedDate = LocalDate.fromEpochDays(epochDays.toInt())
                        if (selectedDate >= minDate) {
                            onDateSelected(selectedDate)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun HabitSelectionCard(
    selectedHabit: Habit?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.select_habit),
                    style = MaterialTheme.typography.titleMedium
                )
                if (selectedHabit != null) {
                    Text(
                        text = selectedHabit.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tap_to_select_habit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ParticipantsSelectionCard(
    selectedParticipants: List<User>,
    onClick: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.select_participants),
                    style = MaterialTheme.typography.titleMedium
                )
                if (selectedParticipants.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.selected_participants, selectedParticipants.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tap_to_select_participants),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun DateSelectionSection(
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.challenge_dates),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start Date
                GlassmorphicCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onStartDateClick)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.start_date),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = startDate.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // End Date
                GlassmorphicCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEndDateClick)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.end_date),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = endDate.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeRulesSection(
    challengeType: ChallengeType,
    targetValue: String,
    allowMissedDays: String,
    requireConsecutive: Boolean,
    onChallengeTypeChange: (ChallengeType) -> Unit,
    onTargetValueChange: (String) -> Unit,
    onAllowMissedDaysChange: (String) -> Unit,
    onRequireConsecutiveToggle: (Boolean) -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.challenge_rules),
                style = MaterialTheme.typography.titleMedium
            )
            
            // Challenge Type Selection
            Text(
                text = stringResource(R.string.challenge_type),
                style = MaterialTheme.typography.labelMedium
            )
            
            ChallengeType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = challengeType == type,
                            onClick = { onChallengeTypeChange(type) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = challengeType == type,
                        onClick = { onChallengeTypeChange(type) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (type) {
                                ChallengeType.COMPLETION_RATE -> stringResource(R.string.completion_rate)
                                ChallengeType.STREAK -> stringResource(R.string.streak_challenge)
                                ChallengeType.TOTAL_DAYS -> stringResource(R.string.total_days)
                                ChallengeType.FIRST_TO_TARGET -> stringResource(R.string.first_to_target)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = when (type) {
                                ChallengeType.COMPLETION_RATE -> stringResource(R.string.completion_rate_description)
                                ChallengeType.STREAK -> stringResource(R.string.streak_description)
                                ChallengeType.TOTAL_DAYS -> stringResource(R.string.total_days_description)
                                ChallengeType.FIRST_TO_TARGET -> stringResource(R.string.first_to_target_description)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Target Value
            GlassmorphicTextField(
                value = targetValue,
                onValueChange = onTargetValueChange,
                label = { Text(stringResource(R.string.target_value)) },
                placeholder = { Text(stringResource(R.string.target_value_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun HabitSelectorDialog(
    habits: List<Habit>,
    onHabitSelected: (Habit) -> Unit,
    onDismiss: () -> Unit
) {
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_habit)) },
        content = {
            if (habits.isEmpty()) {
                Text(
                    text = stringResource(R.string.select_habit_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            } else {
                LazyColumn {
                    items(habits) { habit ->
                        TextButton(
                            onClick = { onHabitSelected(habit) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = habit.title,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ParticipantSelectorDialog(
    friends: List<User>,
    selectedParticipants: List<User>,
    onParticipantToggle: (User) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedParticipantIds = remember(selectedParticipants) {
        selectedParticipants.map { it.id }.toSet()
    }

    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_participants)) },
        content = {
            LazyColumn {
                items(friends) { friend ->
                    val isSelected = selectedParticipantIds.contains(friend.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onParticipantToggle(friend) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onParticipantToggle(friend) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = friend.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}
