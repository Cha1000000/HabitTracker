package com.habittracker.presentation.ui.stats

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.DayCompletionStatus
import com.habittracker.domain.model.DayStatistics
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.domain.model.HabitStatisticsData
import com.habittracker.domain.model.HabitStatisticsSummary
import com.habittracker.domain.model.StatisticsPeriod
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GradientProgressBar
import com.habittracker.presentation.ui.habits.components.HabitEntryDetailSheet
import com.habittracker.presentation.ui.habits.components.TahoeDatePicker
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.viewmodel.HabitStatisticsViewModel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfHabitStatScreen(
    onNavigateBack: () -> Unit,
    viewModel: HabitStatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.selfStatUiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()

    BackHandler {
        if (uiState.selectedDayForDetail != null) {
            viewModel.onDismissDayDetail()
        } else {
            onNavigateBack()
        }
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.habit_statistics_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (uiState.selectedDayForDetail != null) {
                                    viewModel.onDismissDayDetail()
                                } else {
                                    onNavigateBack()
                                }
                            }
                        ) {
                            Icon(
                                TahoeIcons.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadStatistics() }) {
                            Icon(
                                TahoeIcons.ArrowPath,
                                contentDescription = stringResource(R.string.cd_refresh)
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
                                TahoeIcons.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.error ?: stringResource(R.string.stats_error_load),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                uiState.statistics != null -> {
                    StatisticsContent(
                        statistics = uiState.statistics!!,
                        selectedPeriod = uiState.selectedPeriod,
                        onPeriodChanged = viewModel::onSelfPeriodChanged,
                        onCustomDateRangeChanged = viewModel::onSelfCustomDateRangeChanged,
                        onDayClick = viewModel::onDaySelected,
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
        
        // HabitEntryDetailSheet для просмотра деталей дня и добавления фото
        uiState.selectedDayForDetail?.let { selectedDate ->
            val entry = viewModel.getEntryForDate(selectedDate)
            HabitEntryDetailSheet(
                entry = entry,
                date = selectedDate,
                habitTitle = uiState.statistics?.habit?.title ?: "",
                habitUnit = uiState.statistics?.habit?.unit,
                isUploading = uiState.isUploadingPhoto,
                onDismiss = viewModel::onDismissDayDetail,
                onAddPhoto = { uri -> viewModel.attachPhotoToEntry(uri, selectedDate) },
                onDeletePhoto = { viewModel.deletePhotoFromEntry(selectedDate) }
            )
        }
    }
}

@Composable
private fun StatisticsContent(
    statistics: HabitStatisticsData,
    selectedPeriod: StatisticsPeriod,
    onPeriodChanged: (StatisticsPeriod) -> Unit,
    onCustomDateRangeChanged: (LocalDate, LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf(statistics.startDate) }
    var customEndDate by remember { mutableStateOf(statistics.endDate) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок с иконкой привычки
        item {
            HabitHeaderCard(
                habit = statistics.habit,
                isDarkTheme = isDarkTheme
            )
        }

        // Переключатель периода
        item {
            PeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodChanged = onPeriodChanged,
                onCustomClick = { showStartDatePicker = true },
                startDate = statistics.startDate,
                endDate = statistics.endDate,
                isDarkTheme = isDarkTheme
            )
        }

        // Блок выбора произвольных дат (показывается только когда выбран CUSTOM период)
        if (showStartDatePicker || showEndDatePicker) {
            item {
                CustomDateRangeSelector(
                    showStartPicker = showStartDatePicker,
                    showEndPicker = showEndDatePicker,
                    customStartDate = customStartDate,
                    customEndDate = customEndDate,
                    onStartDateSelected = { date ->
                        customStartDate = date
                        showStartDatePicker = false
                        showEndDatePicker = true
                    },
                    onEndDateSelected = { date ->
                        customEndDate = date
                        showEndDatePicker = false
                        onCustomDateRangeChanged(customStartDate, customEndDate)
                    },
                    onDismiss = {
                        showStartDatePicker = false
                        showEndDatePicker = false
                    },
                    isDarkTheme = isDarkTheme
                )
            }
        }

        // Сводная статистика
        item {
            SummaryCard(
                summary = statistics.summary,
                isMeasurable = statistics.habit.goalType == HabitGoalType.MEASURABLE,
                unit = statistics.habit.unit,
                isDarkTheme = isDarkTheme
            )
        }

        // Календарная сетка
        item {
            CalendarGridCard(
                dailyStatistics = statistics.dailyStatistics,
                onDayClick = onDayClick,
                isDarkTheme = isDarkTheme
            )
        }

        // Легенда
        item {
            LegendCard(isDarkTheme = isDarkTheme)
        }
    }
}

@Composable
private fun HabitHeaderCard(
    habit: com.habittracker.domain.model.Habit,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
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
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        try {
                            Color(habit.color.toColorInt())
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = habit.icon,
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (habit.description.isNotBlank()) {
                    Text(
                        text = habit.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selectedPeriod: StatisticsPeriod,
    onPeriodChanged: (StatisticsPeriod) -> Unit,
    onCustomClick: () -> Unit,
    startDate: LocalDate,
    endDate: LocalDate,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.habit_stat_period),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPeriod == StatisticsPeriod.WEEK,
                    onClick = { onPeriodChanged(StatisticsPeriod.WEEK) },
                    label = { Text(stringResource(R.string.habit_stat_period_week)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isDarkTheme)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                )

                FilterChip(
                    selected = selectedPeriod == StatisticsPeriod.MONTH,
                    onClick = { onPeriodChanged(StatisticsPeriod.MONTH) },
                    label = { Text(stringResource(R.string.habit_stat_period_month)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isDarkTheme)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                )

                FilterChip(
                    selected = selectedPeriod == StatisticsPeriod.CUSTOM,
                    onClick = onCustomClick,
                    label = { Text(stringResource(R.string.habit_stat_period_custom)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isDarkTheme)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            if (selectedPeriod == StatisticsPeriod.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${formatDate(startDate)} — ${formatDate(endDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: HabitStatisticsSummary,
    isMeasurable: Boolean,
    unit: String,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    TahoeIcons.ChartBar,
                    contentDescription = null,
                    tint = if (isDarkTheme) Color(0xFF4DC273) else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.habit_stat_summary),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Процент выполнения с прогресс-баром
            Text(
                text = stringResource(R.string.habit_stat_completion_rate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                GradientProgressBar(
                    progress = summary.completionRate,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(summary.completionRate * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Статистика в сетке
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = stringResource(R.string.habit_stat_completed),
                    value = summary.completedDays.toString(),
                    color = Color(0xFF4CAF50)
                )
                StatItem(
                    label = stringResource(R.string.habit_stat_missed),
                    value = summary.missedDays.toString(),
                    color = Color(0xFFF44336)
                )
                StatItem(
                    label = stringResource(R.string.habit_stat_rest_days),
                    value = summary.restDays.toString(),
                    color = Color(0xFF9E9E9E)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = stringResource(R.string.habit_stat_current_streak),
                    value = summary.currentStreak.toString(),
                    color = Color(0xFFFF9800)
                )
                StatItem(
                    label = stringResource(R.string.habit_stat_best_streak),
                    value = summary.bestStreak.toString(),
                    color = Color(0xFFFFD700)
                )
                if (isMeasurable) {
                    StatItem(
                        label = stringResource(R.string.habit_stat_total_value),
                        value = "${summary.totalValue} $unit",
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    StatItem(
                        label = stringResource(R.string.habit_stat_active_days),
                        value = summary.activeDays.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CalendarGridCard(
    dailyStatistics: List<DayStatistics>,
    onDayClick: (LocalDate) -> Unit,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    TahoeIcons.Calendar,
                    contentDescription = null,
                    tint = if (isDarkTheme) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.habit_stat_calendar),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Заголовки дней недели
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    R.string.weekday_mon,
                    R.string.weekday_tue,
                    R.string.weekday_wed,
                    R.string.weekday_thu,
                    R.string.weekday_fri,
                    R.string.weekday_sat,
                    R.string.weekday_sun
                ).forEach { dayRes ->
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

            // Группируем по неделям
            val weeks = groupByWeeks(dailyStatistics)

            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Добавляем пустые ячейки в начале недели
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

                    repeat(emptyDaysBefore) {
                        Box(modifier = Modifier.weight(1f))
                    }

                    week.forEach { dayStat ->
                        DayCell(
                            dayStatistics = dayStat,
                            onClick = { onDayClick(dayStat.date) },
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Добавляем пустые ячейки в конце недели
                    val emptyDaysAfter = 7 - emptyDaysBefore - week.size
                    repeat(emptyDaysAfter) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
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

    val borderColor = when (dayStatistics.status) {
        DayCompletionStatus.NOT_ACTIVE -> if (isDarkTheme) Color(0xFF3A3A3A) else Color(0xFFBDBDBD)
        else -> Color.Transparent
    }
    
    // Клик доступен только для прошедших дней (не FUTURE и не NOT_ACTIVE)
    val isClickable = dayStatistics.status !in listOf(
        DayCompletionStatus.FUTURE,
        DayCompletionStatus.NOT_ACTIVE
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .then(
                if (isClickable) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayStatistics.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = when (dayStatistics.status) {
                DayCompletionStatus.COMPLETED,
                DayCompletionStatus.MISSED,
                DayCompletionStatus.PARTIAL -> Color.White
                DayCompletionStatus.REST_DAY -> Color.White
                DayCompletionStatus.NOT_ACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                DayCompletionStatus.FUTURE -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LegendCard(isDarkTheme: Boolean) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.habit_stat_legend),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(
                    color = Color(0xFF4CAF50),
                    label = stringResource(R.string.habit_stat_legend_completed)
                )
                LegendItem(
                    color = Color(0xFFF44336),
                    label = stringResource(R.string.habit_stat_legend_missed)
                )
                LegendItem(
                    color = Color(0xFF9E9E9E),
                    label = stringResource(R.string.habit_stat_legend_rest)
                )
                LegendItem(
                    color = Color(0xFFFF9800),
                    label = stringResource(R.string.habit_stat_legend_partial)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

    if (currentWeek.isNotEmpty()) {
        weeks.add(currentWeek)
    }

    return weeks
}

private fun formatDate(date: LocalDate): String {
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}

@Composable
private fun CustomDateRangeSelector(
    showStartPicker: Boolean,
    showEndPicker: Boolean,
    customStartDate: LocalDate,
    customEndDate: LocalDate,
    onStartDateSelected: (LocalDate) -> Unit,
    onEndDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок с кнопкой закрытия
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.habit_stat_period_custom),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        TahoeIcons.XMark,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Выбор даты начала
            if (showStartPicker) {
                Text(
                    text = stringResource(R.string.start_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TahoeDatePicker(
                    date = customStartDate,
                    onDateSelected = onStartDateSelected,
                    maxDate = customEndDate,
                    label = stringResource(R.string.select_date)
                )
            }

            // Выбор даты окончания
            if (showEndPicker) {
                Text(
                    text = stringResource(R.string.end_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TahoeDatePicker(
                    date = customEndDate,
                    onDateSelected = onEndDateSelected,
                    minDate = customStartDate,
                    label = stringResource(R.string.select_date)
                )
            }
        }
    }
}
