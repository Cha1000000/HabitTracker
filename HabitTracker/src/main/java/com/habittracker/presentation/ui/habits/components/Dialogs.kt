package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
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
            Text(text = stringResource(R.string.select_friends_to_invite), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
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
