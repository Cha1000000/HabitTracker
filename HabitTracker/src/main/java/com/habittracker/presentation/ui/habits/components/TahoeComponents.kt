package com.habittracker.presentation.ui.habits.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType.Companion.LongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.habittracker.R
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
import kotlinx.datetime.Clock.System
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Возвращает настроенные цвета для TextField в стиле Glassmorphic Tahoe
 * Поддерживает темную и светлую темы с прозрачными фонами и улучшенным контрастом
 * 
 * @param isDarkTheme Флаг темной темы (по умолчанию берется из системы)
 * @return TextFieldColors с настроенными цветами для glassmorphic стиля
 */
@Composable
fun rememberGlassmorphicTextFieldColors(
    isDarkTheme: Boolean = isSystemInDarkTheme()
): TextFieldColors {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    return TextFieldDefaults.colors(
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
        focusedLabelColor = if (isDarkTheme) Color(0xFF93C5FD) else TahoeLightAccent,
        unfocusedLabelColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else onSurfaceVariant,
        // Placeholder
        focusedPlaceholderColor = if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.6f),
        unfocusedPlaceholderColor = if (isDarkTheme) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.5f),
        // Иконки - делаем видимыми
        focusedLeadingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else onSurfaceVariant,
        unfocusedLeadingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else onSurfaceVariant.copy(alpha = 0.7f),
        focusedTrailingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else onSurfaceVariant,
        unfocusedTrailingIconColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else onSurfaceVariant.copy(alpha = 0.7f)
    )
}

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
        Color.Black.copy(alpha = 0.85f)
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
    dimmed: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    // Уменьшаем прозрачность и elevation для "приглушенного" состояния
    val effectiveGlassAlpha = if (dimmed) glassAlpha * 0.5f else glassAlpha
    val effectiveBorderAlpha = if (dimmed) borderAlpha * 0.5f else borderAlpha
    val effectiveElevation = if (dimmed) elevation * 0.5f else elevation
    
    val backgroundColor = if (isDarkTheme) {
        GlassWhite10.copy(alpha = effectiveGlassAlpha)
    } else {
        Color.White.copy(alpha = effectiveGlassAlpha)
    }
    
    val borderColor = if (isDarkTheme) {
        GlassWhite30.copy(alpha = effectiveBorderAlpha)
    } else {
        GlassBlack10.copy(alpha = effectiveBorderAlpha)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (isDarkTheme && effectiveElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = effectiveElevation * 0.3f,
                        shape = RoundedCornerShape(cornerRadius),
                        ambientColor = Color.White.copy(alpha = 0.05f),
                        spotColor = Color.White.copy(alpha = 0.05f)
                    )
                } else if (!isDarkTheme && effectiveElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = effectiveElevation,
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
 * Outlined кнопка в стиле Glassmorphic
 */
@Composable
fun GlassmorphicOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    enabled: Boolean = true,
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
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val colors = rememberGlassmorphicTextFieldColors()
    
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
        textStyle = textStyle
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

/**
 * Glassmorphic поисковая строка в стиле macOS
 */
@Composable
fun GlassmorphicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Поиск...",
    onClear: () -> Unit = {},
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    GlassmorphicCard(
        modifier = modifier,
        isDarkTheme = isDarkTheme,
        cornerRadius = 12.dp,
        glassAlpha = if (isDarkTheme) 0.15f else 0.1f,
        borderAlpha = if (isDarkTheme) 0.3f else 0.2f
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { 
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                AnimatedVisibility(visible = query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { /* Handle search */ }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Tahoe стиль фильтр-чип
 */
@Composable
fun TahoeFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
        },
        animationSpec = tween(300),
        label = "chipBackgroundColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        } else {
            if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
        },
        animationSpec = tween(300),
        label = "chipBorderColor"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = tween(200),
        label = "chipScale"
    )
    
    Surface(
        onClick = onClick,
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        modifier = modifier
            .scale(scale)
            .heightIn(min = 32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                label()
            }
        }
    }
}

/**
 * Tahoe Top Bar с заголовком и подзаголовком
 */
@Composable
fun TahoeTopBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            navigationIcon?.invoke()
            
            Column(
                modifier = Modifier.padding(start = if (navigationIcon != null) 8.dp else 0.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions?.invoke(this)
        }
    }
}

/**
 * Круговой прогрессбар для обертки круглых кнопок дат
 */
@Composable
fun TahoeCircularProgressBorder(
    progress: Float,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    strokeWidth: Dp = 2.dp,
    size: Dp = 48.dp
) {
    val progressColor = if (isDarkTheme) TahoeSkyBlue else TahoeLightAccent
    val trackColor = if (isDarkTheme) GlassWhite10 else GlassBlack10.copy(alpha = 0.1f)
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Фоновый круг (трек)
        Canvas(
            modifier = Modifier.size(size)
        ) {
            drawCircle(
                color = trackColor,
                radius = (size.toPx() - strokeWidth.toPx()) / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth.toPx()
                )
            )
        }
        
        // Прогрессбар
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(size),
                color = progressColor,
                strokeWidth = strokeWidth,
                trackColor = Color.Transparent
            )
        }
    }
}

