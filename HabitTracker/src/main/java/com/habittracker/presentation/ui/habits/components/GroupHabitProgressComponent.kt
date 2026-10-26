package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.presentation.ui.components.UserAvatar
import com.habittracker.presentation.ui.theme.TahoeIcons
import androidx.compose.runtime.Immutable
import kotlin.time.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Данные о прогрессе участника групповой привычки
 */
@Immutable
data class ParticipantProgress(
    val userId: String,
    val userName: String,
    val isCompletedToday: Boolean,
    val currentStreak: Int,
    val completionRate: Float, // процент выполнения за последние 7 дней (0.0 - 1.0)
    val avatarUrl: String? = null // URL аватарки участника
)

/**
 * Компонент для отображения прогресса групповой привычки
 */
@Composable
fun GroupHabitProgressComponent(
    habit: Habit,
    participantsProgress: List<ParticipantProgress>,
    modifier: Modifier = Modifier
) {
    if (!habit.isGroupHabit()) return
    
    val completedToday = participantsProgress.count { it.isCompletedToday }
    val totalParticipants = participantsProgress.size
    val completionPercentage = if (totalParticipants > 0) {
        completedToday.toFloat() / totalParticipants.toFloat()
    } else 0f
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Общий прогресс группы
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.group_progress_today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.group_progress_count_format, completedToday, totalParticipants),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (completionPercentage >= 0.5f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        GradientProgressBar(
            progress = completionPercentage,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Список участников с их статусом
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp) // Добавляем padding для индикатора выполнения
        ) {
            items(participantsProgress) { participant ->
                ParticipantStatusItem(
                    participant = participant,
                    size = 40.dp,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * Компонент для отображения статуса отдельного участника с аватаркой
 */
@Composable
private fun ParticipantStatusItem(
    participant: ParticipantProgress,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val successColor = Color(0xFF4CAF50)
    val borderColor = if (participant.isCompletedToday) successColor else Color.Transparent
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Аватарка или плейсхолдер
        UserAvatar(
            data = participant.avatarUrl,
            name = participant.userName,
            size = size,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(
                    width = if (participant.isCompletedToday) 2.dp else 0.dp,
                    color = borderColor,
                    shape = CircleShape
                ),
        )
        
        // Индикатор выполнения (галочка в углу)
        if (participant.isCompletedToday) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(successColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TahoeIcons.Check,
                    contentDescription = stringResource(R.string.completed_today),
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Расширенная карточка группового прогресса с детальной информацией
 */
@Composable
fun GroupHabitDetailCard(
    habit: Habit,
    participantsProgress: List<ParticipantProgress>,
    modifier: Modifier = Modifier
) {
    if (!habit.isGroupHabit()) return
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.group_participants),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            participantsProgress.forEach { participant ->
                ParticipantDetailRow(
                    participant = participant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Детальная строка с информацией об участнике
 */
@Composable
private fun ParticipantDetailRow(
    participant: ParticipantProgress,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParticipantStatusItem(
                participant = participant,
                size = 24.dp,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = participant.userName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Streak
            Text(
                text = stringResource(R.string.streak_fire_format, participant.currentStreak),
                style = MaterialTheme.typography.bodySmall,
                color = if (participant.currentStreak > 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Completion rate
            Text(
                text = "${(participant.completionRate * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    participant.completionRate >= 0.8f -> Color(0xFF4CAF50)
                    participant.completionRate >= 0.5f -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                },
                textAlign = TextAlign.End
            )
        }
    }
}

// Preview компоненты
@Preview(showBackground = true)
@Composable
private fun GroupHabitProgressComponentPreview() {
    val sampleHabit = Habit(
        id = "1",
        title = "Утренняя зарядка",
        description = "15 минут зарядки каждое утро",
        icon = "🏃",
        type = HabitType.GROUP,
        frequency = HabitFrequency.DAILY,
        reminderTime = LocalTime(8, 0),
        reminderEnabled = true,
        targetValue = 1,
        unit = "раз",
        createdBy = "user1",
        participants = listOf("user1", "user2", "user3"),
        maxParticipants = 10,
        isActive = true,
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
    
    val sampleProgress = listOf(
        ParticipantProgress("user1", "Алексей", true, 5, 0.85f),
        ParticipantProgress("user2", "Мария", false, 2, 0.60f),
        ParticipantProgress("user3", "Иван", true, 7, 0.95f)
    )
    
    Column(modifier = Modifier.padding(16.dp)) {
        GroupHabitProgressComponent(
            habit = sampleHabit,
            participantsProgress = sampleProgress
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        GroupHabitDetailCard(
            habit = sampleHabit,
            participantsProgress = sampleProgress
        )
    }
}
