package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.habittracker.R
import com.habittracker.domain.model.HabitGoalType
import com.habittracker.presentation.ui.theme.HabitTrackerTheme
import com.habittracker.presentation.ui.theme.TahoeElectricBlue
import com.habittracker.presentation.ui.theme.TahoePurple
import com.habittracker.presentation.ui.theme.TahoeIcons

@Composable
fun HabitTypeSelectionDialog(
    onDismiss: () -> Unit,
    onTypeSelected: (HabitGoalType) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.create_new_habit),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = TahoeIcons.XMark,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Simple Habit Option
                HabitTypeOption(
                    title = stringResource(R.string.habit_goal_type_simple),
                    description = stringResource(R.string.habit_goal_type_simple_desc),
                    icon = TahoeIcons.Check,
                    iconTint = TahoeElectricBlue,
                    isDarkTheme = isDarkTheme,
                    onClick = { onTypeSelected(HabitGoalType.SIMPLE) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Measurable Habit Option
                HabitTypeOption(
                    title = stringResource(R.string.habit_goal_type_measurable),
                    description = stringResource(R.string.habit_goal_type_measurable_desc),
                    icon = TahoeIcons.ChartBar,
                    iconTint = TahoePurple,
                    isDarkTheme = isDarkTheme,
                    onClick = { onTypeSelected(HabitGoalType.MEASURABLE) }
                )
            }
        }
    }
}

@Composable
private fun HabitTypeOption(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Circle
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.15f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Text Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitTypeSelectionDialogPreview() {
    HabitTrackerTheme {
        // Since Dialog is a separate window, we can't easily preview it inside the standard preview without it taking full screen or behaving oddly.
        // However, for preview purposes, we can just render the content without the Dialog wrapper or use it as is.
        // But since the original composable wraps content in Dialog, it might not render in interactive preview.
        // Let's create a wrapper that mocks the Dialog content or just call it. 
        // Dialog composable in Preview works but might be overlayed.
        
        // Let's extract the content to a separate composable if we want perfect preview, 
        // but for now let's just preview the dialog call, knowing it displays over the background.
        // Actually, to make it visible in the preview pane effectively, we often need to extract the content.
        // But let's try calling it directly.
        
        // To make it look better in preview, let's just invoke it.
        // Note: Dialog in preview might show up centered or full screen depending on AS version.
        
        // Alternative: Extract content to HabitTypeSelectionContent. 
        // I will refactor slightly to make it previewable properly.
        HabitTypeSelectionContent(
            onDismiss = {},
            onTypeSelected = {}
        )
    }
}

@Composable
private fun HabitTypeSelectionContent(
    onDismiss: () -> Unit,
    onTypeSelected: (HabitGoalType) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(16.dp) // padding for preview visibility
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.create_new_habit),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = TahoeIcons.XMark,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Simple Habit Option
            HabitTypeOption(
                title = stringResource(R.string.habit_goal_type_simple),
                description = stringResource(R.string.habit_goal_type_simple_desc),
                icon = TahoeIcons.Check,
                iconTint = TahoeElectricBlue,
                isDarkTheme = isDarkTheme,
                onClick = { onTypeSelected(HabitGoalType.SIMPLE) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Measurable Habit Option
            HabitTypeOption(
                title = stringResource(R.string.habit_goal_type_measurable),
                description = stringResource(R.string.habit_goal_type_measurable_desc),
                icon = TahoeIcons.ChartBar,
                iconTint = TahoePurple,
                isDarkTheme = isDarkTheme,
                onClick = { onTypeSelected(HabitGoalType.MEASURABLE) }
            )
        }
    }
}
