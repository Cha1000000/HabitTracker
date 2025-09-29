package com.habittracker.presentation.ui.habits.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.habittracker.presentation.ui.theme.GlassBlack10
import com.habittracker.presentation.ui.theme.GlassBlack20
import com.habittracker.presentation.ui.theme.GlassBlack30
import com.habittracker.presentation.ui.theme.GlassBlack50
import com.habittracker.presentation.ui.theme.GlassWhite10
import com.habittracker.presentation.ui.theme.GlassWhite30
import com.habittracker.presentation.ui.theme.GlassWhite40
import com.habittracker.presentation.ui.theme.TahoeAqua
import com.habittracker.presentation.ui.theme.TahoeDeepBlue
import com.habittracker.presentation.ui.theme.TahoeElectricBlue
import com.habittracker.presentation.ui.theme.TahoeIndigoAccent
import com.habittracker.presentation.ui.theme.TahoeLavender
import com.habittracker.presentation.ui.theme.TahoeLightAccent
import com.habittracker.presentation.ui.theme.TahoeLightBlue
import com.habittracker.presentation.ui.theme.TahoeLightPurpleAccent
import com.habittracker.presentation.ui.theme.TahoeMidnightBlue
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
            TahoeMidnightBlue,
            TahoeElectricBlue,
            TahoeRoyalBlue,
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
        GlassWhite10.copy(alpha = glassAlpha)
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
            .then(
                if (isDarkTheme && elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation * 0.3f,
                        shape = RoundedCornerShape(cornerRadius),
                        ambientColor = Color.White.copy(alpha = 0.05f),
                        spotColor = Color.White.copy(alpha = 0.05f)
                    )
                } else if (!isDarkTheme && elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = RoundedCornerShape(cornerRadius),
                        ambientColor = TahoeLightAccent.copy(alpha = 0.1f),
                        spotColor = TahoeLightPurpleAccent.copy(alpha = 0.1f)
                    )
                } else {
                    Modifier
                }
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
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    singleLine: Boolean = true,
    readOnly: Boolean = false,
) {
    val colors = TextFieldDefaults.colors(
        // Фон полей - делаем темную тему прозрачнее
        focusedContainerColor = if (isDarkTheme) GlassWhite10.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.85f),
        unfocusedContainerColor = if (isDarkTheme) GlassWhite10.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.7f),
        disabledContainerColor = if (isDarkTheme) GlassBlack30.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f),
        // Индикатор фокуса и курсор
        focusedIndicatorColor = if (isDarkTheme) Color(0xFF60A5FA) else TahoeLightAccent,
        unfocusedIndicatorColor = if (isDarkTheme) GlassWhite30.copy(alpha = 0.5f) else GlassBlack20,
        cursorColor = if (isDarkTheme) Color(0xFF60A5FA) else TahoeLightAccent,
        // Текст - улучшаем контраст
        focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
        unfocusedTextColor = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else GlassBlack50,
        // Label - делаем прозрачными для лучшего вида
        focusedLabelColor = if (isDarkTheme) Color(0xFF93C5FD) else TahoeLightAccent, // Яркий голубой для фокуса
        unfocusedLabelColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
        // Placeholder 
        focusedPlaceholderColor = if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.6f),
        unfocusedPlaceholderColor = if (isDarkTheme) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.5f),
        // Иконки - делаем видимыми
        focusedLeadingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedLeadingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        focusedTrailingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        colors = colors,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        readOnly = readOnly,
    )
}

/**
 * Outlined кнопка в стиле Glassmorphic
 */
@Composable
fun GlassmorphicOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable RowScope.() -> Unit
) {
    // Оптимизированные цвета для лучшей видимости логотипа Google
    val backgroundColor = if (isDarkTheme) {
        // В темной теме делаем фон чуть темнее для контраста с логотипом
        if (enabled) Color.Black.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.5f)
    } else {
        // В светлой теме делаем более прозрачным для эффекта стекла
        if (enabled) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f)
    }
    
    val borderColor = if (isDarkTheme) {
        if (enabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
    } else {
        if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f)
    }
    
    val contentColor = if (isDarkTheme) {
        if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    } else {
        if (enabled) MaterialTheme.colorScheme.primary else Color.Gray
    }
    
    // Используем OutlinedButton вместо Button с border для исправления просветов
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor,
            disabledContentColor = contentColor
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        content = content
    )
}

/**
 * Glassmorphic Switch компонент
 */
