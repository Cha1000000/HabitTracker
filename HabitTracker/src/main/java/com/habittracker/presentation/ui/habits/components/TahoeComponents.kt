package com.habittracker.presentation.ui.habits.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.tooling.preview.Preview
import com.habittracker.presentation.ui.theme.GlassBlack10
import com.habittracker.presentation.ui.theme.GlassBlack20
import com.habittracker.presentation.ui.theme.GlassBlack30
import com.habittracker.presentation.ui.theme.GlassBlack50
import com.habittracker.presentation.ui.theme.GlassWhite10
import com.habittracker.presentation.ui.theme.GlassWhite20
import com.habittracker.presentation.ui.theme.GlassWhite30
import com.habittracker.presentation.ui.theme.GlassWhite40
import com.habittracker.presentation.ui.theme.GlassWhite50
import com.habittracker.presentation.ui.theme.TahoeAqua
import com.habittracker.presentation.ui.theme.TahoeDeepBlue
import com.habittracker.presentation.ui.theme.TahoeElectricBlue
import com.habittracker.presentation.ui.theme.TahoeLavender
import com.habittracker.presentation.ui.theme.TahoeLightAccent
import com.habittracker.presentation.ui.theme.TahoeLightPurpleAccent
import com.habittracker.presentation.ui.theme.TahoeRoyalBlue
import com.habittracker.presentation.ui.theme.TahoeSkyBlue
import kotlin.math.cos
import kotlin.math.sin

/**
 * Модификатор для создания градиентного фона в стиле Tahoe
 */
@Composable
fun Modifier.tahoeGradientBackground(
    isDarkTheme: Boolean = isSystemInDarkTheme()
): Modifier {
    val gradientColors = if (isDarkTheme) {
        listOf(
            TahoeDeepBlue,
            TahoeElectricBlue,
            TahoeRoyalBlue,
            TahoeSkyBlue
        )
    } else {
        listOf(
            Color(0xFFE0F2FE),
            Color(0xFFBAE6FD),
            Color(0xFF7DD3FC),
            Color(0xFF38BDF8)
        )
    }
    return this.drawBehind {
        val angleInRadians = (135f * Math.PI / 160f).toFloat()
        val x = cos(angleInRadians) * size.width
        val y = sin(angleInRadians) * size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = gradientColors,
                start = Offset(0f, 0f),
                end = Offset(x, y)
            ),
            size = size
        )
    }
}

@Preview
@Composable
private fun GlassmorphicDialogPreview() {
    MaterialTheme {
        GlassmorphicDialog(
            onDismissRequest = {},
            title = { Text("Preview title") },
            content = { Text("Preview content goes here. It can wrap to multiple lines.") },
            actions = {
                Text("Action", color = Color.White)
            }
        )
    }
}

/**
 * Базовый диалог в стиле Glassmorphic Tahoe
 */
@Composable
fun GlassmorphicDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    cornerRadius: Dp = 24.dp,
    maxWidthFraction: Float = 0.96f,
    maxHeight: Dp = 560.dp,
    title: (@Composable (() -> Unit))? = null,
    content: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val containerColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.6f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    val borderColor = if (isDarkTheme) {
        GlassWhite30
    } else {
        GlassBlack10
    }

    Dialog(onDismissRequest = onDismissRequest) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.96f)
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth(maxWidthFraction)
                    .heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                containerColor,
                                containerColor.copy(alpha = containerColor.alpha * 0.8f)
                            )
                        )
                    )
                    .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (title != null) {
                        title()
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    content()

                    if (actions != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            content = actions
                        )
                    }
                }
            }
        }
    }
}

/**
 * Модификатор для создания радиального градиента (для акцентов)
 */
@Composable
fun Modifier.tahoeRadialGradient(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    center: Offset = Offset.Unspecified
): Modifier {
    val gradientColors = if (isDarkTheme) {
        listOf(
            Color.Transparent,
            TahoeSkyBlue.copy(alpha = 0.1f),
            TahoeAqua.copy(alpha = 0.05f),
            Color.Transparent
        )
    } else {
        listOf(
            Color.Transparent,
            Color(0x1A38BDF8),  // Светло-голубой с прозрачностью
            Color(0x0D7DD3FC),  // Очень прозрачный голубой
            Color.Transparent
        )
    }

    return this.drawBehind {
        val finalCenter = if (center == Offset.Unspecified) {
            Offset(size.width / 2f, size.height / 2f)
        } else center

        drawCircle(
            brush = Brush.radialGradient(
                colors = gradientColors,
                center = finalCenter,
                radius = maxOf(size.width, size.height)
            ),
            center = finalCenter,
            radius = maxOf(size.width, size.height)
        )
    }
}

