package com.habittracker.presentation.ui.stats

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
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
                            text = uiState.error ?: "Не удалось загрузить статистику",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Попробовать снова")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserLevelCard(levelInfo: com.habittracker.domain.model.UserLevelInfo) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Уровень ${levelInfo.currentLevel}",
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
                text = "${levelInfo.currentPoints} / ${levelInfo.pointsForNextLevel} очков",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { levelInfo.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            
            if (!levelInfo.isMaxLevel) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "До следующего уровня: ${levelInfo.pointsToNextLevel} очков",
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
        title = "Привычки",
        icon = Icons.Default.CheckCircle,
        stats = listOf(
            "Всего привычек" to habitStats.totalHabits.toString(),
            "Активных привычек" to habitStats.activeHabits.toString(),
            "Выполнено сегодня" to habitStats.completedToday.toString(),
            "Всего выполнений" to habitStats.totalCompletions.toString(),
            "За эту неделю" to habitStats.completionsThisWeek.toString(),
            "За этот месяц" to habitStats.completionsThisMonth.toString(),
            "Средний процент выполнения" to "${habitStats.averageCompletionRate.toInt()}%",
            "Идеальных дней" to habitStats.perfectDays.toString()
        ).plus(
            habitStats.mostCompletedHabit?.let { 
                listOf("Лучшая привычка" to it)
            } ?: emptyList()
        )
    )
}

@Composable
private fun StreakStatsCard(streakStats: StreakStats) {
    StatsCard(
        title = "Серии",
        icon = Icons.Default.Whatshot,
        stats = listOf(
            "Текущая серия" to streakStats.currentStreak.toString(),
            "Лучшая серия" to streakStats.longestStreak.toString(),
            "Средняя серия" to streakStats.averageStreak.toString(),
            "Активных серий" to streakStats.activeStreaks.toString()
        )
    )
}

@Composable
private fun SocialStatsCard(socialStats: SocialStats) {
    StatsCard(
        title = "Социальные",
        icon = Icons.Default.Group,
        stats = listOf(
            "Друзей" to socialStats.friendsCount.toString(),
            "Групповых привычек" to socialStats.groupHabitsCount.toString(),
            "Челленджей участвовал" to socialStats.challengesParticipated.toString(),
            "Челленджей выиграл" to socialStats.challengesWon.toString()
        )
    )
}

@Composable
private fun ChallengeStatsCard(challengeStats: ChallengeStats) {
    StatsCard(
        title = "Челленджи",
        icon = Icons.Default.EmojiEvents,
        stats = listOf(
            "Всего челленджей" to challengeStats.totalChallenges.toString(),
            "Активных" to challengeStats.activeChallenges.toString(),
            "Завершено" to challengeStats.completedChallenges.toString(),
            "Выиграно" to challengeStats.wonChallenges.toString(),
            "Процент побед" to "${challengeStats.winRate.toInt()}%",
            "Создано" to challengeStats.createdChallenges.toString()
        )
    )
}

@Composable
private fun TimeStatsCard(timeStats: TimeStats) {
    StatsCard(
        title = "Время выполнения",
        icon = Icons.Default.Schedule,
        stats = listOf(
            "Утром (5-11)" to timeStats.morningCompletions.toString(),
            "Днем (12-17)" to timeStats.afternoonCompletions.toString(),
            "Вечером (18-22)" to timeStats.eveningCompletions.toString(),
            "Оценка постоянства" to "${(timeStats.consistencyScore * 100).toInt()}%"
        ).plus(
            timeStats.averageCompletionTime?.let {
                listOf("Среднее время" to it)
            } ?: emptyList()
        ).plus(
            timeStats.mostProductiveHour?.let {
                listOf("Лучший час" to "${it}:00")
            } ?: emptyList()
        )
    )
}

@Composable
private fun StatsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    stats: List<Pair<String, String>>
) {
    Card {
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
                    tint = MaterialTheme.colorScheme.primary
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
