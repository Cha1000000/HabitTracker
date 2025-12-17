package com.habittracker.presentation.ui.challenges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.habittracker.R
import com.habittracker.domain.model.Challenge
import com.habittracker.domain.model.ChallengeStatus
import com.habittracker.domain.model.ChallengeType
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GradientProgressBar
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeCard(
    challenge: Challenge,
    currentUserId: String?,
    onChallengeClick: () -> Unit,
    onJoinClick: () -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val daysRemaining = (challenge.endDate.toEpochDays() - today.toEpochDays()).toInt()
    val totalDays = (challenge.endDate.toEpochDays() - challenge.startDate.toEpochDays()).toInt() + 1
    val progress = if (totalDays > 0) {
        maxOf(0f, (totalDays - daysRemaining).toFloat() / totalDays)
    } else 0f
    val isDarkTheme = isSystemInDarkTheme()

    GlassmorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onChallengeClick() },
        isDarkTheme = isDarkTheme
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (challenge.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = challenge.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                ChallengeStatusBadge(
                    status = challenge.status,
                    type = challenge.rules.type
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val barColors = when {
                    daysRemaining <= 1 -> listOf(
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.errorContainer
                    )
                    daysRemaining <= 3 -> listOf(
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                    else -> listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                }
                GradientProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    colors = barColors,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Participants
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${challenge.participants.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Days remaining
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (daysRemaining <= 3) Icons.Default.Timer else Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when {
                            daysRemaining <= 1 -> MaterialTheme.colorScheme.error
                            daysRemaining <= 3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            daysRemaining <= 0 -> stringResource(R.string.challenge_ended)
                            daysRemaining == 1 -> stringResource(R.string.one_day_left)
                            else -> stringResource(R.string.days_left, daysRemaining)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            daysRemaining <= 1 -> MaterialTheme.colorScheme.error
                            daysRemaining <= 3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Challenge type
                ChallengeTypeChip(type = challenge.rules.type)
            }

            // Action buttons based on user participation
            val isParticipant = currentUserId != null && 
                (challenge.participants.contains(currentUserId) || challenge.createdBy == currentUserId)
            val isCreator = currentUserId == challenge.createdBy
            val canJoin = challenge.status == ChallengeStatus.PENDING && !isParticipant
            val canLeave = (challenge.status == ChallengeStatus.PENDING || challenge.status == ChallengeStatus.ACTIVE) && 
                isParticipant && !isCreator
            
            if (canJoin || canLeave) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (canJoin) {
                        Button(onClick = onJoinClick) {
                            Text(stringResource(R.string.join_challenge))
                        }
                    }
                    if (canLeave) {
                        TextButton(onClick = onLeaveClick) {
                            Text(stringResource(R.string.leave_challenge))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChallengeStatusBadge(
    status: ChallengeStatus,
    type: ChallengeType,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, text, icon) = when (status) {
        ChallengeStatus.PENDING -> {
            Tuple4(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                stringResource(R.string.challenge_pending),
                Icons.Default.Schedule
            )
        }
        ChallengeStatus.ACTIVE -> {
            Tuple4(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                stringResource(R.string.challenge_active),
                Icons.Default.Timer
            )
        }
        ChallengeStatus.COMPLETED -> {
            Tuple4(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                stringResource(R.string.challenge_completed),
                Icons.Default.EmojiEvents
            )
        }
        ChallengeStatus.CANCELLED -> {
            Tuple4(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                stringResource(R.string.challenge_cancelled),
                Icons.Default.Timer
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ChallengeTypeChip(
    type: ChallengeType,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (type) {
        ChallengeType.COMPLETION_RATE -> {
            stringResource(R.string.completion_rate) to MaterialTheme.colorScheme.primary
        }
        ChallengeType.STREAK -> {
            stringResource(R.string.streak_challenge) to MaterialTheme.colorScheme.secondary
        }
        ChallengeType.TOTAL_DAYS -> {
            stringResource(R.string.total_days) to MaterialTheme.colorScheme.tertiary
        }
        ChallengeType.FIRST_TO_TARGET -> {
            stringResource(R.string.first_to_target) to MaterialTheme.colorScheme.error
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// Helper data class for multiple return values
private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
