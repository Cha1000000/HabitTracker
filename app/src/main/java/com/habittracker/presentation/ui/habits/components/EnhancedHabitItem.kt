package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.presentation.viewmodel.HabitWithCompletion
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime

/**
 * Улучшенный компонент для отображения привычки с поддержкой групповых привычек
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedHabitItem(
    habitWithCompletion: HabitWithCompletion,
    onHabitClick: (HabitWithCompletion) -> Unit,
    onToggleCompletion: () -> Unit,
    onEdit: (HabitWithCompletion) -> Unit = {},
    onDelete: (HabitWithCompletion) -> Unit = {},
    modifier: Modifier = Modifier,
    showGroupProgress: Boolean = true
) {
    val habit = habitWithCompletion.habit
    var showActionsDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onHabitClick(habitWithCompletion) },
                onLongClick = { showActionsDialog = true }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (habitWithCompletion.isCompletedToday) {
                Color(0xFF4CAF50).copy(alpha = 0.1f) // Светло-зеленый фон для выполненных
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Основная информация о привычке
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка привычки
                Text(
                    text = habit.icon,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(8.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Информация о привычке
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = habit.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (habitWithCompletion.isCompletedToday) {
                                Color(0xFF4CAF50)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        // Иконка типа привычки
                        if (habit.isGroupHabit()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = stringResource(R.string.group_habit),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.personal_habit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    if (habit.description.isNotEmpty()) {
                        Text(
                            text = habit.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (habitWithCompletion.isCompletedToday) {
                                Color(0xFF4CAF50).copy(alpha = 0.7f)
                            } else {
                                Color.Gray
                            }
                        )
                    }
                    
                    // Показываем частоту и дополнительную информацию
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (habit.frequency) {
                                HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                                HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                                HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                                HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                                HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Для групповых привычек показываем количество участников
                        if (habit.isGroupHabit()) {
                            Text(
                                text = stringResource(R.string.participants_count, habit.participants.size, habit.maxParticipants),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Показываем цель
                        if (habit.targetValue > 1 || habit.unit != "раз") {
                            Text(
                                text = "${habit.targetValue} ${habit.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Кнопка отметки выполнения
                IconButton(
                    onClick = onToggleCompletion,
                    enabled = !habitWithCompletion.isLoading
                ) {
                    if (habitWithCompletion.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (habitWithCompletion.isCompletedToday) {
                                stringResource(R.string.habits_unmark_complete)
                            } else {
                                stringResource(R.string.habits_mark_complete)
                            },
                            tint = if (habitWithCompletion.isCompletedToday) {
                                Color(0xFF4CAF50)
                            } else {
                                Color.Gray
                            }
                        )
                    }
                }
            }
            
            // Групповой прогресс (если это групповая привычка и есть данные)
            if (habit.isGroupHabit() && showGroupProgress && habitWithCompletion.participantsProgress.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                GroupHabitProgressComponent(
                    habit = habit,
                    participantsProgress = habitWithCompletion.participantsProgress
                )
            }
        }
    }

    if (showActionsDialog) {
        AlertDialog(
            onDismissRequest = { showActionsDialog = false },
            title = { Text(text = stringResource(id = R.string.cd_more)) },
            text = { Text(text = habit.title) },
            confirmButton = {
                Text(
                    text = stringResource(id = R.string.edit),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(16.dp)
                        .clickable {
                            showActionsDialog = false
                            onEdit(habitWithCompletion)
                        }
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(id = R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable {
                                showActionsDialog = false
                                onDelete(habitWithCompletion)
                            }
                    )
                }
            }
        )
    }
}

// Preview компоненты
@Preview(showBackground = true)
@Composable
private fun EnhancedHabitItemPreview() {
    val personalHabit = Habit(
        id = "1",
        title = "Чтение книг",
        description = "30 минут чтения каждый день",
        icon = "📚",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = LocalTime(20, 0),
        reminderEnabled = true,
        targetValue = 30,
        unit = "минут",
        createdBy = "user1",
        participants = listOf("user1"),
        isActive = true,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds()
    )
    
    val groupHabit = Habit(
        id = "2",
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
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds()
    )
    
    val sampleProgress = listOf(
        ParticipantProgress("user1", "Алексей", true, 5, 0.85f),
        ParticipantProgress("user2", "Мария", false, 2, 0.60f),
        ParticipantProgress("user3", "Иван", true, 7, 0.95f)
    )
    
    Column(modifier = Modifier.padding(16.dp)) {
        // Личная привычка
        EnhancedHabitItem(
            habitWithCompletion = HabitWithCompletion(
                habit = personalHabit,
                isCompletedToday = false,
                isLoading = false
            ),
            onHabitClick = {},
            onToggleCompletion = {}
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Групповая привычка
        EnhancedHabitItem(
            habitWithCompletion = HabitWithCompletion(
                habit = groupHabit,
                isCompletedToday = true,
                isLoading = false,
                participantsProgress = sampleProgress
            ),
            onHabitClick = {},
            onToggleCompletion = {}
        )
    }
}
