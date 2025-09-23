package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.habittracker.R
import com.habittracker.presentation.ui.theme.GlassmorphicButton
import com.habittracker.presentation.ui.theme.TahoeSkyBlue
import kotlinx.datetime.LocalTime
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()

    val timeState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    color = if (isDarkTheme) Color.Black.copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.85f)
                )
                .border(
                    width = 1.dp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.2f)
                    else Color.Black.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.habit_select_time),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(0.dp).padding(vertical = 12.dp))

                TimePicker(
                    state = timeState,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = TimePickerDefaults.colors(
                        clockDialColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        selectorColor = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary,
                        clockDialUnselectedContentColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        clockDialSelectedContentColor = if (isDarkTheme) Color.Black else Color.White,
                        containerColor = Color.Transparent,
                        periodSelectorBorderColor = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary,
                        periodSelectorSelectedContainerColor = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = if (isDarkTheme) Color.Black else Color.White,
                        periodSelectorUnselectedContentColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        timeSelectorSelectedContainerColor = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary,
                        timeSelectorUnselectedContainerColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        timeSelectorSelectedContentColor = if (isDarkTheme) Color.Black else Color.White,
                        timeSelectorUnselectedContentColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.width(0.dp).padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    GlassmorphicButton(
                        onClick = {
                            val selectedTime = LocalTime(
                                hour = timeState.hour,
                                minute = timeState.minute
                            )
                            onTimeSelected(selectedTime)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}
