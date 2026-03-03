package com.habittracker.presentation.ui.habits

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.domain.model.HabitEntry
import com.habittracker.presentation.ui.components.SectionCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicDropdownMenu
import com.habittracker.presentation.ui.habits.components.GlassmorphicDropdownMenuItem
import com.habittracker.presentation.ui.habits.components.HabitEntryDetailSheet
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.habits.components.ConfirmDialog
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.utils.DateTimeFormatter
import com.habittracker.presentation.viewmodel.SelfHabitDetailViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfHabitDetailScreen(
    habitId: String,
    selectedDate: LocalDate? = null,
    navController: NavController,
    viewModel: SelfHabitDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit = { navController.popBackStack() }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showResetMenu by remember { mutableStateOf(false) }
    var showResetSeasonDialog by remember { mutableStateOf(false) }
    var showResetFullDialog by remember { mutableStateOf(false) }
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    val effectiveDate = selectedDate ?: remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    LaunchedEffect(habitId, effectiveDate) {
        viewModel.loadHabitDetails(habitId, effectiveDate)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearSuccessMessage()
        }
    }

    val addPhotoText = androidx.compose.ui.res.stringResource(R.string.photo_proof_habit_completed)
    val addPhotoActionText = androidx.compose.ui.res.stringResource(R.string.photo_proof_snackbar_action)
    LaunchedEffect(uiState.showPhotoProofPrompt) {
        if (uiState.showPhotoProofPrompt) {
            val result = snackbarHostState.showSnackbar(
                message = addPhotoText,
                actionLabel = addPhotoActionText,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.showPhotoSheet()
            }
            viewModel.dismissPhotoProofPrompt()
        }
    }

    if (showResetSeasonDialog) {
        ConfirmDialog(
            title = androidx.compose.ui.res.stringResource(
                id = R.string.reset_personal_habit_season_title,
                uiState.habit?.title ?: ""
            ),
            message = androidx.compose.ui.res.stringResource(id = R.string.reset_personal_habit_season_message),
            confirmLabel = androidx.compose.ui.res.stringResource(id = R.string.action_reset),
            cancelLabel = androidx.compose.ui.res.stringResource(id = R.string.cancel),
            onConfirm = {
                showResetSeasonDialog = false
                viewModel.resetPersonalHabitSeason()
            },
            onDismiss = { showResetSeasonDialog = false }
        )
    }

    if (showResetFullDialog) {
        ConfirmDialog(
            title = androidx.compose.ui.res.stringResource(
                id = R.string.reset_personal_habit_full_title,
                uiState.habit?.title ?: ""
            ),
            message = androidx.compose.ui.res.stringResource(id = R.string.reset_personal_habit_full_message),
            confirmLabel = androidx.compose.ui.res.stringResource(id = R.string.action_full_reset),
            cancelLabel = androidx.compose.ui.res.stringResource(id = R.string.cancel),
            isDestructive = true,
            onConfirm = {
                showResetFullDialog = false
                viewModel.resetPersonalHabitFull()
            },
            onDismiss = { showResetFullDialog = false }
        )
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.habit?.title
                                ?: androidx.compose.ui.res.stringResource(R.string.self_habit_details),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = androidx.compose.ui.res.stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        if (uiState.habit != null) {
                            IconButton(
                                onClick = {
                                    navController.navigate("self_habit_stat_screen/$habitId?isGroup=false")
                                }
                            ) {
                                Icon(
                                    imageVector = TahoeIcons.ChartBar,
                                    contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_habit_statistics)
                                )
                            }

                            IconButton(
                                onClick = {
                                    navController.navigate("add_edit_habit_screen?habitId=$habitId")
                                }
                            ) {
                                Icon(
                                    imageVector = TahoeIcons.Cog,
                                    contentDescription = androidx.compose.ui.res.stringResource(R.string.habit_edit_title)
                                )
                            }

                            Box {
                                IconButton(onClick = { showResetMenu = true }) {
                                    Icon(
                                        imageVector = TahoeIcons.EllipsisVertical,
                                        contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_more)
                                    )
                                }
                                GlassmorphicDropdownMenu(
                                    expanded = showResetMenu,
                                    onDismissRequest = { showResetMenu = false },
                                    alignment = Alignment.TopEnd,
                                    isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                                ) {
                                    GlassmorphicDropdownMenuItem(
                                        text = { Text(text = androidx.compose.ui.res.stringResource(R.string.action_season_reset_short)) },
                                        leadingIcon = { Icon(imageVector = TahoeIcons.ArrowPath, contentDescription = null) },
                                        onClick = {
                                            showResetMenu = false
                                            showResetSeasonDialog = true
                                        }
                                    )
                                    GlassmorphicDropdownMenuItem(
                                        text = {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(R.string.action_full_reset),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = TahoeIcons.XCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showResetMenu = false
                                            showResetFullDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            if (uiState.isLoading && uiState.habit == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        HabitInfoSection(
                            habit = uiState.habit,
                            modifier = Modifier.fillMaxWidth(),
                            isCompletedToday = uiState.isCompletedToday,
                            isMarkingCompletion = uiState.isMarkingCompletion,
                            hasPhotoProof = !uiState.currentUserPhotoUrl.isNullOrBlank(),
                            currentUserPhotoUrl = uiState.currentUserPhotoUrl,
                            currentUserNote = uiState.currentUserNote,
                            isSavingNote = uiState.isUploadingPhoto,
                            onAddPhotoClick = { viewModel.showPhotoSheet() },
                            onPhotoClick = { url -> fullScreenPhotoUrl = url },
                            onSaveNote = { note -> viewModel.updateHabitEntryNote(note) },
                            onToggleCompletion = { viewModel.toggleHabitCompletion() }
                        )
                    }

                    item {
                        SelfProgressSection(
                            currentStreak = uiState.currentStreak,
                            totalCompletedDays = uiState.totalCompletedDays,
                            completionRatePercent = uiState.completionRatePercent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        SelfRecentActivitySection(
                            entries = uiState.habitEntries,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (uiState.showPhotoSheet && uiState.habit != null) {
        HabitEntryDetailSheet(
            entry = HabitEntry(
                habitId = uiState.habit?.id ?: "",
                userId = uiState.currentUserId,
                date = effectiveDate,
                completed = uiState.isCompletedToday,
                value = 0,
                note = uiState.currentUserNote,
                photoUrl = uiState.currentUserPhotoUrl,
            ),
            date = effectiveDate,
            habitTitle = uiState.habit?.title ?: "",
            habitUnit = uiState.habit?.unit,
            isUploading = uiState.isUploadingPhoto,
            successMessage = uiState.photoUploadSuccessMessage,
            onDismiss = { viewModel.hidePhotoSheet() },
            onAddPhoto = { uri -> viewModel.attachPhoto(uri) },
            onDeletePhoto = { viewModel.deletePhoto() },
            onSaveComment = { note -> viewModel.updateHabitEntryNote(note) },
            onSuccessMessageShown = { viewModel.clearPhotoUploadSuccessMessage() }
        )
    }

    fullScreenPhotoUrl?.let { url ->
        FullScreenPhotoDialog(url = url, onDismiss = { fullScreenPhotoUrl = null })
    }
}

@Composable
private fun SelfProgressSection(
    currentStreak: Int,
    totalCompletedDays: Int,
    completionRatePercent: Int,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        title = androidx.compose.ui.res.stringResource(R.string.stats_habits_title),
        subtitle = androidx.compose.ui.res.stringResource(R.string.stats_habits_avg_completion),
        outlined = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SelfProgressMetric(
                value = "$totalCompletedDays",
                label = androidx.compose.ui.res.stringResource(R.string.completed)
            )
            SelfProgressMetric(
                value = "$currentStreak",
                label = androidx.compose.ui.res.stringResource(R.string.current_streak)
            )
            SelfProgressMetric(
                value = androidx.compose.ui.res.stringResource(R.string.percentage_format, completionRatePercent),
                label = androidx.compose.ui.res.stringResource(R.string.habit_stat_completion_rate)
            )
        }
    }
}

@Composable
private fun SelfProgressMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SelfRecentActivitySection(entries: List<HabitEntry>, modifier: Modifier = Modifier) {
    val sorted = entries.sortedByDescending { it.date }.take(10)
    SectionCard(
        modifier = modifier,
        title = androidx.compose.ui.res.stringResource(R.string.recent_activity),
        subtitle = androidx.compose.ui.res.stringResource(R.string.recent_activity_description),
        outlined = false
    ) {
        if (sorted.isEmpty()) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.self_habit_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sorted.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.date.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                entry.isRestDay -> androidx.compose.ui.res.stringResource(R.string.photo_proof_status_rest)
                                entry.completed -> androidx.compose.ui.res.stringResource(R.string.photo_proof_status_completed)
                                else -> androidx.compose.ui.res.stringResource(R.string.photo_proof_status_missed)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                entry.isRestDay -> Color(0xFFFFB74D)
                                entry.completed -> Color(0xFF4CAF50)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    entry.completedAt?.let { completedAt ->
                        Text(
                            text = DateTimeFormatter.formatRelativeTime(
                                completedAt.toInstant(TimeZone.currentSystemDefault())
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
