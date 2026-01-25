package com.habittracker.presentation.ui.challenges

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeParticipantStats
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.User
import com.habittracker.domain.model.getDaysElapsed
import com.habittracker.domain.model.getDaysRemaining
import com.habittracker.domain.model.getDurationInDays
import com.habittracker.domain.model.getProgressPercentage
import com.habittracker.domain.model.getRulesText
import com.habittracker.presentation.ui.components.UserAvatar
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicDialog
import com.habittracker.presentation.ui.habits.components.GradientProgressBar
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.viewmodel.ChallengeDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeDetailScreen(
    challengeId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChallengeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()

    LaunchedEffect(challengeId) {
        viewModel.loadChallenge(challengeId)
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.challenge?.title ?: stringResource(R.string.challenge_details_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        val isCreator = uiState.challenge?.createdBy == uiState.currentUserId
                        val isActiveOrPending = uiState.challenge?.status != ChallengeStatus.CANCELLED &&
                            uiState.challenge?.status != ChallengeStatus.COMPLETED
                        
                        // Показываем меню для создателя или для участников активных челленджей
                        if (isCreator || isActiveOrPending) {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = TahoeIcons.EllipsisVertical,
                                    contentDescription = "Меню"
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (isCreator) {
                                    // Опции для создателя
                                    if (isActiveOrPending) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.cancel_challenge)) },
                                            onClick = {
                                                showMenu = false
                                                viewModel.showCancelConfirmDialog()
                                            },
                                            leadingIcon = {
                                                Icon(TahoeIcons.XCircle, contentDescription = null)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_challenge)) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.showDeleteConfirmDialog()
                                        },
                                        leadingIcon = {
                                            Icon(TahoeIcons.Trash, contentDescription = null)
                                        }
                                    )
                                } else if (isActiveOrPending) {
                                    // Опция для участников (не создателей)
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.leave_challenge)) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.showLeaveConfirmDialog()
                                        },
                                        leadingIcon = {
                                            Icon(TahoeIcons.CalendarDays, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = TahoeIcons.ExclamationCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.error ?: "Ошибка загрузки",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                uiState.challenge != null -> {
                    ChallengeDetailContent(
                        challenge = uiState.challenge!!,
                        habit = uiState.habit,
                        participants = uiState.participants,
                        participantStats = uiState.participantStats,
                        currentUserId = uiState.currentUserId,
                        isCreator = uiState.challenge!!.createdBy == uiState.currentUserId,
                        onResendInvitation = { userId -> viewModel.resendInvitation(userId) },
                        onRemoveParticipant = { user -> viewModel.showRemoveParticipantDialog(user) },
                        onAddParticipants = { viewModel.showAddParticipantDialog() },
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmDialog() },
            title = { Text("Удалить челлендж?") },
            text = { Text("Это действие нельзя отменить. Челлендж будет удалён для всех участников.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteChallenge(onNavigateBack) }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmDialog() }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (uiState.showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideCancelConfirmDialog() },
            title = { Text("Отменить челлендж?") },
            text = { Text("Челлендж будет отменён. Участники получат уведомление.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.cancelChallenge {} }
                ) {
                    Text("Отменить челлендж", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCancelConfirmDialog() }) {
                    Text("Назад")
                }
            }
        )
    }
    
    // Leave Challenge Confirmation Dialog
    if (uiState.showLeaveConfirmDialog) {
        GlassmorphicDialog(
            onDismissRequest = { viewModel.hideLeaveConfirmDialog() },
            title = { Text(stringResource(R.string.leave_challenge_title)) },
            content = {
                Text(
                    text = stringResource(R.string.leave_challenge_confirmation),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.hideLeaveConfirmDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { viewModel.leaveChallenge(onNavigateBack) }
                    ) {
                        Text(
                            text = stringResource(R.string.leave),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // TODO: Show snackbar
            viewModel.clearError()
        }
    }
    
    // Add Participant Dialog
    if (uiState.showAddParticipantDialog) {
        AddParticipantDialog(
            availableFriends = uiState.availableFriends,
            selectedParticipants = uiState.selectedNewParticipants,
            onParticipantToggle = viewModel::onNewParticipantToggle,
            onConfirm = viewModel::addSelectedParticipants,
            onDismiss = viewModel::hideAddParticipantDialog
        )
    }
    
    // Remove Participant Confirmation Dialog
    val participantToRemove = uiState.participantToRemove
    if (uiState.showRemoveParticipantDialog && participantToRemove != null) {
        GlassmorphicDialog(
            onDismissRequest = { viewModel.hideRemoveParticipantDialog() },
            title = { Text(stringResource(R.string.remove_participant_title)) },
            content = {
                Text(
                    text = stringResource(R.string.remove_participant_confirmation, participantToRemove.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.hideRemoveParticipantDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { viewModel.confirmRemoveParticipant() }
                    ) {
                        Text(
                            text = stringResource(R.string.remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun ChallengeDetailContent(
    challenge: Challenge,
    habit: Habit?,
    participants: List<User>,
    participantStats: Map<String, ChallengeParticipantStats>,
    currentUserId: String?,
    isCreator: Boolean,
    onResendInvitation: (String) -> Unit,
    onRemoveParticipant: (User) -> Unit,
    onAddParticipants: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val daysRemaining = challenge.getDaysRemaining()
    val daysElapsed = challenge.getDaysElapsed()
    val totalDays = challenge.getDurationInDays()
    val progress = challenge.getProgressPercentage()

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Challenge Info Card
        item {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                isDarkTheme = isDarkTheme
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChallengeStatusBadge(
                            status = challenge.status,
                            type = challenge.rules.type
                        )
                        
                        if (challenge.reward.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = challenge.reward,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (challenge.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = challenge.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Прогресс",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$daysElapsed / $totalDays дней",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        GradientProgressBar(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Начало",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = challenge.startDate.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Окончание",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = challenge.endDate.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (daysRemaining > 0 && challenge.status == ChallengeStatus.ACTIVE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Осталось $daysRemaining дней",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Habit Info
        if (habit != null) {
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    isDarkTheme = isDarkTheme
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TahoeIcons.Clock,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Привычка",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = habit.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Rules
        item {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                isDarkTheme = isDarkTheme
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Правила",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    RuleItem(
                        icon = TahoeIcons.Flag,
                        label = "Цель",
                        value = challenge.getRulesText()
                    )
                    
                    if (challenge.rules.allowMissedDays > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleItem(
                            icon = TahoeIcons.CalendarDays,
                            label = "Допустимые пропуски",
                            value = "${challenge.rules.allowMissedDays} дней"
                        )
                    }
                    
                    if (challenge.rules.requireConsecutive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleItem(
                            icon = TahoeIcons.ChartBar,
                            label = "Требование",
                            value = "Последовательное выполнение"
                        )
                    }
                    
                    if (challenge.rules.allowBackfillCompletions) {
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleItem(
                            icon = TahoeIcons.Clock,
                            label = "Дополнительно",
                            value = stringResource(R.string.allow_backfill_completions_info)
                        )
                    }
                }
            }
        }

        // Participants Leaderboard
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Участники (${participants.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Кнопка добавления участников (только для создателя, для активного или ожидающего челленджа)
                if (isCreator && (challenge.status == ChallengeStatus.ACTIVE || challenge.status == ChallengeStatus.PENDING)) {
                    OutlinedButton(
                        onClick = onAddParticipants,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = TahoeIcons.Group,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Добавить",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        if (participants.isEmpty()) {
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    isDarkTheme = isDarkTheme
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет участников",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(participants.sortedBy { participantStats[it.id]?.position ?: Int.MAX_VALUE }) { user ->
                val stats = participantStats[user.id]
                val hasAccepted = challenge.acceptedParticipants.contains(user.id) || user.id == challenge.createdBy
                val canRemove = isCreator && 
                    user.id != challenge.createdBy && 
                    (challenge.status == ChallengeStatus.ACTIVE || challenge.status == ChallengeStatus.PENDING)
                val isWinner = !challenge.winnerId.isNullOrBlank() && challenge.winnerId == user.id
                ParticipantCard(
                    user = user,
                    stats = stats,
                    isCurrentUser = user.id == currentUserId,
                    isChallengeCreator = user.id == challenge.createdBy,
                    isWinner = isWinner,
                    hasAccepted = hasAccepted,
                    canResendInvitation = isCreator && !hasAccepted,
                    canRemoveParticipant = canRemove,
                    onResendInvitation = { onResendInvitation(user.id) },
                    onRemoveParticipant = { onRemoveParticipant(user) },
                    isDarkTheme = isDarkTheme,
                    isMeasurableHabit = habit?.goalType == HabitGoalType.MEASURABLE,
                    habitUnit = habit?.unit ?: ""
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun RuleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ParticipantCard(
    user: User,
    stats: ChallengeParticipantStats?,
    isCurrentUser: Boolean,
    isChallengeCreator: Boolean,
    isWinner: Boolean,
    hasAccepted: Boolean,
    canResendInvitation: Boolean,
    canRemoveParticipant: Boolean,
    onResendInvitation: () -> Unit,
    onRemoveParticipant: () -> Unit,
    isDarkTheme: Boolean,
    isMeasurableHabit: Boolean = false,
    habitUnit: String = ""
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Position
                if (stats != null && hasAccepted) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val positionColor = when (stats.position) {
                            1 -> MaterialTheme.colorScheme.primary
                            2 -> MaterialTheme.colorScheme.secondary
                            3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "#${stats.position}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = positionColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                UserAvatar(
                    avatarThumbnailUrl = user.avatarThumbnailUrl,
                    avatarUrl = user.avatarUrl,
                    name = user.name,
                    size = 40.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Name and badges
                Column(modifier = Modifier.weight(1f)) {
                    if (isWinner) {
                        Text(
                            text = "ПОБЕДИТЕЛЬ!",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isCurrentUser) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(Вы)",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isChallengeCreator) {
                                Icon(
                                    imageVector = TahoeIcons.Star,
                                    contentDescription = "Создатель",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isWinner) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🥇",
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                    
                    // Status row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasAccepted) {
                            if (stats != null) {
                                val statsText = if (isMeasurableHabit) {
                                    // Для измеряемых привычек показываем суммарное значение
                                    val unitText = if (habitUnit.isNotBlank()) " $habitUnit" else ""
                                    "${stats.totalValue}$unitText • ${stats.completedDays}/${stats.totalDays} дней"
                                } else {
                                    // Для простых привычек показываем дни и процент
                                    "${stats.completedDays}/${stats.totalDays} дней • ${(stats.completionRate * 100).toInt()}%"
                                }
                                Text(
                                    text = statsText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Icon(
                                imageVector = TahoeIcons.Hourglass,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ожидает подтверждения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Progress or streak indicator
                if (hasAccepted && stats != null && stats.currentStreak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TahoeIcons.Fire,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${stats.currentStreak}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // Remove participant button (only for creator, not for self or challenge creator)
                if (canRemoveParticipant) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onRemoveParticipant,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = TahoeIcons.Trash,
                            contentDescription = "Удалить участника",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Resend invitation button
            if (canResendInvitation) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onResendInvitation,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = TahoeIcons.PaperAirplane,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Отправить приглашение повторно",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AddParticipantDialog(
    availableFriends: List<User>,
    selectedParticipants: List<User>,
    onParticipantToggle: (User) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_participants)) },
        content = {
            if (availableFriends.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_friends_to_add),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(availableFriends) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onParticipantToggle(friend) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedParticipants.contains(friend),
                                onCheckedChange = { onParticipantToggle(friend) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Avatar
                            UserAvatar(
                                avatarThumbnailUrl = friend.avatarThumbnailUrl,
                                avatarUrl = friend.avatarUrl,
                                name = friend.name,
                                size = 32.dp,
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    enabled = selectedParticipants.isNotEmpty()
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        }
    )
}
