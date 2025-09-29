package com.habittracker.presentation.ui.habits.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.R

/**
 * Опция расширяемого FAB меню
 */
data class FABOption(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/**
 * Расширяемое FAB меню в стиле Tahoe
 * Показывает опции для создания и поиска привычек
 */
@Composable
fun ExpandableFAB(
    modifier: Modifier = Modifier,
    onCreateHabit: () -> Unit,
    onBrowsePublic: () -> Unit,
    onJoinByCode: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = tween(300),
        label = "fabRotation"
    )
    
    val options = listOf(
        FABOption(
            icon = Icons.Default.QrCode,
            label = stringResource(R.string.fab_enter_code),
            onClick = {
                isExpanded = false
                onJoinByCode()
            }
        ),
        FABOption(
            icon = Icons.Default.AutoStories,
            label = stringResource(R.string.fab_find_habit),
            onClick = {
                isExpanded = false
                onBrowsePublic()
            }
        ),
        FABOption(
            icon = Icons.Default.Add,
            label = stringResource(R.string.fab_create_habit),
            onClick = {
                isExpanded = false
                onCreateHabit()
            }
        )
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Опции меню
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    options.forEach { option ->
                        FABMenuItem(
                            icon = option.icon,
                            label = option.label,
                            onClick = option.onClick
                        )
                    }
                }
            }
            
            // Главная кнопка FAB с эффектом стекла
            GlassmorphicFAB(
                onClick = { isExpanded = !isExpanded },
                isDarkTheme = isSystemInDarkTheme()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isExpanded) stringResource(R.string.fab_close_menu) else stringResource(R.string.fab_open_menu),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

/**
 * Элемент меню FAB
 */
@Composable
private fun FABMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        // Метка с glassmorphic эффектом
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        
        // Мини FAB
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Простая версия FAB с диалогом выбора
 * Альтернативный вариант без анимации раскрытия
 */
@Composable
fun SimpleFABWithDialog(
    modifier: Modifier = Modifier,
    onCreateHabit: () -> Unit,
    onBrowsePublic: () -> Unit,
    onJoinByCode: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = { showDialog = true },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_add))
        }
        
        if (showDialog) {
            JoinOptionsDialog(
                onDismiss = { showDialog = false },
                onCreateNew = {
                    showDialog = false
                    onCreateHabit()
                },
                onBrowsePublic = {
                    showDialog = false
                    onBrowsePublic()
                },
                onJoinByCode = {
                    showDialog = false
                    onJoinByCode()
                }
            )
        }
    }
}

/**
 * Диалог выбора способа добавления привычки
 */
@Composable
private fun JoinOptionsDialog(
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onBrowsePublic: () -> Unit,
    onJoinByCode: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.fab_add_habit_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogOption(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.fab_create_new_title),
                    description = stringResource(R.string.fab_create_new_desc),
                    onClick = onCreateNew
                )
                DialogOption(
                    icon = Icons.Default.Explore,
                    title = stringResource(R.string.fab_browse_catalog_title),
                    description = stringResource(R.string.fab_browse_catalog_desc),
                    onClick = onBrowsePublic
                )
                DialogOption(
                    icon = Icons.Default.QrCode,
                    title = stringResource(R.string.fab_enter_code_title),
                    description = stringResource(R.string.fab_enter_code_desc),
                    onClick = onJoinByCode
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Опция в диалоге выбора
 */
@Composable
private fun DialogOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
