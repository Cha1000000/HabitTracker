package com.habittracker.presentation.ui.achievements

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.Achievement
import com.habittracker.domain.model.AchievementCategory
import com.habittracker.domain.model.AchievementCondition
import com.habittracker.domain.model.AchievementMetric
import com.habittracker.domain.model.AchievementType
import com.habittracker.domain.model.AchievementWithProgress
import com.habittracker.domain.model.UserAchievement
import com.habittracker.domain.model.getDisplayTitle
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GradientProgressBar
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.viewmodel.AchievementsViewModel
import kotlinx.datetime.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val isDarkTheme = isSystemInDarkTheme()
    val nearToUnlock = uiState.achievements
        .asSequence()
        .filter { !it.isUnlocked }
        .filter { it.progress > 0f && it.progress < 1f }
        .sortedByDescending { it.progress }
        .take(3)
        .toList()
    val nearIds = nearToUnlock.map { it.achievement.id }.toSet()
    val remainingAchievements = uiState.achievements.filterNot { nearIds.contains(it.achievement.id) }
    
    val categories = listOf(
        AchievementCategory.HABITS to stringResource(R.string.achievements_habits),
        AchievementCategory.STREAKS to stringResource(R.string.achievements_streaks),
        AchievementCategory.SOCIAL to stringResource(R.string.achievements_social),
        AchievementCategory.CHALLENGES to stringResource(R.string.achievements_challenges),
        AchievementCategory.MILESTONES to stringResource(R.string.achievements_milestones),
        AchievementCategory.SPECIAL to stringResource(R.string.achievements_special)
    )

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
                    Column {
                        Text(
                            text = stringResource(R.string.achievements_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (!uiState.isLoading && uiState.error == null) {
                            Text(
                                text = stringResource(
                                    R.string.achievements_progress,
                                    uiState.unlockedCount,
                                    uiState.totalCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = TahoeIcons.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
                    titleContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground
                )
            )
            }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Вкладки категорий
            ScrollableTabRow(
                modifier = Modifier.fillMaxWidth(),
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                categories.forEachIndexed { index, (category, title) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            selectedTabIndex = index
                            viewModel.loadAchievementsByCategory(category)
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(category),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(title)
                            }
                        }
                    )
                }
            }

            // Контент
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.achievements_loading),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = TahoeIcons.LockClosed,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.achievements_error),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: stringResource(R.string.achievements_unknown_error),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                uiState.achievements.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = TahoeIcons.Trophy,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.achievements_empty),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.achievements_empty_description),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (nearToUnlock.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.achievements_near_to_unlock),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            items(nearToUnlock) { achievement ->
                                AchievementCard(
                                    achievement = achievement,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        items(remainingAchievements) { achievement ->
                            AchievementCard(
                                achievement = achievement,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AchievementCard(
    achievement: AchievementWithProgress,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val isCompleted = achievement.isUnlocked
    val isLocked = !isCompleted && achievement.progress == 0f
    
    // Прозрачность для заблокированных элементов
    val contentAlpha = if (isLocked) 0.5f else 1.0f
    
    GlassmorphicCard(
        modifier = modifier,
        isDarkTheme = isDarkTheme
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Иконка достижения (emoji)
                    Text(
                        text = achievement.achievement.icon,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .alpha(contentAlpha)
                    )
                    
                    Column {
                        Text(
                            text = achievement.achievement.titleResId?.let { stringResource(it) } 
                                ?: achievement.getDisplayTitle(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLocked) 0.5f else 1f)
                            }
                        )
                        Text(
                            text = achievement.achievement.descriptionResId?.let { stringResource(it) }
                                ?: achievement.achievement.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isLocked) 0.5f else 1f)
                        )
                    }
                }
                
                // Статусная иконка
                if (isCompleted) {
                    Icon(
                        imageVector = TahoeIcons.CheckCircle,
                        contentDescription = stringResource(R.string.achievement_unlocked),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(24.dp)
                    )
                } else if (isLocked) {
                    Icon(
                        imageVector = TahoeIcons.LockClosed,
                        contentDescription = stringResource(R.string.achievement_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Прогресс
            if (!isCompleted) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.achievement_progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isLocked) 0.5f else 1f)
                        )
                        Text(
                            text = "${(achievement.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isLocked) 0.5f else 1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    GradientProgressBar(
                        progress = achievement.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isLocked) 0.5f else 1f)
                    )
                }
            }
            
            // Награда (очки)
            if (achievement.achievement.pointsReward > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(contentAlpha)
                ) {
                    Icon(
                        imageVector = TahoeIcons.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isLocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            Color(0xFFFFD700) // Золотой цвет
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.achievement_reward,
                            achievement.achievement.pointsReward
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isLocked) 0.5f else 1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun getCategoryIcon(category: AchievementCategory): ImageVector {
    return when (category) {
        AchievementCategory.HABITS -> TahoeIcons.CheckCircle
        AchievementCategory.STREAKS -> TahoeIcons.Fire
        AchievementCategory.SOCIAL -> TahoeIcons.Group
        AchievementCategory.CHALLENGES -> TahoeIcons.Trophy
        AchievementCategory.MILESTONES -> TahoeIcons.Star
        AchievementCategory.SPECIAL -> TahoeIcons.Clock
    }
}

@Preview(showBackground = true)
@Composable
private fun AchievementsScreenPreview() {
    MaterialTheme {
        AchievementsScreen(
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AchievementCardPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Разблокированное достижение
            AchievementCard(
                achievement = AchievementWithProgress(
                    achievement = Achievement(
                        id = "1",
                        title = "Первые шаги",
                        description = "Создайте свою первую привычку",
                        icon = "🎯",
                        category = AchievementCategory.HABITS,
                        type = AchievementType.SINGLE,
                        condition = AchievementCondition(
                            metric = AchievementMetric.TOTAL_HABITS_CREATED,
                            targetValue = 1
                        ),
                        pointsReward = 10,
                        createdAt = Instant.DISTANT_PAST
                    ),
                    userAchievement = UserAchievement(
                        userId = "user1",
                        achievementId = "1",
                        progress = 1,
                        isUnlocked = true,
                        unlockedAt = Instant.DISTANT_PAST,
                        createdAt = Instant.DISTANT_PAST
                    ),
                    progress = 1.0f,
                    isUnlocked = true
                )
            )
            
            // Заблокированное достижение
            AchievementCard(
                achievement = AchievementWithProgress(
                    achievement = Achievement(
                        id = "2",
                        title = "Мастер привычек",
                        description = "Создайте 10 привычек",
                        icon = "🏆",
                        category = AchievementCategory.HABITS,
                        type = AchievementType.SINGLE,
                        condition = AchievementCondition(
                            metric = AchievementMetric.TOTAL_HABITS_CREATED,
                            targetValue = 10
                        ),
                        pointsReward = 50,
                        createdAt = Instant.DISTANT_PAST
                    ),
                    userAchievement = UserAchievement(
                        userId = "user1",
                        achievementId = "2",
                        progress = 3,
                        isUnlocked = false,
                        unlockedAt = null,
                        createdAt = Instant.DISTANT_PAST
                    ),
                    progress = 0.3f,
                    isUnlocked = false
                )
            )
        }
    }
}
