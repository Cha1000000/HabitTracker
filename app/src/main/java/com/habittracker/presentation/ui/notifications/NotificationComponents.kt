package com.habittracker.presentation.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habittracker.R
import com.habittracker.domain.usecase.notification.GroupHabitNotification
import com.habittracker.domain.usecase.notification.NotificationType
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (notification.isRead) 2.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surface
        )
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
                        imageVector = Icons.Default.Check,
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
        NotificationType.PARTICIPANT_COMPLETED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        NotificationType.PARTICIPANT_STREAK -> Icons.Default.Whatshot to Color(0xFFFF9800)
        NotificationType.GROUP_ACHIEVEMENT -> Icons.Default.EmojiEvents to Color(0xFFFFD700)
        NotificationType.NEW_INVITATION -> Icons.Default.Mail to Color(0xFF2196F3)
        NotificationType.INVITATION_ACCEPTED -> Icons.Default.PersonAdd to Color(0xFF4CAF50)
        NotificationType.PARTICIPANT_JOINED -> Icons.Default.Group to Color(0xFF9C27B0)
        NotificationType.HABIT_REMINDER -> Icons.Default.Notifications to Color(0xFF607D8B)
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
            imageVector = Icons.Default.NotificationsNone,
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
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError
            )
        }
    }
}

/**
 * Форматирует время уведомления для отображения
 */
private fun formatNotificationTime(timestamp: Instant): String {
    val now = Clock.System.now()
    val notificationTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    val currentTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
    
    val diffMinutes = (now.epochSeconds - timestamp.epochSeconds) / 60
    
    return when {
        diffMinutes < 1 -> "Только что"
        diffMinutes < 60 -> "${diffMinutes}м назад"
        diffMinutes < 1440 -> "${diffMinutes / 60}ч назад"
        diffMinutes < 10080 -> "${diffMinutes / 1440}д назад"
        else -> "${notificationTime.dayOfMonth}.${notificationTime.monthNumber}.${notificationTime.year}"
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
