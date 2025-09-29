package com.habittracker.presentation.ui.stats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.gamification.ChallengeStats
import com.habittracker.domain.model.gamification.HabitStats
import com.habittracker.domain.model.gamification.SocialStats
import com.habittracker.domain.model.gamification.StreakStats
import com.habittracker.domain.model.gamification.TimeStats
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GradientProgressBar
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            // Handle error display
            viewModel.onErrorShown()
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
                    title = { Text(stringResource(R.string.stats_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                Icons.Default.Refresh,
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
                
                uiState.userStats != null -> {
                    val userStats = uiState.userStats!!
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    item {
                        UserLevelCard(userStats.levelInfo)
                    }
                    
                    item {
                        HabitStatsCard(userStats.habitStats)
                    }
                    
                    item {
                        StreakStatsCard(userStats.streakStats)
                    }
                    
                    item {
                        SocialStatsCard(userStats.socialStats)
                    }
                    
                    item {
                        ChallengeStatsCard(userStats.challengeStats)
                    }
                    
                    item {
                        TimeStatsCard(userStats.timeStats)
                    }
                }
            }
            
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = uiState.error ?: stringResource(R.string.stats_error_load),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun UserLevelCard(levelInfo: com.habittracker.domain.model.UserLevelInfo) {
    val isDarkTheme = isSystemInDarkTheme()
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
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
                    text = stringResource(R.string.level_title, levelInfo.currentLevel),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = levelInfo.levelTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(
                    R.string.level_points_inline,
                    levelInfo.currentPoints,
                    levelInfo.pointsForNextLevel
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            GradientProgressBar(
                progress = levelInfo.progress,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!levelInfo.isMaxLevel) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.level_next_points, levelInfo.pointsToNextLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HabitStatsCard(habitStats: HabitStats) {
    StatsCard(
        title = stringResource(R.string.stats_habits_title),
        icon = Icons.Default.CheckCircle,
        stats = listOf(
            stringResource(R.string.stats_habits_total) to habitStats.totalHabits.toString(),
            stringResource(R.string.stats_habits_active) to habitStats.activeHabits.toString(),
            stringResource(R.string.stats_habits_completed_today) to habitStats.completedToday.toString(),
            stringResource(R.string.stats_habits_total_completions) to habitStats.totalCompletions.toString(),
            stringResource(R.string.stats_habits_week) to habitStats.completionsThisWeek.toString(),
            stringResource(R.string.stats_habits_month) to habitStats.completionsThisMonth.toString(),
            stringResource(R.string.stats_habits_avg_completion) to "${habitStats.averageCompletionRate.toInt()}%",
            stringResource(R.string.stats_habits_perfect_days) to habitStats.perfectDays.toString()
        ).plus(
            habitStats.mostCompletedHabit?.let { 
                listOf(stringResource(R.string.stats_habits_best) to it)
            } ?: emptyList()
        )
    )
}

@Composable
private fun StreakStatsCard(streakStats: StreakStats) {
    StatsCard(
        title = stringResource(R.string.stats_streaks_title),
        icon = Icons.Default.Whatshot,
        stats = listOf(
            stringResource(R.string.stats_streak_current) to streakStats.currentStreak.toString(),
            stringResource(R.string.stats_streak_best) to streakStats.longestStreak.toString(),
            stringResource(R.string.stats_streak_avg) to streakStats.averageStreak.toString(),
            stringResource(R.string.stats_streak_active) to streakStats.activeStreaks.toString()
        )
    )
}

@Composable
private fun SocialStatsCard(socialStats: SocialStats) {
    StatsCard(
        title = stringResource(R.string.stats_social_title),
        icon = Icons.Default.Group,
        stats = listOf(
            stringResource(R.string.stats_social_friends) to socialStats.friendsCount.toString(),
            stringResource(R.string.stats_social_group_habits) to socialStats.groupHabitsCount.toString(),
            stringResource(R.string.stats_social_challenges_participated) to socialStats.challengesParticipated.toString(),
            stringResource(R.string.stats_social_challenges_won) to socialStats.challengesWon.toString()
        )
    )
}

@Composable
private fun ChallengeStatsCard(challengeStats: ChallengeStats) {
    StatsCard(
        title = stringResource(R.string.stats_challenges_title),
        icon = Icons.Default.EmojiEvents,
        stats = listOf(
            stringResource(R.string.stats_challenges_total) to challengeStats.totalChallenges.toString(),
            stringResource(R.string.stats_challenges_active) to challengeStats.activeChallenges.toString(),
            stringResource(R.string.stats_challenges_completed) to challengeStats.completedChallenges.toString(),
            stringResource(R.string.stats_challenges_won) to challengeStats.wonChallenges.toString(),
            stringResource(R.string.stats_challenges_win_rate) to "${challengeStats.winRate.toInt()}%",
            stringResource(R.string.stats_challenges_created) to challengeStats.createdChallenges.toString()
        )
    )
}

@Composable
private fun TimeStatsCard(timeStats: TimeStats) {
    StatsCard(
        title = stringResource(R.string.stats_time_title),
        icon = Icons.Default.Schedule,
        stats = listOf(
            stringResource(R.string.stats_time_morning) to timeStats.morningCompletions.toString(),
            stringResource(R.string.stats_time_afternoon) to timeStats.afternoonCompletions.toString(),
            stringResource(R.string.stats_time_evening) to timeStats.eveningCompletions.toString(),
            stringResource(R.string.stats_time_consistency) to "${(timeStats.consistencyScore * 100).toInt()}%"
        ).plus(
            timeStats.averageCompletionTime?.let {
                listOf(stringResource(R.string.stats_time_average) to it)
            } ?: emptyList()
        ).plus(
            timeStats.mostProductiveHour?.let {
                listOf(stringResource(R.string.stats_time_best_hour) to "${it}:00")
            } ?: emptyList()
        )
    )
}

@Composable
private fun StatsCard(
    title: String,
    icon: ImageVector,
    stats: List<Pair<String, String>>
) {
    val isDarkTheme = isSystemInDarkTheme()
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        cornerRadius = 16.dp,
        glassAlpha = if (isDarkTheme) 0.18f else 0.85f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.15f,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isDarkTheme) Color(0xFF4DC273) else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            stats.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
