package com.habittracker.presentation.ui.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.DayCompletionStatus
import com.habittracker.domain.model.DayStatistics
import com.habittracker.domain.model.HabitStatisticsData
import com.habittracker.domain.model.HabitStatisticsSummary
import com.habittracker.domain.model.User
import com.habittracker.presentation.ui.components.UserAvatar
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicSwitch
import com.habittracker.presentation.ui.habits.components.HabitEntryDetailSheet
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.viewmodel.HabitStatisticsViewModel
import kotlinx.datetime.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupHabitStatScreen(
    onNavigateBack: () -> Unit,
    viewModel: HabitStatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.groupStatUiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()
    
    var selectedDayDetail by remember { mutableStateOf<Pair<DayStatistics, Boolean>?>(null) }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.statistics?.habit?.title ?: stringResource(R.string.cd_habit_statistics),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.statistics != null) {
                GroupHabitStatisticsContent(
                    statistics = uiState.statistics!!,
                    participants = uiState.participantsInfo,
                    showParticipantsComparison = uiState.showParticipantsComparison,
                    selectedParticipantIds = uiState.selectedParticipantIds,
                    onToggleParticipantsComparison = { viewModel.onToggleParticipantsComparison() },
                    onToggleParticipantSelection = { userId -> viewModel.onToggleParticipantSelection(userId) },
                    onClearParticipantFilter = { viewModel.onClearParticipantFilter() },
                    onParticipantCalendarExpanded = { userId -> viewModel.onParticipantCalendarExpanded(userId) },
                    onDayClick = { dayStat, isReadOnly -> selectedDayDetail = dayStat to isReadOnly },
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
            }
        }
    }

    selectedDayDetail?.let { (dayStat, isReadOnly) ->
        val successMsg = when (uiState.photoUploadMessage) {
            "photo_success" -> stringResource(R.string.photo_proof_upload_success)
            "note_success" -> stringResource(R.string.photo_proof_note_success)
            else -> null
        }

        HabitEntryDetailSheet(
            entry = dayStat.entry,
            date = dayStat.date,
            habitTitle = uiState.statistics?.habit?.title ?: "",
            habitUnit = uiState.statistics?.habit?.unit,
            isUploading = uiState.isUploadingPhoto,
            successMessage = successMsg,
            onDismiss = { selectedDayDetail = null },
            onAddPhoto = { uri -> viewModel.attachPhotoToGroupHabit(uri, dayStat.date) },
            onDeletePhoto = { viewModel.deletePhotoFromGroupHabit(dayStat.date) },
            onSaveComment = { note -> viewModel.updateEntryNote(dayStat.date, note) },
            onSuccessMessageShown = { viewModel.clearGroupPhotoUploadSuccess() },
            isReadOnly = isReadOnly
        )
    }
}

@Composable
private fun GroupHabitStatisticsContent(
    statistics: com.habittracker.domain.model.GroupHabitStatisticsData,
    participants: List<User>,
    showParticipantsComparison: Boolean,
    selectedParticipantIds: Set<String>,
    onToggleParticipantsComparison: () -> Unit,
    onToggleParticipantSelection: (String) -> Unit,
    onClearParticipantFilter: () -> Unit,
    onParticipantCalendarExpanded: (String) -> Unit,
    onDayClick: (DayStatistics, Boolean) -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GroupHabitHeaderCard(
                habit = statistics.habit,
                participantsCount = participants.size + 1, // Include current user
                isDarkTheme = isDarkTheme
            )
        }

        item {
            Text(
                text = stringResource(R.string.habit_stat_my_statistics),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            MySummaryCard(
                summary = statistics.currentUserStatistics.summary,
                isDarkTheme = isDarkTheme
            )
        }

        item {
            CalendarGridCard(
                title = stringResource(R.string.habit_stat_my_calendar),
                dailyStatistics = statistics.currentUserStatistics.dailyStatistics,
                onDayClick = { dayStat -> onDayClick(dayStat, false) },
                isDarkTheme = isDarkTheme
            )
        }

        item {
            ComparisonModeCard(
                showParticipantsComparison = showParticipantsComparison,
                onToggle = onToggleParticipantsComparison,
                isDarkTheme = isDarkTheme
            )
        }

        if (showParticipantsComparison && statistics.participantsStatistics.isNotEmpty()) {
            item {
                ParticipantsFilterCard(
                    participants = participants,
                    selectedIds = selectedParticipantIds,
                    onToggleSelection = onToggleParticipantSelection,
                    onClearFilter = onClearParticipantFilter,
                    isDarkTheme = isDarkTheme
                )
            }

            val filteredParticipants = if (selectedParticipantIds.isEmpty()) {
                statistics.participantsStatistics
            } else {
                statistics.participantsStatistics.filterKeys { it in selectedParticipantIds }
            }

            filteredParticipants.forEach { (userId, participantStats) ->
                val user = participants.find { it.id == userId }
                if (user != null) {
                    item(key = "participant_$userId") {
                        ParticipantStatisticsCard(
                            user = user,
                            statistics = participantStats,
                            isDarkTheme = isDarkTheme,
                            onCalendarExpanded = { onParticipantCalendarExpanded(userId) },
                            onDayClick = { dayStat -> onDayClick(dayStat, true) }
                        )
                    }
                }
            }
        }

        item {
            LegendCard(isDarkTheme = isDarkTheme)
        }
    }
}

