package com.habittracker.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Анимированная галочка с эффектом рисования
 */
@Composable
fun AnimatedCheckmark(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Color(0xFF4CAF50),
    uncheckedColor: Color = Color.Gray.copy(alpha = 0.5f),
    strokeWidth: Dp = 3.dp,
    animationDuration: Int = 400
) {
    val checkProgress = remember { Animatable(if (isChecked) 1f else 0f) }
    val circleProgress = remember { Animatable(if (isChecked) 1f else 0f) }
    val scale = remember { Animatable(1f) }
    
    LaunchedEffect(isChecked) {
        if (isChecked) {
            // Анимация появления: сначала круг, потом галочка с bounce
            scale.snapTo(0.8f)
            circleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = animationDuration / 2)
            )
            checkProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = animationDuration / 2)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            // Анимация исчезновения
            checkProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = animationDuration / 3)
            )
            circleProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = animationDuration / 3)
            )
        }
    }
    
    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        val strokePx = strokeWidth.toPx()
        val scaleFactor = scale.value
        val center = Offset(canvasSize / 2, canvasSize / 2)
        val radius = (canvasSize / 2 - strokePx) * scaleFactor
        
        // Рисуем фоновый круг (серый)
        if (circleProgress.value < 1f) {
            drawCircle(
                color = uncheckedColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        
        // Рисуем активный круг (зеленый)
        if (circleProgress.value > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * circleProgress.value,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius,
                    center.y - radius
                ),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        
        // Координаты галочки
        val checkStart = Offset(
            center.x - radius * 0.35f * scaleFactor,
            center.y + radius * 0.05f * scaleFactor
        )
        val checkMid = Offset(
            center.x - radius * 0.05f * scaleFactor,
            center.y + radius * 0.35f * scaleFactor
        )
        val checkEnd = Offset(
            center.x + radius * 0.4f * scaleFactor,
            center.y - radius * 0.25f * scaleFactor
        )
        
        // Рисуем галочку
        if (checkProgress.value > 0f) {
            // Первая часть галочки (короткая линия)
            val firstLineProgress = (checkProgress.value * 2).coerceAtMost(1f)
            if (firstLineProgress > 0f) {
                val firstLineEnd = Offset(
                    checkStart.x + (checkMid.x - checkStart.x) * firstLineProgress,
                    checkStart.y + (checkMid.y - checkStart.y) * firstLineProgress
                )
                drawLine(
                    color = color,
                    start = checkStart,
                    end = firstLineEnd,
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
            
            // Вторая часть галочки (длинная линия)
            val secondLineProgress = ((checkProgress.value - 0.5f) * 2).coerceIn(0f, 1f)
            if (secondLineProgress > 0f) {
                val secondLineEnd = Offset(
                    checkMid.x + (checkEnd.x - checkMid.x) * secondLineProgress,
                    checkMid.y + (checkEnd.y - checkMid.y) * secondLineProgress
                )
                drawLine(
                    color = color,
                    start = checkMid,
                    end = secondLineEnd,
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        } else {
            // Рисуем серую галочку когда не отмечено
            drawLine(
                color = uncheckedColor,
                start = checkStart,
                end = checkMid,
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = uncheckedColor,
                start = checkMid,
                end = checkEnd,
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Простая анимированная галочка без круга
 */
@Composable
fun SimpleAnimatedCheckmark(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    checkedColor: Color = Color(0xFF4CAF50),
    uncheckedColor: Color = Color.Gray,
    strokeWidth: Dp = 2.5.dp
) {
    val progress = remember { Animatable(if (isChecked) 1f else 0f) }
    val scale = remember { Animatable(1f) }
    
    LaunchedEffect(isChecked) {
        if (isChecked) {
            scale.snapTo(0.7f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200)
            )
        }
    }
    
    val currentColor = if (progress.value > 0.5f) checkedColor else uncheckedColor
    
    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        val strokePx = strokeWidth.toPx()
        val scaleFactor = scale.value
        val center = Offset(canvasSize / 2, canvasSize / 2)
        
        // Координаты галочки
        val checkStart = Offset(
            center.x - canvasSize * 0.25f * scaleFactor,
            center.y + canvasSize * 0.02f * scaleFactor
        )
        val checkMid = Offset(
            center.x - canvasSize * 0.05f * scaleFactor,
            center.y + canvasSize * 0.22f * scaleFactor
        )
        val checkEnd = Offset(
            center.x + canvasSize * 0.3f * scaleFactor,
            center.y - canvasSize * 0.18f * scaleFactor
        )
        
        if (progress.value > 0f) {
            // Первая часть галочки
            val firstLineProgress = (progress.value * 2).coerceAtMost(1f)
            if (firstLineProgress > 0f) {
                val firstLineEnd = Offset(
                    checkStart.x + (checkMid.x - checkStart.x) * firstLineProgress,
                    checkStart.y + (checkMid.y - checkStart.y) * firstLineProgress
                )
                drawLine(
                    color = currentColor,
                    start = checkStart,
                    end = firstLineEnd,
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
            
            // Вторая часть галочки
            val secondLineProgress = ((progress.value - 0.5f) * 2).coerceIn(0f, 1f)
            if (secondLineProgress > 0f) {
                val secondLineEnd = Offset(
                    checkMid.x + (checkEnd.x - checkMid.x) * secondLineProgress,
                    checkMid.y + (checkEnd.y - checkMid.y) * secondLineProgress
                )
                drawLine(
                    color = currentColor,
                    start = checkMid,
                    end = secondLineEnd,
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        } else {
            // Рисуем серую галочку когда не отмечено
            drawLine(
                color = uncheckedColor.copy(alpha = 0.5f),
                start = checkStart,
                end = checkMid,
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = uncheckedColor.copy(alpha = 0.5f),
                start = checkMid,
                end = checkEnd,
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}
