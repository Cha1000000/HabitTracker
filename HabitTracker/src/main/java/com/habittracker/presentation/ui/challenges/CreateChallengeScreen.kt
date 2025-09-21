package com.habittracker.presentation.ui.challenges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.ChallengeType
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.User
import com.habittracker.presentation.viewmodel.CreateChallengeViewModel
import com.habittracker.presentation.viewmodel.DatePickerType
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChallengeScreen(
    onNavigateBack: () -> Unit,
    onChallengeCreated: (String) -> Unit,
    viewModel: CreateChallengeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.create_challenge_title),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        )

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            item {
                OutlinedTextField(
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
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text(stringResource(R.string.challenge_description)) },
                    placeholder = { Text(stringResource(R.string.challenge_description_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }

            // Habit Selection
            item {
                HabitSelectionCard(
                    selectedHabit = uiState.selectedHabit,
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
                OutlinedTextField(
                    value = uiState.reward,
                    onValueChange = viewModel::onRewardChange,
                    label = { Text(stringResource(R.string.reward)) },
                    placeholder = { Text(stringResource(R.string.reward_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }

            // Create Button
            item {
                Button(
                    onClick = {
                        viewModel.createChallenge { challenge ->
                            onChallengeCreated(challenge.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isCreating
                ) {
                    if (uiState.isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.create_challenge_button))
                    }
                }
            }
        }
    }

    // Error Snackbar
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Show snackbar
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

    // TODO: Add DatePickerDialog when needed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitSelectionCard(
    selectedHabit: Habit?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
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
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Нажмите для выбора привычки",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantsSelectionCard(
    selectedParticipants: List<User>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
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
                        text = "${selectedParticipants.size} участников выбрано",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Нажмите для выбора участников",
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
    Card(
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
                OutlinedCard(
                    onClick = onStartDateClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
                OutlinedCard(
                    onClick = onEndDateClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
    Card(
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
            
            ChallengeType.values().forEach { type ->
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
            OutlinedTextField(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_habit)) },
        text = {
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_participants)) },
        text = {
            LazyColumn {
                items(friends) { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedParticipants.contains(friend),
                                onClick = { onParticipantToggle(friend) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedParticipants.contains(friend),
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
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {}
    )
}