@Composable
private fun GroupHabitHeaderCard(
    habit: com.habittracker.domain.model.Habit,
    participantsCount: Int,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        try { Color(habit.color.toColorInt()).copy(alpha = 0.1f) }
                        catch (_: Exception) { MaterialTheme.colorScheme.primaryContainer }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = habit.icon, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.habit_stat_participants_count, participantsCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MySummaryCard(
    summary: HabitStatisticsSummary,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SummaryItem(
                value = summary.currentStreak.toString(),
                label = stringResource(R.string.habit_stat_streak_short),
                color = Color(0xFFFF9800)
            )
            SummaryItem(
                value = summary.bestStreak.toString(),
                label = stringResource(R.string.habit_stat_best_streak),
                color = Color(0xFFFFD700)
            )
            SummaryItem(
                value = "${(summary.completionRate * 100).toInt()}%",
                label = stringResource(R.string.habit_stat_completion_short),
                color = Color(0xFF2196F3)
            )
        }
    }
}

@Composable
private fun SummaryItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarGridCard(
    title: String,
    dailyStatistics: List<DayStatistics>,
    onDayClick: (DayStatistics) -> Unit,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            CalendarGrid(dailyStatistics = dailyStatistics, onDayClick = onDayClick, isDarkTheme = isDarkTheme)
        }
    }
}

@Composable
private fun CalendarGrid(
    dailyStatistics: List<DayStatistics>,
    onDayClick: (DayStatistics) -> Unit,
    isDarkTheme: Boolean
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed, R.string.weekday_thu, R.string.weekday_fri, R.string.weekday_sat, R.string.weekday_sun).forEach { dayRes ->
                Text(
                    text = stringResource(dayRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val weeks = groupByWeeks(dailyStatistics)
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val firstDayOfWeek = week.firstOrNull()?.date?.dayOfWeek
                val emptyDaysBefore = when (firstDayOfWeek) {
                    DayOfWeek.MONDAY -> 0
                    DayOfWeek.TUESDAY -> 1
                    DayOfWeek.WEDNESDAY -> 2
                    DayOfWeek.THURSDAY -> 3
                    DayOfWeek.FRIDAY -> 4
                    DayOfWeek.SATURDAY -> 5
                    DayOfWeek.SUNDAY -> 6
                    null -> 0
                }
                repeat(emptyDaysBefore) { Box(modifier = Modifier.weight(1f)) }
                week.forEach { dayStat ->
                    DayCell(dayStatistics = dayStat, onClick = { onDayClick(dayStat) }, isDarkTheme = isDarkTheme, modifier = Modifier.weight(1f))
                }
                val emptyDaysAfter = 7 - emptyDaysBefore - week.size
                repeat(emptyDaysAfter) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayStatistics: DayStatistics,
    onClick: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (dayStatistics.status) {
        DayCompletionStatus.COMPLETED -> Color(0xFF4CAF50)
        DayCompletionStatus.MISSED -> Color(0xFFF44336)
        DayCompletionStatus.REST_DAY -> Color(0xFF9E9E9E)
        DayCompletionStatus.NOT_ACTIVE -> Color.Transparent
        DayCompletionStatus.FUTURE -> if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
        DayCompletionStatus.PARTIAL -> Color(0xFFFF9800)
    }
    val isClickable = dayStatistics.status !in listOf(DayCompletionStatus.FUTURE, DayCompletionStatus.NOT_ACTIVE)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayStatistics.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (dayStatistics.status == DayCompletionStatus.NOT_ACTIVE) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ComparisonModeCard(showParticipantsComparison: Boolean, onToggle: () -> Unit, isDarkTheme: Boolean) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.habit_stat_compare_participants),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.habit_stat_compare_participants_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GlassmorphicSwitch(
                checked = showParticipantsComparison,
                onCheckedChange = { onToggle() },
                trackWidth = 46.dp,
                trackHeight = 26.dp,
                thumbSize = 22.dp
            )
        }
    }
}

