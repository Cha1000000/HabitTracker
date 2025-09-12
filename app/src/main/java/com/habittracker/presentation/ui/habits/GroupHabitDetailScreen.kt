package com.habittracker.presentation.ui.habits

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.User
import com.habittracker.presentation.ui.habits.components.InviteCodeCard
import com.habittracker.presentation.viewmodel.GroupHabitDetailViewModel
import kotlinx.datetime.Clock
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.habit?.title ?: stringResource(R.string.group_habit_details),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                }
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
                    HabitInfoCard(
                        habit = uiState.habit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Прогресс группы
                item {
                    GroupProgressCard(
                        participants = uiState.participants,
                        habitEntries = uiState.habitEntries,
                        currentStreak = uiState.groupStreak,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Участники группы
                item {
                    ParticipantsCard(
                        participants = uiState.participants,
                        habitEntries = uiState.habitEntries,
                        isOwner = uiState.isOwner,
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
                        InviteManagementCard(
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
                    ActivityHistoryCard(
                        habitEntries = uiState.habitEntries,
                        participants = uiState.participants,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Кнопка выхода из группы (для участников)
                if (!uiState.isOwner) {
                    item {
                        LeaveGroupButton(
                            onLeaveGroup = { viewModel.leaveGroup() },
                            isLoading = uiState.isLoading
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка с основной информацией о привычке
 */
@Composable
private fun HabitInfoCard(
    habit: Habit?,
    modifier: Modifier = Modifier
) {
    habit?.let { h ->
        Card(
            modifier = modifier,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка привычки
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(h.color))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = h.icon,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = h.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (h.description.isNotBlank()) {
                        Text(
                            text = h.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Частота
                        Text(
                            text = when (h.frequency) {
                                HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                                HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                                HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                                HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                                HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                                HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Целевое значение
                        if (h.targetValue > 0) {
                            Text(
                                text = "${h.targetValue} ${h.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка прогресса группы
 */
@Composable
private fun GroupProgressCard(
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
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.group_progress),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Прогресс сегодня
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$completedToday/$totalParticipants",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.completed_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Групповая серия
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$currentStreak",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(R.string.group_streak_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Процент выполнения
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(completionRate * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (completionRate >= 0.8f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.completion_rate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Прогресс-бар
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { completionRate },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (completionRate >= 0.8f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Карточка участников группы
 */
@Composable
private fun ParticipantsCard(
    participants: List<User>,
    habitEntries: List<HabitEntry>,
    isOwner: Boolean,
    isManageMode: Boolean,
    onRemoveParticipant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.participants, participants.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (isOwner && isManageMode) {
                    Text(
                        text = stringResource(R.string.tap_to_remove),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            participants.forEach { participant ->
                val todayEntry = habitEntries.find { 
                    it.userId == participant.id && it.date == today 
                }
                val isCompleted = todayEntry?.completed == true
                
                ParticipantItem(
                    participant = participant,
                    isCompleted = isCompleted,
                    isOwner = isOwner,
                    isManageMode = isManageMode,
                    onRemove = { onRemoveParticipant(participant.id) }
                )
                
                if (participant != participants.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Элемент участника группы
 */
@Composable
private fun ParticipantItem(
    participant: User,
    isCompleted: Boolean,
    isOwner: Boolean,
    isManageMode: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCompleted) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар участника
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = participant.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = if (isCompleted) 
                    stringResource(R.string.completed_today) 
                else 
                    stringResource(R.string.not_completed_today),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCompleted) 
                    Color(0xFF4CAF50) 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Индикатор выполнения
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        // Кнопка удаления (только для владельца в режиме управления)
        if (isOwner && isManageMode) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RemoveCircle,
                    contentDescription = stringResource(R.string.remove_participant),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Карточка управления приглашениями
 */
@Composable
private fun InviteManagementCard(
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
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.invite_management),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Invite-код
            InviteCodeCard(
                inviteCode = inviteCode,
                isPublic = habit?.isPublic == true,
                isGenerating = isGeneratingCode,
                onGenerateCode = onGenerateCode,
                onTogglePublic = onTogglePublic,
                onShareCode = onShareCode,
                onDisableCode = onDisableCode
            )
            
            // Ожидающие приглашения
            if (pendingInvitations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = stringResource(R.string.pending_invitations, pendingInvitations.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Карточка истории активности
 */
@Composable
private fun ActivityHistoryCard(
    habitEntries: List<HabitEntry>,
    participants: List<User>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.recent_activity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (habitEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_activity_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Показываем последние 5 записей
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
                    text = completedAt.toString(), // TODO: Форматирование времени
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
 * Кнопка выхода из группы
 */
@Composable
private fun LeaveGroupButton(
    onLeaveGroup: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.leave_group_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onLeaveGroup,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.leave_group))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupHabitDetailScreenPreview() {
    MaterialTheme {
        // Preview будет добавлен после создания ViewModel
    }
}
