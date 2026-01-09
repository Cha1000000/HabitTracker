package com.habittracker.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habittracker.R
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.utils.GroupHabitValidationResult
import com.habittracker.presentation.utils.ValidationResult

/**
 * Компонент для отображения результатов валидации с анимациями
 */
@Composable
fun ValidationFeedback(
    validationResult: GroupHabitValidationResult?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = validationResult != null && (!validationResult.isValid || validationResult.warnings.isNotEmpty()),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        validationResult?.let { result ->
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Отображение ошибок
                result.errors.forEach { error ->
                    ValidationMessage(
                        message = error,
                        type = ValidationMessageType.ERROR
                    )
                }
                
                // Отображение предупреждений
                result.warnings.forEach { warning ->
                    ValidationMessage(
                        message = warning,
                        type = ValidationMessageType.WARNING
                    )
                }
            }
        }
    }
}

/**
 * Компонент для отображения отдельного сообщения валидации
 */
@Composable
fun ValidationMessage(
    message: String,
    type: ValidationMessageType,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, icon) = when (type) {
        ValidationMessageType.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            TahoeIcons.Error
        )
        ValidationMessageType.WARNING -> Triple(
            Color(0xFFFFF3CD), // Light yellow
            Color(0xFF856404), // Dark yellow
            TahoeIcons.Warning
        )
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Компонент для отображения простого результата валидации
 */
@Composable
fun SimpleValidationFeedback(
    validationResult: ValidationResult?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = validationResult != null && validationResult !is ValidationResult.Success,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        validationResult?.let { result ->
            when (result) {
                is ValidationResult.Error -> {
                    ValidationMessage(
                        message = result.message,
                        type = ValidationMessageType.ERROR
                    )
                }
                is ValidationResult.Warning -> {
                    ValidationMessage(
                        message = result.message,
                        type = ValidationMessageType.WARNING
                    )
                }
                else -> {}
            }
        }
    }
}

/**
 * Компонент для отображения ошибки отдельного поля
 */
@Composable
fun FieldValidationFeedback(
    errorMessage: String?,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !errorMessage.isNullOrBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        errorMessage?.let { message ->
            ValidationMessage(
                message = message,
                type = if (isWarning) ValidationMessageType.WARNING else ValidationMessageType.ERROR
            )
        }
    }
}

/**
 * Компонент для отображения прогресса валидации в реальном времени
 */
@Composable
fun LiveValidationIndicator(
    isValid: Boolean,
    hasWarnings: Boolean,
    modifier: Modifier = Modifier
) {
    val (color, icon, text) = when {
        isValid && !hasWarnings -> Triple(
            MaterialTheme.colorScheme.primary,
            TahoeIcons.Check,
            stringResource(R.string.validation_all_good)
        )
        isValid && hasWarnings -> Triple(
            Color(0xFFFF9800), // Orange
            TahoeIcons.Warning,
            stringResource(R.string.validation_has_warnings)
        )
        else -> Triple(
            MaterialTheme.colorScheme.error,
            TahoeIcons.Error,
            stringResource(R.string.validation_has_errors)
        )
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Типы сообщений валидации
 */
enum class ValidationMessageType {
    ERROR,
    WARNING
}

@Preview(showBackground = true)
@Composable
fun ValidationFeedbackPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ValidationFeedback(
                validationResult = GroupHabitValidationResult(
                    isValid = false,
                    errors = listOf(
                        "Название привычки не может быть пустым",
                        "Превышен лимит участников (10)"
                    ),
                    warnings = listOf(
                        "Вы не выбрали друзей для приглашения"
                    ),
                    canProceed = false
                )
            )
            
            LiveValidationIndicator(
                isValid = true,
                hasWarnings = false
            )
            
            LiveValidationIndicator(
                isValid = true,
                hasWarnings = true
            )
            
            LiveValidationIndicator(
                isValid = false,
                hasWarnings = false
            )
        }
    }
}
