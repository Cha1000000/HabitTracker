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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.R

/**
 * Компонент для отображения и управления invite-кодом групповой привычки
 */
@Composable
fun InviteCodeCard(
    modifier: Modifier = Modifier,
    inviteCode: String?,
    isPublic: Boolean,
    isGenerating: Boolean = false,
    onGenerateCode: () -> Unit,
    onTogglePublic: (Boolean) -> Unit,
    onShareCode: (String) -> Unit,
    onDisableCode: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    GlassmorphicCard(
        modifier = modifier.fillMaxWidth(),
        isDarkTheme = isDarkTheme,
        elevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.invite_code_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Переключатель публичности
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPublic) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassmorphicSwitch(
                        checked = isPublic,
                        onCheckedChange = onTogglePublic
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isPublic) {
                    stringResource(R.string.public_habit_description)
                } else {
                    stringResource(R.string.private_habit_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (inviteCode != null) {
                // Отображение существующего кода
                InviteCodeDisplay(
                    inviteCode = inviteCode,
                    onShareCode = { onShareCode(inviteCode) },
                    onDisableCode = onDisableCode
                )
            } else {
                // Генерация нового кода
                InviteCodeGeneration(
                    isGenerating = isGenerating,
                    onGenerateCode = onGenerateCode
                )
            }
        }
    }
}

/**
 * Компонент для отображения существующего invite-кода
 */
@Composable
private fun InviteCodeDisplay(
    inviteCode: String,
    onShareCode: () -> Unit,
    onDisableCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.current_invite_code),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Код в рамке
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable {
                    clipboardManager.setText(AnnotatedString(inviteCode))
                }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = inviteCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inviteCode))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_code),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Кнопки действий
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShareCode,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.share_code))
            }
            
            TextButton(
                onClick = onDisableCode,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.disable_code),
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}

/**
 * Компонент для генерации нового invite-кода
 */
@Composable
private fun InviteCodeGeneration(
    isGenerating: Boolean,
    onGenerateCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_invite_code),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onGenerateCode,
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.generating_code))
            } else {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.generate_invite_code))
            }
        }
    }
}

/**
 * Компонент для ввода invite-кода для присоединения к привычке
 */
@Composable
fun JoinByCodeCard(
    onJoinByCode: (String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var codeInput by remember { mutableStateOf("") }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.join_habit_by_code),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.join_habit_by_code_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it.uppercase() },
                label = { Text(stringResource(R.string.invite_code)) },
                placeholder = { Text("ABC123") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (codeInput.isNotBlank()) {
                            onJoinByCode(codeInput.trim())
                        }
                    }
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (codeInput.isNotBlank()) {
                        onJoinByCode(codeInput.trim())
                    }
                },
                enabled = codeInput.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.joining))
                } else {
                    Text(stringResource(R.string.join_habit))
                }
            }
        }
    }
}

// Preview компонентов
@Preview(showBackground = true)
@Composable
private fun InviteCodeComponentsPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        // Карточка с существующим кодом
        InviteCodeCard(
            inviteCode = "ABC123",
            isPublic = true,
            isGenerating = false,
            onGenerateCode = {},
            onTogglePublic = {},
            onShareCode = {},
            onDisableCode = {}
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Карточка без кода
        InviteCodeCard(
            inviteCode = null,
            isPublic = false,
            isGenerating = false,
            onGenerateCode = {},
            onTogglePublic = {},
            onShareCode = {},
            onDisableCode = {}
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Карточка присоединения
        JoinByCodeCard(
            onJoinByCode = {},
            isLoading = false
        )
    }
}
