package com.habittracker.presentation.ui.habits.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EnhancedHabitItem(
    habitWithCompletion: HabitWithCompletion,
    onHabitClick: (HabitWithCompletion) -> Unit,
    onToggleCompletion: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (HabitWithCompletion) -> Unit = {},
    onDelete: (HabitWithCompletion) -> Unit = {},
    showGroupProgress: Boolean = true
) {
    val habit = habitWithCompletion.habit
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val isDone = habitWithCompletion.isCompletedToday
    val successColor = Color(0xFF4CAF50)
    val outlineColor = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.medium
    val isDarkTheme = isSystemInDarkTheme()

    Box(modifier = modifier.fillMaxWidth()) {
        val interaction = remember { MutableInteractionSource() }
        val indication = if (isDone) null else LocalIndication.current

        GlassmorphicCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interaction,
                    indication = indication,
                    onClick = { onHabitClick(habitWithCompletion) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                ),
            isDarkTheme = isDarkTheme,
            cornerRadius = 12.dp,
            glassAlpha = if (isDone) {
                if (isDarkTheme) 0.25f else 0.95f
            } else {
                if (isDarkTheme) 0.15f else 0.85f
            },
            borderAlpha = if (isDone) {
                if (isDarkTheme) 0.5f else 0.3f
            } else {
                if (isDarkTheme) 0.3f else 0.15f
            },
            elevation = if (isDone) 4.dp else 2.dp
        ) {
            // Корневой слой без внутреннего отступа, чтобы «⋮» можно было прижать к краю карточки
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .then(
                        if (isDone) Modifier.drawBehind {
                            val stripeWidth = 4.dp.toPx()
                            // Левая акцентная полоска
                            drawRect(color = successColor, size = Size(stripeWidth, size.height))

                            // Рамка по форме темы (через Path)
                            val borderWidth = 1.dp.toPx()
                            when (val outline = shape.createOutline(this.size, layoutDirection, this)) {
                                is Outline.Rounded -> {
                                    val p = Path().apply { addRoundRect(outline.roundRect) }
                                    drawPath(p, color = successColor.copy(alpha = 0.35f), style = Stroke(width = borderWidth))
                                }
                                is Outline.Rectangle -> drawRect(
                                    color = successColor.copy(alpha = 0.35f),
                                    size = this.size,
                                    style = Stroke(width = borderWidth)
                                )
                                is Outline.Generic -> drawPath(
                                    outline.path,
                                    color = successColor.copy(alpha = 0.35f),
                                    style = Stroke(width = borderWidth)
                                )
                            }
                        } else Modifier.drawBehind {
                            val borderWidth = 1.dp.toPx()
                            when (val outline = shape.createOutline(this.size, layoutDirection, this)) {
                                is Outline.Rounded -> {
                                    val p = Path().apply { addRoundRect(outline.roundRect) }
                                    drawPath(p, color = outlineColor, style = Stroke(width = borderWidth))
                                }
                                is Outline.Rectangle -> drawRect(
                                    color = outlineColor,
                                    size = this.size,
                                    style = Stroke(width = borderWidth)
                                )
                                is Outline.Generic -> drawPath(
                                    outline.path,
                                    color = outlineColor,
                                    style = Stroke(width = borderWidth)
                                )
                            }
                        }
                    )
            ) {
                // Основной контент с привычными отступами
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = habit.icon,
                            fontSize = 24.sp,
                            modifier = Modifier.size(48.dp).padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = habit.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (habitWithCompletion.isCompletedToday) successColor else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (habit.isGroupHabit()) {
                                    Icon(imageVector = Icons.Default.Group, contentDescription = stringResource(R.string.group_habit), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = stringResource(R.string.personal_habit), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (habit.description.isNotEmpty()) {
                                Text(
                                    text = habit.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (habitWithCompletion.isCompletedToday) successColor.copy(alpha = 0.7f) else Color.Gray
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (habit.frequency) {
                                        HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                                        HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
                                        HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                                        HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                                        HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                                        HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (habit.isGroupHabit()) {
                                    Text(
                                        text = stringResource(R.string.participants_count, habit.participants.size, habit.maxParticipants),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (habit.targetValue > 1 || habit.unit != "раз") {
                                    Text(
                                        text = "${habit.targetValue} ${habit.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onToggleCompletion, enabled = !habitWithCompletion.isLoading) {
                            if (habitWithCompletion.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = if (habitWithCompletion.isCompletedToday) stringResource(R.string.habits_unmark_complete) else stringResource(R.string.habits_mark_complete),
                                    tint = if (habitWithCompletion.isCompletedToday) successColor else Color.Gray
                                )
                            }
                        }
                    }
                    if (habit.isGroupHabit() && showGroupProgress && habitWithCompletion.participantsProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        GroupHabitProgressComponent(habit = habit, participantsProgress = habitWithCompletion.participantsProgress)
                    }
                }
                // «⋮» в самом правом верхнем углу карточки
                Box(modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-4).dp)) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.cd_more))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        offset = DpOffset(x = (-18).dp, y = (-6).dp)
                    ) {
                        val doneLabel = if (habitWithCompletion.isCompletedToday) {
                            stringResource(id = R.string.habits_unmark_complete)
                        } else {
                            stringResource(id = R.string.menu_done)
                        }
                        val doneIcon = if (habitWithCompletion.isCompletedToday) {
                            Icons.Default.Close
                        } else {
                            Icons.Default.Check
                        }
                        val doneTint by animateColorAsState(
                            targetValue = if (habitWithCompletion.isCompletedToday) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            label = "menuDoneTint"
                        )
                        DropdownMenuItem(
                            text = {
                                Crossfade(targetState = doneLabel, label = "menuDoneText") { labelText ->
                                    Text(text = labelText)
                                }
                            },
                            leadingIcon = {
                                Crossfade(targetState = doneIcon, label = "menuDoneIcon") { icon ->
                                    Icon(imageVector = icon, contentDescription = null, tint = doneTint)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onToggleCompletion()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.edit)) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit(habitWithCompletion)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete(habitWithCompletion)
                            }
                        )
                    }
                }
            }
        }
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
