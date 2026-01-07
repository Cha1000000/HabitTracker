package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habittracker.R
import com.habittracker.presentation.ui.theme.HabitTrackerTheme

@Composable
fun HabitValueInputDialog(
    habitTitle: String,
    currentValue: Int,
    targetValue: Int,
    unit: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { 
        mutableStateOf(
            TextFieldValue(
                text = currentValue.toString(),
                selection = TextRange(currentValue.toString().length)
            )
        ) 
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.enter_value_title))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.enter_value_message, habitTitle, targetValue, unit),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                GlassmorphicTextField(
                    value = textValue.text,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                            textValue = textValue.copy(text = newValue, selection = TextRange(newValue.length))
                        }
                    },
                    label = { Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = textValue.text.toIntOrNull() ?: 0
                    onConfirm(value)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Preview(showBackground = true)
@Composable
private fun HabitValueInputDialogPreview() {
    HabitTrackerTheme {
        HabitValueInputDialog(
            habitTitle = "Пить воду",
            currentValue = 3,
            targetValue = 8,
            unit = "стаканов",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
