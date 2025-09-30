package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.R
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.UserRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

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
 * Обертка для диалога ввода кода с инъекцией зависимостей
 */
@Composable
fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    val context = LocalContext.current
    val habitRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HabitRepositoryEntryPoint::class.java
        ).habitRepository()
    }
    JoinByCodeDialogContent(
        onDismiss = onDismiss,
        onJoin = onJoin,
        habitRepository = habitRepository
    )
}

/**
 * Entry point для получения HabitRepository через Hilt
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface HabitRepositoryEntryPoint {
    fun habitRepository(): HabitRepository
}

/**
 * Entry point для получения UserRepository через Hilt
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface UserRepositoryEntryPoint {
    fun userRepository(): UserRepository
}

/**
 * Диалог для ввода кода приглашения (внутренняя реализация)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinByCodeDialogContent(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
    habitRepository: HabitRepository
) {
    var code by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var foundHabit by remember { mutableStateOf<com.habittracker.domain.model.Habit?>(null) }
    
    // Реактивный поиск привычки при вводе 8 символов
    LaunchedEffect(code) {
        if (code.length == 8) {
            isSearching = true
            isError = false
            foundHabit = null
            
            // Debounce для оптимизации запросов
            delay(300)
            
            try {
                val habit = habitRepository.getHabitByInviteCode(code)
                foundHabit = habit
                if (habit == null) {
                    isError = true
                }
            } catch (e: Exception) {
                isError = true
            } finally {
                isSearching = false
            }
        } else {
            foundHabit = null
            isError = false
        }
    }
    
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        isDarkTheme = isSystemInDarkTheme(),
        maxWidthFraction = 0.99f,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.join_by_code_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        },
        content = {
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
                        }
                    },
                    label = { Text(stringResource(R.string.join_by_code_label)) },
                    placeholder = { Text(stringResource(R.string.join_by_code_placeholder)) },
                    isError = isError && code.length == 8,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (isError && code.length == 8) {
                        { Text(stringResource(R.string.error_invalid_invite_code)) }
                    } else null,
                    trailingIcon = {
                        when {
                            isSearching -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            foundHabit != null -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (foundHabit != null) {
                                onJoin(code)
                            }
                        }
                    )
                )
                
                // Превью найденной привычки
                foundHabit?.let { habit ->
                    Spacer(modifier = Modifier.height(16.dp))
                    HabitPreviewCard(habit = habit)
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onJoin(code)
                },
                enabled = foundHabit != null && !isSearching,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.GroupAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.join_by_code_button))
            }
        }
    )
}

/**
 * Карточка превью привычки в диалоге
 */
@Composable
private fun HabitPreviewCard(habit: com.habittracker.domain.model.Habit) {
    // Получаем имя автора привычки
    var authorName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    
    LaunchedEffect(habit.createdBy) {
        try {
            val userRepository = EntryPointAccessors.fromApplication(
                context.applicationContext,
                UserRepositoryEntryPoint::class.java
            ).userRepository()
            val user = userRepository.getUserById(habit.createdBy)
            authorName = user?.name ?: habit.createdBy
        } catch (e: Exception) {
            authorName = habit.createdBy
        }
    }
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        isDarkTheme = isSystemInDarkTheme(),
        cornerRadius = 12.dp,
        glassAlpha = 0.1f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка привычки
            Text(
                text = habit.icon,
                fontSize = 32.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            // Информация о привычке
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.habit_preview_author) + " ${authorName ?: "..."}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${habit.participants.size}/${habit.maxParticipants} ${stringResource(R.string.habit_preview_participants)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