@Composable
private fun ParticipantsFilterCard(participants: List<User>, selectedIds: Set<String>, onToggleSelection: (String) -> Unit, onClearFilter: () -> Unit, isDarkTheme: Boolean) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.habit_stat_filter_participants),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClearFilter) {
                        Text(
                            text = stringResource(R.string.habit_stat_show_all),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(participants) { user ->
                    FilterChip(
                        selected = user.id in selectedIds,
                        onClick = { onToggleSelection(user.id) },
                        label = { Text(user.name.ifBlank { "User" }) },
                        leadingIcon = { UserAvatar(avatarThumbnailUrl = user.avatarThumbnailUrl, avatarUrl = user.avatarUrl, name = user.name, size = 24.dp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantStatisticsCard(user: User, statistics: HabitStatisticsData, isDarkTheme: Boolean, onCalendarExpanded: () -> Unit = {}, onDayClick: (DayStatistics) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { 
            isExpanded = !isExpanded
            if (isExpanded) onCalendarExpanded()
        },
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UserAvatar(avatarThumbnailUrl = user.avatarThumbnailUrl, avatarUrl = user.avatarUrl, name = user.name, size = 40.dp)
                Column {
                    Text(
                        text = user.name.ifBlank { "User" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(statistics.summary.completionRate * 100).toInt()}% ${
                            stringResource(
                                R.string.habit_stat_completion_short
                            )
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(imageVector = TahoeIcons.ChevronDown, contentDescription = null, modifier = Modifier.rotate(if (isExpanded) 180f else 0f))
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                CalendarGrid(dailyStatistics = statistics.dailyStatistics, onDayClick = onDayClick, isDarkTheme = isDarkTheme)
            }
        }
    }
}

@Composable
private fun LegendCard(isDarkTheme: Boolean) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth(), isDarkTheme = isDarkTheme, cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.habit_stat_legend),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LegendItem(color = Color(0xFF4CAF50), label = stringResource(R.string.habit_stat_legend_completed))
                LegendItem(color = Color(0xFFF44336), label = stringResource(R.string.habit_stat_legend_missed))
                LegendItem(color = Color(0xFF9E9E9E), label = stringResource(R.string.habit_stat_legend_rest))
                LegendItem(color = Color(0xFFFF9800), label = stringResource(R.string.habit_stat_legend_partial))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun groupByWeeks(dailyStatistics: List<DayStatistics>): List<List<DayStatistics>> {
    if (dailyStatistics.isEmpty()) return emptyList()
    val weeks = mutableListOf<MutableList<DayStatistics>>()
    var currentWeek = mutableListOf<DayStatistics>()
    dailyStatistics.forEach { dayStat ->
        if (dayStat.date.dayOfWeek == DayOfWeek.MONDAY && currentWeek.isNotEmpty()) {
            weeks.add(currentWeek)
            currentWeek = mutableListOf()
        }
        currentWeek.add(dayStat)
    }
    if (currentWeek.isNotEmpty()) weeks.add(currentWeek)
    return weeks
}