/**
 * Glassmorphic карточка в стиле Tahoe
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    cornerRadius: Dp = 16.dp,
    glassAlpha: Float = if (isDarkTheme) 0.15f else 0.85f,
    borderAlpha: Float = if (isDarkTheme) 0.3f else 0.15f,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor = if (isDarkTheme) {
        GlassWhite20.copy(alpha = glassAlpha)
    } else {
        Color.White.copy(alpha = glassAlpha)
    }
    
    val borderColor = if (isDarkTheme) {
        GlassWhite30.copy(alpha = borderAlpha)
    } else {
        GlassBlack10.copy(alpha = borderAlpha)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (isDarkTheme) TahoeRoyalBlue.copy(alpha = 0.2f) else TahoeLightAccent.copy(alpha = 0.1f),
                spotColor = if (isDarkTheme) TahoeLavender.copy(alpha = 0.2f) else TahoeLightPurpleAccent.copy(alpha = 0.1f)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = backgroundColor.alpha * 0.7f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            ),
        content = content
    )
}

/**
 * Кнопка в стиле Glassmorphic
 */
@Composable
fun GlassmorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable RowScope.() -> Unit
) {
    val backgroundColor = if (isDarkTheme) {
        if (enabled) TahoeRoyalBlue else GlassWhite10
    } else {
        val actionColor = TahoeLightAccent
        if (enabled) actionColor.copy(alpha = 0.9f) else actionColor.copy(alpha = 0.5f)
    }
    
    val borderColor = if (isDarkTheme) {
        GlassWhite40
    } else {
        GlassBlack10
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = Color.White,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f),
            disabledContentColor = if (isDarkTheme) GlassWhite30 else GlassBlack30
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        shape = RoundedCornerShape(18.dp),
        content = content
    )
}

/**
 * Floating Action Button в стиле Glassmorphic
 */
@Composable
fun GlassmorphicFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    if (isDarkTheme) {
        // В темной теме используем полностью кастомную реализацию без теней
        Box(
            modifier = modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        // В светлой теме используем стандартный FAB с минимальной elevation
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = Color.White.copy(alpha = 0.95f),
            contentColor = TahoeLightAccent,
            shape = RoundedCornerShape(16.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                hoveredElevation = 6.dp,
                focusedElevation = 6.dp
            )
        ) {
            content()
        }
    }
}

/**
 * Компонент для градиентного фона экрана
 */
@Composable
fun TahoeGradientBackground(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .tahoeGradientBackground(isDarkTheme)
    ) {
        // Добавляем дополнительный эффект свечения
        Box(
            modifier = Modifier
                .fillMaxSize()
                .tahoeRadialGradient(
                    isDarkTheme = isDarkTheme,
                    center = Offset(0f, 0f)
                )
        )
        
        // Контент поверх градиента
        content()
    }
}

/**
 * Текстовое поле в стиле Glassmorphic
 */
@Composable
fun GlassmorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    singleLine: Boolean = true
) {
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = if (isDarkTheme) GlassWhite10 else Color.White.copy(alpha = 0.8f),
        unfocusedContainerColor = if (isDarkTheme) GlassWhite10.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f),
        disabledContainerColor = if (isDarkTheme) GlassBlack30 else Color.Gray.copy(alpha = 0.3f),
        focusedIndicatorColor = if (isDarkTheme) TahoeRoyalBlue else TahoeLightAccent,
        unfocusedIndicatorColor = if (isDarkTheme) GlassWhite30 else GlassBlack20,
        focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
        unfocusedTextColor = if (isDarkTheme) GlassWhite50 else GlassBlack50
    )
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (isDarkTheme) GlassWhite20 else GlassBlack10,
                shape = RoundedCornerShape(12.dp)
            ),
        label = label,
        placeholder = placeholder,
        colors = colors,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp)
    )
}

/**
 * Анимированный градиентный индикатор прогресса
 */
@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val progressColors = if (isDarkTheme) {
        listOf(
            TahoeRoyalBlue,
            TahoeLavender,
            TahoeSkyBlue
        )
    } else {
        listOf(
            TahoeLightAccent,
            TahoeLightPurpleAccent,
            Color(0xFF5E5CE6)
        )
    }
    
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                color = if (isDarkTheme) GlassWhite10 else GlassBlack10,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(
                    brush = Brush.horizontalGradient(colors = progressColors),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}
