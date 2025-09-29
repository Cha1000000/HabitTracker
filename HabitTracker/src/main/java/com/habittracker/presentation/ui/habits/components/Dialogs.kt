package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habittracker.R
import com.habittracker.domain.model.User

/**
 * Универсальный подтверждающий диалог в стиле Tahoe Glassmorphic
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String = "",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        },
        content = {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(text = cancelLabel.ifEmpty { stringResource(R.string.action_cancel) })
            }
            Spacer(modifier = Modifier.size(8.dp))
            GlassmorphicButton(onClick = { onConfirm() }) {
                Text(text = confirmLabel)
            }
        }
    )
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    MaterialTheme {
        ConfirmDialog(
            title = "Confirm action",
            message = "Are you sure you want to proceed?",
            confirmLabel = "Apply",
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview
@Composable
private fun FriendsSelectDialogPreview() {
    val friends = listOf(
        User(id = "1", name = "Alice", email = "alice@example.com"),
        User(id = "2", name = "Bob", email = "bob@example.com"),
        User(id = "3", name = "Charlie", email = "")
    )
    val selected = setOf("1", "3")
    MaterialTheme {
        FriendsSelectDialog(
            friends = friends,
            selectedIds = selected,
            onToggle = {},
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview
@Composable
private fun EmojiPickerDialogPreview() {
    val emojis = listOf("🏃", "📚", "💧", "🎯", "🌿", "💡", "✅", "🔔")
    MaterialTheme {
        EmojiPickerDialog(
            emojis = emojis,
            onSelect = {},
            onDismiss = {}
        )
    }
}

/**
 * Диалог выбора друзей для инвайта/шеринга
 */
@Composable
fun FriendsSelectDialog(
    friends: List<User>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_friends_to_invite),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground
            )
        },
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(friends) { user ->
                    val checked = selectedIds.contains(user.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(user.id) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(user.id) })
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
            Spacer(modifier = Modifier.size(8.dp))
            GlassmorphicButton(onClick = onConfirm) { Text(text = stringResource(R.string.action_done)) }
        }
    )
}

/**
 * Диалог выбора эмодзи
 */
@Composable
fun EmojiPickerDialog(
    emojis: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.habit_select_icon), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        },
        content = {
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp),
                columns = GridCells.Adaptive(minSize = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(emojis.size) { index ->
                    val emoji = emojis[index]
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onSelect(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Диалог для ввода кода приглашения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.QrCode,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.join_by_code_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.join_by_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code.uppercase(),
                    onValueChange = { newCode ->
                        if (newCode.length <= 8 && newCode.all { it.isLetterOrDigit() }) {
                            code = newCode.uppercase()
                            isError = false
                        }
                    },
                    label = { Text(stringResource(R.string.join_by_code_label)) },
                    placeholder = { Text(stringResource(R.string.join_by_code_placeholder)) },
                    isError = isError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.join_by_code_error)) }
                    } else null,
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (code.length == 8) {
                                onJoin(code)
                            } else {
                                isError = true
                            }
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (code.length == 8) {
                        onJoin(code)
                    } else {
                        isError = true
                    }
                },
                enabled = code.isNotEmpty()
            ) {
                Text(stringResource(R.string.join_by_code_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