/**
 * Заголовок с датой и прогрессом
 */
@Composable
fun TahoeDateHeader(
    selectedDate: kotlinx.datetime.LocalDate,
    completionPercentage: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("d MMM").withLocale(Locale.getDefault())
    }
    val dateText = if (isToday) {
        stringResource(R.string.label_today)
    } else {
        val javaDate = java.time.LocalDate.of(
            selectedDate.year,
            selectedDate.monthNumber,
            selectedDate.dayOfMonth
        )
        javaDate.format(dateFormatter)
    }
    
    GlassmorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        isDarkTheme = isDarkTheme,
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Text(
                        text = "${selectedDate.dayOfMonth} ${getMonthName(selectedDate.monthNumber)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = stringResource(R.string.label_completed_percentage, completionPercentage),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (completionPercentage == 100) FontWeight.Bold else FontWeight.Normal,
                color = if (completionPercentage == 100) {
                    if (isDarkTheme) TahoeSkyBlue else TahoeLightAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Вспомогательная функция для получения названия месяца
 */
@Composable
private fun getMonthName(month: Int): String {
    return when (month) {
        1 -> stringResource(R.string.month_jan_short)
        2 -> stringResource(R.string.month_feb_short)
        3 -> stringResource(R.string.month_mar_short)
        4 -> stringResource(R.string.month_apr_short)
        5 -> stringResource(R.string.month_may_short)
        6 -> stringResource(R.string.month_jun_short)
        7 -> stringResource(R.string.month_jul_short)
        8 -> stringResource(R.string.month_aug_short)
        9 -> stringResource(R.string.month_sep_short)
        10 -> stringResource(R.string.month_oct_short)
        11 -> stringResource(R.string.month_nov_short)
        12 -> stringResource(R.string.month_dec_short)
        else -> ""
    }
}

/**
 * Компонент дня недели для селектора дат
 */
@Composable
fun DayOfWeekItem(
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    isCompleted: Boolean,
    completionProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isFutureDate: Boolean = false
) {
    val backgroundColor = when {
        isCompleted -> if (isDarkTheme) TahoeSkyBlue else TahoeLightAccent
        isToday -> if (isDarkTheme) GlassWhite30 else GlassBlack20
        else -> Color.Transparent
    }
    
    val textColor = when {
        isCompleted -> Color.White
        isFutureDate -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) // Приглушенный цвет для будущих дат
        isToday -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Box(
        modifier = modifier
            .size(48.dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        // Прогрессбар для частично выполненных дней
        if (!isCompleted && completionProgress > 0f) {
            TahoeCircularProgressBorder(
                progress = completionProgress,
                size = 44.dp,
                isDarkTheme = isDarkTheme
            )
        }
        
        // Кнопка дня
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || isCompleted) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        
        // Индикатор выбранного дня
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 6.dp)
                    .size(width = 20.dp, height = 2.dp)
                    .background(
                        color = if (isDarkTheme) TahoeSkyBlue else TahoeLightAccent,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

/**
 * Горизонтальный селектор дат с днями недели и поддержкой свайпов
 */
@Composable
fun TahoeDateSelector(
    selectedDate: kotlinx.datetime.LocalDate,
    onDateSelected: (kotlinx.datetime.LocalDate) -> Unit,
    weekDates: List<kotlinx.datetime.LocalDate>,
    completionData: Map<kotlinx.datetime.LocalDate, Float>, // Прогресс выполнения для каждого дня (0f-1f)
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onSwipeLeft: (() -> Unit)? = null, // Свайп влево = следующая неделя
    onSwipeRight: (() -> Unit)? = null, // Свайп вправо = предыдущая неделя
    canSwipeLeft: Boolean = true,
    canSwipeRight: Boolean = true,
    onBoundaryReached: ((String) -> Unit)? = null // Колбэк при достижении границы
) {
    val today = System.now().toLocalDateTime(currentSystemDefault()).date
    
    val weekDayNames = listOf(
        stringResource(R.string.day_mon_short),
        stringResource(R.string.day_tue_short),
        stringResource(R.string.day_wed_short),
        stringResource(R.string.day_thu_short),
        stringResource(R.string.day_fri_short),
        stringResource(R.string.day_sat_short),
        stringResource(R.string.day_sun_short)
    )
    
    var totalDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var currentOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = currentOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "weekSwipeAnimation"
    )

    val hapticFeedback = LocalHapticFeedback.current
    val boundaryReachedLeft = stringResource(R.string.boundary_reached_oldest)
    val boundaryReachedRight = stringResource(R.string.boundary_reached_future)
    
    // Вычисляем расширенный список дат для отображения при свайпе
    val itemWidth = 48f // ширина одного элемента в dp
    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.dp.toPx() }
    
    // Определяем, сколько дополнительных дат нужно показать слева и справа
    val offsetInDays = animatedOffset / itemWidthPx
    val extraDaysLeft = if (offsetInDays > 0) ceil(offsetInDays).toInt().coerceAtMost(7) else 0
    val extraDaysRight = if (offsetInDays < 0) ceil(kotlin.math.abs(offsetInDays)).toInt().coerceAtMost(7) else 0
    
    // Создаём расширенный список дат
    val extendedDates = remember(weekDates, extraDaysLeft, extraDaysRight) {
        val firstDate = weekDates.firstOrNull() ?: today
        val lastDate = weekDates.lastOrNull() ?: today
        
        val leftDates: List<kotlinx.datetime.LocalDate> = if (extraDaysLeft > 0) {
            (extraDaysLeft downTo 1).map { days -> 
                kotlinx.datetime.LocalDate.Companion.fromEpochDays(
                    firstDate.toEpochDays() - days
                )
            }
        } else emptyList()
        
        val rightDates: List<kotlinx.datetime.LocalDate> = if (extraDaysRight > 0) {
            (1..extraDaysRight).map { days -> 
                kotlinx.datetime.LocalDate.Companion.fromEpochDays(
                    lastDate.toEpochDays() + days
                )
            }
        } else emptyList()
        
        leftDates + weekDates + rightDates
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .pointerInput(canSwipeLeft, canSwipeRight) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                    },
                    onDragEnd = {
                        // Порог для срабатывания свайпа
                        val threshold = 100f
                        
                        if (totalDrag > threshold && canSwipeRight) {
                            // Свайп вправо - предыдущая неделя
                            onSwipeRight?.invoke()
                        } else if (totalDrag < -threshold && canSwipeLeft) {
                            // Свайп влево - следующая неделя
                            onSwipeLeft?.invoke()
                        } else if (totalDrag > threshold) {
                            // Достигли границы слева
                            hapticFeedback.performHapticFeedback(LongPress)
                            onBoundaryReached?.invoke(boundaryReachedLeft)
                        } else if (totalDrag < -threshold && !canSwipeLeft) {
                            // Достигли границы справа
                            hapticFeedback.performHapticFeedback(LongPress)
                            onBoundaryReached?.invoke(boundaryReachedRight)
                        }
                        
                        totalDrag = 0f
                        currentOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                        
                        // Ограничиваем визуальное смещение в зависимости от границ
                        // Максимум 7 дней * ширина элемента = ~336px
                        val maxOffset = itemWidthPx * 7
                        val newOffset = (currentOffset + dragAmount).coerceIn(-maxOffset, maxOffset)
                        
                        // Применяем резистентность при достижении границ
                        currentOffset = when {
                            newOffset > 0 && !canSwipeRight -> newOffset * 0.3f // Резистентность слева
                            newOffset < 0 && !canSwipeLeft -> newOffset * 0.3f // Резистентность справа
                            else -> newOffset
                        }
                    }
                )
            }
    ) {
        // Строка с днями недели
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekDayNames.forEach { dayName ->
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                    textAlign = Center
                )
            }
        }
        //Spacer(modifier = Modifier.height(8.dp))
        // Строка с числами дней
        Box(
            modifier = Modifier
                .fillMaxWidth().padding(start = 2.dp)
                .height(48.dp)
                .graphicsLayer { 
                    clip = false // Отключаем обрезку для показа дополнительных дат
                }
        ) {
            // Вычисляем параметры для выравнивания
            val screenWidth = with(density) { 
                LocalConfiguration.current.screenWidthDp.dp.toPx()
            }
            val containerWidth = screenWidth - with(density) { 32.dp.toPx() } // минус padding
            val spacing = (containerWidth - (7 * itemWidthPx)) / 6.96f // расстояние между элементами
            val totalItemWidth = itemWidthPx + spacing // полная ширина одного элемента с отступом
            
            // Создаём Row с фиксированными позициями элементов
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { 
                        clip = false
                        // Смещаем весь контент с учётом дополнительных дат слева
                        translationX = animatedOffset - (extraDaysLeft * totalItemWidth)
                    }
            ) {
                extendedDates.forEachIndexed { index, date ->
                    val progress = completionData[date] ?: 0f
                    val isCompleted = progress >= 1f
                    val isFutureDate = date > today
                    
                    Box(
                        modifier = Modifier
                            .offset(x = with(density) { (index * totalItemWidth).toDp() })
                            .width(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DayOfWeekItem(
                            dayNumber = date.dayOfMonth,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            isCompleted = isCompleted,
                            completionProgress = if (!isCompleted) progress else 0f,
                            onClick = { onDateSelected(date) },
                            isDarkTheme = isDarkTheme,
                            isFutureDate = isFutureDate
                        )
                    }
                }
            }
        }
    }
}

/**
 * Кнопка возврата к текущему дню
 */
@Composable
fun TahoeBackToTodayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    GlassmorphicButton(
        onClick = onClick,
        modifier = modifier,
        isDarkTheme = isDarkTheme
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.button_today),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
