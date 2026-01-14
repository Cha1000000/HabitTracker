package com.habittracker.presentation.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.habittracker.R
import com.habittracker.presentation.ui.habits.components.GlassmorphicDialog
import com.habittracker.presentation.ui.theme.TahoeIcons

/**
 * Диалог выбора цвета для привычки
 */
@Composable
fun ColorPickerDialog(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        "#2196F3" to stringResource(R.string.color_blue),
        "#4CAF50" to stringResource(R.string.color_green), 
        "#FF9800" to stringResource(R.string.color_orange),
        "#F44336" to stringResource(R.string.color_red),
        "#9C27B0" to stringResource(R.string.color_purple),
        "#00BCD4" to stringResource(R.string.color_cyan),
        "#8BC34A" to stringResource(R.string.color_lime),
        "#FFC107" to stringResource(R.string.color_yellow),
        "#E91E63" to stringResource(R.string.color_pink),
        "#607D8B" to stringResource(R.string.color_gray),
        "#795548" to stringResource(R.string.color_brown),
        "#3F51B5" to stringResource(R.string.color_indigo)
    )

    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.habit_select_color),
                style = MaterialTheme.typography.titleLarge
            )
        },
        content = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(colors) { (colorHex, colorName) ->
                    ColorItem(
                        color = colorHex,
                        name = colorName,
                        isSelected = colorHex == selectedColor,
                        onClick = {
                            onColorSelected(colorHex)
                            onDismiss()
                        }
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Элемент цвета в диалоге выбора
 */
@Composable
private fun ColorItem(
    color: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorValue = Color(color.toColorInt())
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colorValue)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        ) {
            if (isSelected) {
                Icon(
                    imageVector = TahoeIcons.Check,
                    contentDescription = stringResource(R.string.cd_chosen),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview
@Composable
private fun ColorPickerDialogPreview() {
    MaterialTheme {
        ColorPickerDialog(
            selectedColor = "#2196F3",
            onColorSelected = {},
            onDismiss = {}
        )
    }
}