@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun GlassmorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    trackHeight: Dp = 24.dp,
    trackWidth: Dp = 44.dp,
    thumbSize: Dp = 20.dp,
    animationDuration: Int = 300
) {
    val checkedTrackColor = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary
    val uncheckedTrackColor = if (isDarkTheme) GlassWhite10 else MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = Color.White

    // Анимация смещения ползунка
    val offset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - 2.dp else 2.dp,
        animationSpec = tween(durationMillis = animationDuration), label = ""
    )

    // Цвет фона трека
    //val backgroundColor = if (checked) checkedTrackColor else uncheckedTrackColor

    // Анимация цвета фона трека
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) checkedTrackColor else uncheckedTrackColor,
        animationSpec = tween(durationMillis = animationDuration), label = ""
    )

    Surface(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(backgroundColor)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        color = Color.Transparent,
        shape = RoundedCornerShape(trackHeight / 2),
        shadowElevation = 1.dp
    ) {
        Box(
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .offset(x = offset)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}

/**
 * Glassmorphic Switch компонент
 */
@Composable
fun GlassmorphicSwitchSimple(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary,
        checkedThumbColor = if (isDarkTheme) Color.White else Color.White,
        uncheckedTrackColor = if (isDarkTheme) GlassWhite10 else MaterialTheme.colorScheme.surfaceVariant,
        uncheckedThumbColor = if (isDarkTheme) GlassWhite30 else MaterialTheme.colorScheme.onSurfaceVariant,
        disabledCheckedTrackColor = if (isDarkTheme) TahoeSkyBlue.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(
            alpha = 0.3f
        ),
        disabledUncheckedTrackColor = if (isDarkTheme) GlassWhite10.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.3f
        )
    )

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(0.8f),
        enabled = enabled,
        colors = switchColors
    )
}

/**
 * Заголовок секции в стиле Glassmorphic
 */
@Composable
fun GlassmorphicSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (isDarkTheme) TahoeLightBlue else MaterialTheme.colorScheme.primary,
        fontWeight = SemiBold,
        modifier = modifier
    )
}

/**
 * ExposedDropdownMenuBox в стиле Glassmorphic
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GlassmorphicExposedDropdownMenuBox(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ExposedDropdownMenuBoxScope.() -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        content = content
    )
}

/**
 * Полностью кастомный Glassmorphic DropdownMenu
 */
@Composable
fun GlassmorphicDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset(4, 28),
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    if (expanded) {
        // Создаем полупрозрачный фон для закрытия меню при клике вне его
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        )
        
        // Кастомный popup с glassmorphic эффектом
        Popup(
            alignment = alignment,
            offset = offset,
            onDismissRequest = onDismissRequest,
            properties = androidx.compose.ui.window.PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            // Glassmorphic контейнер
            Box(
                modifier = modifier
                    .widthIn(min = 180.dp, max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    // Полупрозрачный фон с размытием
                    .background(
                        color = if (isDarkTheme) {
                            Color(0xD9000000) // 85% черный
                        } else {
                            Color(0xE6FFFFFF) // 90% белый
                        }
                    )
                    // Glassmorphic эффект с градиентом
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDarkTheme) {
                                listOf(
                                    Color.White.copy(alpha = 0.05f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            } else {
                                listOf(
                                    Color.Black.copy(alpha = 0.02f),
                                    Color.Black.copy(alpha = 0.05f)
                                )
                            }
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = if (isDarkTheme) {
                            GlassWhite30.copy(alpha = 0.4f)
                        } else {
                            GlassBlack10.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * DropdownMenuItem в стиле Glassmorphic
 */
@Composable
fun GlassmorphicDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
            leadingIconColor = if (isDarkTheme) GlassWhite40 else MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = if (isDarkTheme) GlassWhite40 else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTextColor = if (isDarkTheme) GlassWhite30 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    )
}

/**
 * Анимированный градиентный индикатор прогресса
 */
@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    colors: List<Color>? = null,
    trackColor: Color? = null,
    height: Dp = 8.dp,
    cornerRadius: Dp = 4.dp,
    animateProgress: Boolean = true
) {
    val progressColors = colors ?: if (isDarkTheme) {
        listOf(TahoeRoyalBlue, TahoeLavender, TahoeSkyBlue)
    } else {
        listOf(TahoeLightAccent, TahoeLightPurpleAccent, TahoeIndigoAccent)
    }

    val bgTrack = trackColor ?: if (isDarkTheme) GlassWhite10 else GlassBlack10

    val clamped = progress.coerceIn(0f, 1f)
    val animated by if (animateProgress) {
        animateFloatAsState(targetValue = clamped, label = "gradientProgress")
    } else {
        androidx.compose.runtime.remember(clamped) { androidx.compose.runtime.mutableStateOf(clamped) }
    }
    
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                color = bgTrack,
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(
                    brush = Brush.horizontalGradient(colors = progressColors),
                    shape = RoundedCornerShape(cornerRadius)
                )
        )
    }
}
