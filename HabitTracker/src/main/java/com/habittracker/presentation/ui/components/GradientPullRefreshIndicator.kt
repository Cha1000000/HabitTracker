package com.habittracker.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Градиентный индикатор для pull-to-refresh без фона.
 * - В режиме тянем: отображает прогресс дугой (0..360°)
 * - В режиме refreshing: крутится как индикатор загрузки
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GradientPullRefreshIndicator(
    refreshing: Boolean,
    state: PullRefreshState,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    strokeWidth: Dp = 5.dp
) {
    val progress = state.progress.coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by if (refreshing) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing)),
            label = "rotation"
        )
    } else remember { mutableFloatStateOf(0f) }
    val isDarkTheme = isSystemInDarkTheme()

    Canvas(modifier = modifier
        .size(size + strokeWidth) // даём место под обводку, чтобы не обрезалось
        .clipToBounds()
    ) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

        // Квадрат под круг по меньшей стороне канвы, с учётом толщины штриха
        val desired = size.toPx()
        val canvasMin = min(this.size.width, this.size.height)
        val diameter = min(desired, canvasMin) - stroke.width
        val left = (this.size.width - diameter) / 2f
        val top = (this.size.height - diameter) / 2f
        val rect = Rect(Offset(left, top), Size(diameter, diameter))

        val minSweep = 16f
        val sweep = if (refreshing) 270f else if (progress > 0f) maxOf(minSweep, progress * 360f) else 0f
        val startAngle = -90f // старт сверху

        val brush = if (isDarkTheme) Brush.sweepGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF1E3A8A),
                0.25f to Color(0xFF3B82F6),
                0.50f to Color(0xFF2563EB),
                0.75f to Color(0xFF0EA5E9),
                1.00f to Color(0xFF1E3A8A)
            ),
            center = rect.center
        ) else Brush.sweepGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF93C5FD),
                0.25f to Color(0xFF60A5FA),
                0.50f to Color(0xFF3B82F6),
                0.75f to Color(0xFF1E3A8A),
                1.00f to Color(0xFF93C5FD)
            ),
            center = rect.center
        )

        if (refreshing) {
            rotate(rotation, pivot = rect.center) {
                drawArc(
                    brush = brush,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = stroke
                )
            }
        } else {
            drawArc(
                brush = brush,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = stroke
            )
        }
    }
}
