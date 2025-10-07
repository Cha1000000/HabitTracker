package com.habittracker.presentation.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.AutoMirrored.Filled
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.User
import com.habittracker.presentation.ui.components.SectionCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicDialog
import com.habittracker.presentation.ui.habits.components.GradientProgressBar
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.utils.DateTimeFormatter
import com.habittracker.presentation.viewmodel.GroupHabitDetailViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Экран детальной информации о групповой привычке
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupHabitDetailScreen(
    habitId: String,
    navController: NavController,
    viewModel: GroupHabitDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit = { navController.popBackStack() }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Загрузка данных привычки
    LaunchedEffect(habitId) {
        viewModel.loadHabitDetails(habitId)
    }
    
    // Обработка сообщений
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccessMessage()
        }
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent, // Прозрачный фон Scaffold
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.habit?.title
                                ?: stringResource(R.string.group_habit_details),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        // Меню действий
                        if (uiState.isOwner) {
                            IconButton(
                                onClick = { viewModel.toggleManageMode() }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isManageMode) Icons.Default.Done else Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.manage_group)
                                )
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
                // Индикатор загрузки
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
                    // Основная информация о привычке
                    item {
                        HabitInfoSection(
                            habit = uiState.habit,
                            isCompletedToday = uiState.isCompletedToday,
                            isMarkingCompletion = uiState.isMarkingCompletion,
                            onToggleCompletion = { viewModel.toggleHabitCompletion() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Прогресс группы
                    item {
                        GroupProgressSection(
                            participants = uiState.participants,
                            habitEntries = uiState.habitEntries,
                            currentStreak = uiState.groupStreak,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Участники группы
                    item {
                        ParticipantsSection(
                            participants = uiState.participants,
                            habitEntries = uiState.habitEntries,
                            isManageMode = uiState.isManageMode,
                            onRemoveParticipant = { userId ->
                                viewModel.removeParticipant(userId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Управление приглашениями (только для владельца)
                    if (uiState.isOwner) {
                        item {
                            InviteManagementSection(
                                habit = uiState.habit,
                                inviteCode = uiState.inviteCode,
                                pendingInvitations = uiState.pendingInvitations,
                                isGeneratingCode = uiState.isGeneratingCode,
                                onGenerateCode = { viewModel.generateInviteCode() },
                                onShareCode = { code -> viewModel.shareInviteCode(code) },
                                onDisableCode = { viewModel.disableInviteCode() },
                                onTogglePublic = { isPublic -> viewModel.togglePublicAccess(isPublic) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // История активности
                    item {
                        ActivityHistorySection(
                            habitEntries = uiState.habitEntries,
                            participants = uiState.participants,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Кнопка выхода из группы (для участников)
                    if (!uiState.isOwner) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            LeaveGroupSection(
                                onLeaveGroup = { 
                                    viewModel.leaveGroup {
                                        navController.popBackStack()
                                    }
                                },
                                isLoading = uiState.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Секция с основной информацией о привычке
 */
@Composable
private fun HabitInfoSection(
    habit: Habit?,
    modifier: Modifier = Modifier,
    isCompletedToday: Boolean = false,
    isMarkingCompletion: Boolean = false,
    onToggleCompletion: () -> Unit = {},
) {
    habit?.let { h ->
        SectionCard(
            modifier = modifier,
            outlined = false
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка привычки с улучшенным дизайном
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            color = try {
                                Color(h.color.toColorInt()).copy(alpha = 0.1f)
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = h.icon,
                        style = MaterialTheme.typography.headlineMedium,
                        color = try {
                            Color(h.color.toColorInt())
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = h.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (h.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = h.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Частота в виде чипса
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = when (h.frequency) {
                                        HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                                        HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly) 
                                        HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                                        HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                                        HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                                        HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Filled.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        
                        // Целевое значение в виде чипса
                        if (h.targetValue > 0) {
                            AssistChip(
                                onClick = { },
                                label = {
                                    Text(
                                        text = "${h.targetValue} ${h.unit}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
                
                // Кнопка отметки выполнения
                IconButton(
                    onClick = onToggleCompletion,
                    enabled = !isMarkingCompletion,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompletedToday) {
                                Color(0xFF4CAF50).copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                ) {
                    if (isMarkingCompletion) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (isCompletedToday) {
                                stringResource(R.string.habits_unmark_complete)
                            } else {
                                stringResource(R.string.habits_mark_complete)
                            },
                            tint = if (isCompletedToday) {
                                Color(0xFF4CAF50)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Секция прогресса группы с улучшенным дизайном
 */
@Composable
private fun GroupProgressSection(
    participants: List<User>,
    habitEntries: List<HabitEntry>,
    currentStreak: Int,
    modifier: Modifier = Modifier
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val todayEntries = habitEntries.filter { it.date == today }
    val completedToday = todayEntries.count { it.completed }
    val totalParticipants = participants.size
    val completionRate = if (totalParticipants > 0) completedToday.toFloat() / totalParticipants else 0f
    
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.group_progress),
        subtitle = stringResource(R.string.group_progress_subtitle),
        outlined = false
    ) {
        // Прогресс-бар
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(
                        R.string.group_progress_count_format,
                        completedToday,
                        totalParticipants
                    ) + " " + stringResource(R.string.completed_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.percentage_format,
                        (completionRate * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val barColors = when {
                completionRate >= 0.75f -> listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer
                )
                completionRate >= 0.5f -> listOf(
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.secondaryContainer
                )
                else -> listOf(
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            }
            GradientProgressBar(
                progress = completionRate,
                modifier = Modifier.fillMaxWidth(),
                colors = barColors,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Метрики в ряд
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Выполнено сегодня
            StatisticItem(
                value = "$completedToday",
                label = stringResource(R.string.completed),
                icon = Icons.Default.CheckCircle
            )
            
            // Групповая серия
            StatisticItem(
                value = "$currentStreak",
                label = stringResource(R.string.streak_days),
                icon = Filled.TrendingUp
            )
            
            // Всего участников
            StatisticItem(
                value = "$totalParticipants",
                label = stringResource(R.string.participants),
                icon = Icons.Default.Group
            )
        }
    }
}

/**
 * Элемент статистики для прогресса
 */
@Composable
private fun StatisticItem(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Секция участников группы
 */
@Composable
private fun ParticipantsSection(
    participants: List<User>,
    habitEntries: List<HabitEntry>,
    isManageMode: Boolean,
    onRemoveParticipant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val todayEntries = habitEntries.filter { it.date == today }
    
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.participants),
        subtitle = if (isManageMode) {
            stringResource(R.string.tap_to_remove)
        } else {
            pluralStringResource(R.plurals.participants_plural, participants.size, participants.size)
        },
        outlined = false
    ) {
        if (participants.isEmpty()) {
            Text(
                text = stringResource(R.string.no_participants),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                participants.forEach { participant ->
                    val isCompleted = todayEntries.any { it.userId == participant.id && it.completed }
                    ParticipantListItem(
                        participant = participant,
                        isCompleted = isCompleted,
                        isOwner = participant.id == participants.firstOrNull()?.id,
                        isManageMode = isManageMode,
                        onRemove = { onRemoveParticipant(participant.id) }
                    )
                }
            }
        }
    }
}

/**
 * Элемент списка участника
 */
@Composable
private fun ParticipantListItem(
    participant: User,
    isCompleted: Boolean,
    isOwner: Boolean,
    isManageMode: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isManageMode) Modifier.clickable { onRemove() }
                else Modifier
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            } else {
                Color.Transparent
            }
        ),
        leadingContent = {
            // Аватар участника
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.name.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = participant.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isOwner) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = stringResource(R.string.owner),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = if (isCompleted) {
                    stringResource(R.string.completed_today)
                } else {
                    stringResource(R.string.not_completed_today)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCompleted) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
                if (isManageMode && !isOwner) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = stringResource(R.string.remove_participant),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}

/**
 * Секция управления приглашениями (для автора/владельца привычки)
 */
@Composable
private fun InviteManagementSection(
    habit: Habit?,
    inviteCode: String?,
    pendingInvitations: List<HabitInvitation>,
    isGeneratingCode: Boolean,
    onGenerateCode: () -> Unit,
    onShareCode: (String) -> Unit,
    onDisableCode: () -> Unit,
    onTogglePublic: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.invite_management),
        subtitle = if (pendingInvitations.isNotEmpty()) {
            stringResource(R.string.pending_invitations, pendingInvitations.size)
        } else null,
        outlined = false
    ) {
        // Публичный доступ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.public_access),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.public_access_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = habit?.isPublic == true,
                onCheckedChange = onTogglePublic
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        // Код приглашения
        Column {
            Text(
                text = stringResource(R.string.invite_code),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (inviteCode != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(inviteCode))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_code),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onShareCode(inviteCode) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share_code),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = onDisableCode
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.disable_code),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onGenerateCode,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGeneratingCode
                ) {
                    if (isGeneratingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.generate_invite_code)
                    )
                }
            }
        }
    }
}

/**
 * Секция истории активности
 */
@Composable
private fun ActivityHistorySection(
    habitEntries: List<HabitEntry>,
    participants: List<User>,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.recent_activity),
        subtitle = stringResource(R.string.recent_activity_description),
        outlined = false,
        action = {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        if (habitEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_activity_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Показываем последние 5 записей
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                habitEntries
                    .sortedByDescending { it.completedAt }
                    .take(5)
                    .forEach { entry ->
                        val participant = participants.find { it.id == entry.userId }
                        if (participant != null) {
                            ActivityItem(
                                participant = participant,
                                entry = entry
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
            }
        }
    }
}

/**
 * Элемент активности
 */
@Composable
private fun ActivityItem(
    participant: User,
    entry: HabitEntry,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = participant.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.completed) {
                    stringResource(R.string.participant_completed, participant.name)
                } else {
                    stringResource(R.string.participant_marked_incomplete, participant.name)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            
            entry.completedAt?.let { completedAt ->
                Text(
                    text = DateTimeFormatter.formatRelativeTime(fromEpochMilliseconds(completedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = if (entry.completed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (entry.completed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Секция выхода из группы с диалогом подтверждения
 */
@Composable
private fun LeaveGroupSection(
    onLeaveGroup: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    // Диалог подтверждения
    if (showConfirmDialog) {
        GlassmorphicDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(text = stringResource(R.string.leave_group_title)) },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.leave_group_warning),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            actions = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                GlassmorphicButton(
                    onClick = {
                        onLeaveGroup()
                        showConfirmDialog = false
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.leave_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
    
    // Кнопка выхода
    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error
        )
    ) {
        Icon(
            imageVector = Filled.ExitToApp,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.leave_group),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupHabitDetailScreenPreview() {
    MaterialTheme {
        // Preview будет добавлен после создания ViewModel
    }
}
