package com.habittracker.presentation.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.presentation.state.GroupHabitResetItem
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicDialog
import com.habittracker.presentation.ui.habits.components.GlassmorphicOutlinedButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicSectionHeader
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.viewmodel.ResetProgressViewModel

private sealed interface ResetAction {
    data object PointsSeason : ResetAction
    data object PointsFull : ResetAction
    data object PersonalSeason : ResetAction
    data object PersonalFull : ResetAction
    data class GroupSeason(val habitId: String, val title: String) : ResetAction
    data class GroupFullMine(val habitId: String, val title: String) : ResetAction
    data class GroupFullAll(val habitId: String, val title: String) : ResetAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetProgressScreen(
    navController: NavController,
    viewModel: ResetProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<ResetAction?>(null) }
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.successResId) {
        uiState.successResId?.let { resId ->
            snackbarHostState.showSnackbar(
                message = context.getString(resId)
            )
            viewModel.onSuccessShown()
        }
    }

    if (pendingAction != null) {
        ResetConfirmationDialog(
            action = pendingAction!!,
            onDismiss = { pendingAction = null },
            onConfirm = {
                when (val action = pendingAction!!) {
                    ResetAction.PointsSeason -> viewModel.resetPointsSeason()
                    ResetAction.PointsFull -> viewModel.resetPointsFull()
                    ResetAction.PersonalSeason -> viewModel.resetPersonalHabitsSeason()
                    ResetAction.PersonalFull -> viewModel.resetPersonalHabitsFull()
                    is ResetAction.GroupSeason -> viewModel.resetGroupHabitSeason(action.habitId)
                    is ResetAction.GroupFullMine -> viewModel.resetGroupHabitFull(action.habitId)
                    is ResetAction.GroupFullAll -> viewModel.resetGroupHabitFullForAll(action.habitId)
                }
                pendingAction = null
            }
        )
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.reset_progress_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
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
                if (uiState.isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                GlassmorphicSectionHeader(
                    text = stringResource(R.string.reset_progress_section_points),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )

                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ResetOptionRow(
                            title = stringResource(R.string.reset_progress_points_season_title),
                            description = stringResource(R.string.reset_progress_points_season_desc),
                            icon = TahoeIcons.ArrowPath,
                            actionLabel = stringResource(R.string.action_season_reset),
                            onClick = { pendingAction = ResetAction.PointsSeason },
                            isDarkTheme = isDarkTheme
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ResetOptionRow(
                            title = stringResource(R.string.reset_progress_points_full_title),
                            description = stringResource(R.string.reset_progress_points_full_desc),
                            icon = TahoeIcons.Broom,
                            actionLabel = stringResource(R.string.action_full_reset),
                            isDestructive = true,
                            onClick = { pendingAction = ResetAction.PointsFull },
                            isDarkTheme = isDarkTheme
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                GlassmorphicSectionHeader(
                    text = stringResource(R.string.reset_progress_section_habits),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )

                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.reset_progress_personal_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                R.string.reset_progress_personal_desc,
                                uiState.personalHabitsCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GlassmorphicOutlinedButton(
                                onClick = { pendingAction = ResetAction.PersonalSeason },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.action_season_reset))
                            }
                            DestructiveOutlinedButton(
                                onClick = { pendingAction = ResetAction.PersonalFull },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.action_full_reset))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.reset_progress_group_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.reset_progress_group_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (uiState.groupHabits.isEmpty()) {
                            Text(
                                text = stringResource(R.string.reset_progress_group_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.groupHabits.forEach { habit ->
                                GroupHabitResetCard(
                                    habit = habit,
                                    onSeasonReset = {
                                        pendingAction = ResetAction.GroupSeason(habit.habitId, habit.title)
                                    },
                                    onFullResetMine = {
                                        pendingAction = ResetAction.GroupFullMine(habit.habitId, habit.title)
                                    },
                                    onFullResetAll = {
                                        pendingAction = ResetAction.GroupFullAll(habit.habitId, habit.title)
                                    },
                                    isDarkTheme = isDarkTheme
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetOptionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    isDarkTheme: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDestructive) {
            DestructiveOutlinedButton(onClick = onClick) {
                Text(actionLabel)
            }
        } else {
            GlassmorphicOutlinedButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun GroupHabitResetCard(
    habit: GroupHabitResetItem,
    onSeasonReset: () -> Unit,
    onFullResetMine: () -> Unit,
    onFullResetAll: () -> Unit,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = TahoeIcons.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (habit.isOwner) {
                            stringResource(R.string.reset_progress_role_owner)
                        } else {
                            stringResource(R.string.reset_progress_role_participant)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(
                        R.string.reset_progress_group_participants,
                        habit.participantsCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassmorphicOutlinedButton(
                    onClick = onSeasonReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_season_reset_short))
                }
                DestructiveOutlinedButton(
                    onClick = onFullResetMine,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_full_reset))
                }
            }

            if (habit.isOwner) {
                Spacer(modifier = Modifier.height(12.dp))
                DestructiveOutlinedButton(
                    onClick = onFullResetAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_full_reset_all))
                }
            }
        }
    }
}

@Composable
private fun DestructiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error
        )
    ) {
        content()
    }
}

@Composable
private fun ResetConfirmationDialog(
    action: ResetAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val titleRes = when (action) {
        ResetAction.PointsSeason -> R.string.reset_confirm_points_season_title
        ResetAction.PointsFull -> R.string.reset_confirm_points_full_title
        ResetAction.PersonalSeason -> R.string.reset_confirm_personal_season_title
        ResetAction.PersonalFull -> R.string.reset_confirm_personal_full_title
        is ResetAction.GroupSeason -> R.string.reset_confirm_group_season_title
        is ResetAction.GroupFullMine -> R.string.reset_confirm_group_full_title
        is ResetAction.GroupFullAll -> R.string.reset_confirm_group_full_all_title
    }

    val message = when (action) {
        ResetAction.PointsSeason -> stringResource(R.string.reset_confirm_points_season_message)
        ResetAction.PointsFull -> stringResource(R.string.reset_confirm_points_full_message)
        ResetAction.PersonalSeason -> stringResource(R.string.reset_confirm_personal_season_message)
        ResetAction.PersonalFull -> stringResource(R.string.reset_confirm_personal_full_message)
        is ResetAction.GroupSeason -> stringResource(
            R.string.reset_confirm_group_season_message,
            action.title
        )
        is ResetAction.GroupFullMine -> stringResource(
            R.string.reset_confirm_group_full_message,
            action.title
        )
        is ResetAction.GroupFullAll -> stringResource(
            R.string.reset_confirm_group_full_all_message,
            action.title
        )
    }

    val confirmLabel = when (action) {
        ResetAction.PointsSeason,
        ResetAction.PersonalSeason,
        is ResetAction.GroupSeason -> stringResource(R.string.action_start_season)
        else -> stringResource(R.string.action_reset)
    }

    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(titleRes)) },
        content = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            GlassmorphicButton(onClick = onConfirm) {
                Text(text = confirmLabel)
            }
        }
    )
}
