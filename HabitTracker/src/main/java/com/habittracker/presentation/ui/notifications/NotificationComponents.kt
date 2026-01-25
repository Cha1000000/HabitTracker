package com.habittracker.presentation.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habittracker.R
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.getCreatedTimeText
import com.habittracker.domain.model.getStatusText
import com.habittracker.domain.usecase.notification.GroupHabitNotification
import com.habittracker.domain.usecase.notification.NotificationType
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicOutlinedButton
import com.habittracker.presentation.ui.theme.TahoeIcons
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Компонент для отображения списка уведомлений
 */
@Composable
fun NotificationsList(
    notifications: List<GroupHabitNotification>,
    onNotificationClick: (GroupHabitNotification) -> Unit,
    onMarkAsRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (notifications.isEmpty()) {
            item {
                EmptyNotificationsState()
            }
        } else {
            items(
                items = notifications,
                key = { it.id }
            ) { notification ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    NotificationItem(
                        notification = notification,
                        onClick = { onNotificationClick(notification) },
                        onMarkAsRead = { onMarkAsRead(notification.id) }
                    )
                }
            }
        }
    }
}

/**
 * Секция входящих приглашений к привычкам
 */
@Composable
fun InvitationsSection(
    invitations: List<HabitInvitation>,
    isLoading: Boolean,
    processingIds: Set<String>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Заголовок секции
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.invitations_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        if (invitations.isEmpty() && !isLoading) {
            Text(
                text = stringResource(R.string.no_invitations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            invitations.forEach { invitation ->
                val isProcessing = processingIds.contains(invitation.id)
                InvitationItem(
                    invitation = invitation,
                    isProcessing = isProcessing,
                    onAccept = { onAccept(invitation.id) },
                    onDecline = { onDecline(invitation.id) }
                )
            }
        }
    }
}

/**
 * Карточка приглашения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationItem(
    invitation: HabitInvitation,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassmorphicCard(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TahoeIcons.Envelope,
                        contentDescription = stringResource(R.string.cd_invitation_icon),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invitation.habitTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.invitation_from_format, invitation.fromUserName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = invitation.getCreatedTimeText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(invitation.getStatusText()) }
                )
            }

            invitation.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassmorphicOutlinedButton(
                    onClick = onDecline,
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.decline_invitation))
                }
                GlassmorphicButton(
                    onClick = onAccept,
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.accept_invitation))
                }
            }
        }
    }
}

/**
 * Компонент для отображения отдельного уведомления
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationItem(
    notification: GroupHabitNotification,
    onClick: () -> Unit,
    onMarkAsRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassmorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = if (notification.isRead) 2.dp else 4.dp,
        dimmed = notification.isRead
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Иконка типа уведомления
            NotificationTypeIcon(
                type = notification.type,
                isRead = notification.isRead
            )
            
            // Содержимое уведомления
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Заголовок
                Text(
                    text = notification.habitTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Сообщение
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Время
                Text(
                    text = formatNotificationTime(notification.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Индикатор непрочитанного уведомления
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.Top)
                )
            }
            
            // Кнопка "отметить как прочитанное"
            if (!notification.isRead) {
                IconButton(
                    onClick = onMarkAsRead,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = TahoeIcons.Check,
                        contentDescription = stringResource(R.string.mark_as_read),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Иконка для типа уведомления
 */
@Composable
fun NotificationTypeIcon(
    type: NotificationType,
    isRead: Boolean,
    modifier: Modifier = Modifier
) {
    val iconData = when (type) {
        NotificationType.PARTICIPANT_COMPLETED -> TahoeIcons.CheckCircle to Color(0xFF4CAF50)
        NotificationType.PARTICIPANT_STREAK -> TahoeIcons.Fire to Color(0xFFFF9800)
        NotificationType.GROUP_ACHIEVEMENT -> TahoeIcons.Trophy to Color(0xFFFFD700)
        NotificationType.NEW_INVITATION -> TahoeIcons.Envelope to Color(0xFF2196F3)
        NotificationType.INVITATION_ACCEPTED -> TahoeIcons.UserPlus to Color(0xFF4CAF50)
        NotificationType.PARTICIPANT_JOINED -> TahoeIcons.Group to Color(0xFF9C27B0)
        NotificationType.FRIEND_JOINED -> TahoeIcons.UserPlus to Color(0xFF9C27B0)
        NotificationType.HABIT_REMINDER -> TahoeIcons.Bell to Color(0xFF607D8B)
        NotificationType.ACHIEVEMENT_UNLOCKED -> TahoeIcons.Trophy to Color(0xFFFFD700)
    }
    
    val icon = iconData.first
    val color = iconData.second
    
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                color = color.copy(alpha = if (isRead) 0.3f else 0.1f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isRead) color.copy(alpha = 0.6f) else color,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Состояние пустого списка уведомлений
 */
@Composable
fun EmptyNotificationsState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = TahoeIcons.BellSlash,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        
        Text(
            text = stringResource(R.string.no_notifications_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = stringResource(R.string.no_notifications_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Индикатор количества непрочитанных уведомлений
 */
@Composable
fun NotificationBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        Badge(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.error
        ) {
            Text(
                text = if (count > 9) stringResource(R.string.nine_plus) else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError
            )
        }
    }
}

/**
 * Форматирует время уведомления для отображения (локализовано)
 */
@Composable
private fun formatNotificationTime(timestamp: Instant): String {
    val now = Clock.System.now()
    val notificationTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    val diffMinutes = (now.epochSeconds - timestamp.epochSeconds) / 60

    return when {
        diffMinutes < 1 -> stringResource(R.string.time_just_now)
        diffMinutes < 60 -> stringResource(R.string.time_minutes_ago, diffMinutes)
        diffMinutes < 1440 -> stringResource(R.string.time_hours_ago, diffMinutes / 60)
        diffMinutes < 10080 -> stringResource(R.string.time_days_ago, diffMinutes / 1440)
        else -> stringResource(
            R.string.date_dd_mm_yyyy,
            notificationTime.dayOfMonth,
            notificationTime.monthNumber,
            notificationTime.year
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationItemPreview() {
    MaterialTheme {
        NotificationItem(
            notification = GroupHabitNotification(
                id = "1",
                type = NotificationType.PARTICIPANT_COMPLETED,
                habitId = "habit1",
                habitTitle = "Утренняя зарядка",
                participantName = "Анна",
                message = "Анна выполнила привычку \"Утренняя зарядка\"",
                timestamp = Clock.System.now(),
                isRead = false
            ),
            onClick = {},
            onMarkAsRead = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyNotificationsPreview() {
    MaterialTheme {
        EmptyNotificationsState()
    }
}

/**
 * Компонент для отображения сохраненного уведомления с swipe-to-delete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoredNotificationItem(
    notification: com.habittracker.domain.model.GroupHabitNotificationModel,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMarkAsRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart && notification.isRead) {
                onDelete()
            }
            false
        }
    )

    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            if (notification.isRead) {
                val shape = MaterialTheme.shapes.medium
                val offsetPx = try { dismissState.requireOffset() } catch (_: IllegalStateException) { 0f }
                val targetAlpha = if (kotlin.math.abs(offsetPx) > 0f) {
                    // Наращиваем непрозрачность с прогрессом свайпа
                    (kotlin.math.abs(offsetPx) / 600f).coerceIn(0f, 1f)
                } else 0f
                val bgAlpha = animateFloatAsState(
                    targetValue = targetAlpha, 
                    label = "swipeBgAlpha"
                ).value

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = bgAlpha)),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = TahoeIcons.Trash,
                        contentDescription = "Удалить",
                        tint = Color.White.copy(alpha = bgAlpha),
                        modifier = Modifier.padding(end = 24.dp)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = notification.isRead,
        modifier = modifier
    ) {
        GlassmorphicCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            elevation = if (notification.isRead) 1.dp else 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка типа уведомления
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (notification.isRead) 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else 
                                MaterialTheme.colorScheme.primaryContainer
                        )
                        .clickable { onMarkAsRead() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TahoeIcons.CheckCircle,
                        contentDescription = null,
                        tint = if (notification.isRead) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else 
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Контент уведомления
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Для уведомлений о достижениях используем локализованные тексты
                    val displayTitle = if (notification.type == "ACHIEVEMENT_UNLOCKED" && notification.titleResId != null) {
                        stringResource(notification.titleResId)
                    } else {
                        notification.habitTitle
                    }
                    
                    val displayMessage = if (notification.type == "ACHIEVEMENT_UNLOCKED" && notification.descriptionResId != null) {
                        stringResource(R.string.achievement_unlocked_message_format, stringResource(notification.descriptionResId))
                    } else {
                        notification.message
                    }
                    
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                        color = if (notification.isRead) 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else 
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = displayMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notification.isRead) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formatTimestamp(notification.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Индикатор непрочитанного
                if (!notification.isRead) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp) // Немного отступа для тач-таргета
                            .size(24.dp) // Увеличиваем область клика
                            .clip(CircleShape)
                            .clickable { onMarkAsRead() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Форматирует timestamp в читаемый формат
 */
@Composable
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> stringResource(R.string.time_just_now)
        diff < 3600_000 -> stringResource(R.string.time_minutes_ago, diff / 60_000)
        diff < 86400_000 -> stringResource(R.string.time_hours_ago, diff / 3600_000)
        else -> stringResource(R.string.time_days_ago, diff / 86400_000)
    }
}
