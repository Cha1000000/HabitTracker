package com.habittracker.presentation.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit

/**
 * Оверлей с конфетти для празднования выполнения привычки
 */
@Composable
fun ConfettiOverlay(
    showConfetti: Boolean,
    onConfettiComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isActive by remember { mutableStateOf(false) }
    
    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            isActive = true
            delay(2500) // Длительность анимации
            isActive = false
            onConfettiComplete()
        }
    }
    
    if (isActive) {
        KonfettiView(
            modifier = modifier.fillMaxSize(),
            parties = listOf(
                // Центральный взрыв
                Party(
                    speed = 0f,
                    maxSpeed = 30f,
                    damping = 0.9f,
                    spread = 360,
                    colors = listOf(
                        0xFF4CAF50.toInt(), // Green
                        0xFF2196F3.toInt(), // Blue
                        0xFFFFD700.toInt(), // Gold
                        0xFFFF5722.toInt(), // Orange
                        0xFF9C27B0.toInt(), // Purple
                        0xFFE91E63.toInt()  // Pink
                    ),
                    emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                    position = Position.Relative(0.5, 0.3),
                    shapes = listOf(Shape.Circle, Shape.Square),
                    size = listOf(Size(8), Size(10), Size(12))
                ),
                // Левый фонтан
                Party(
                    speed = 10f,
                    maxSpeed = 30f,
                    damping = 0.9f,
                    angle = 45,
                    spread = 60,
                    colors = listOf(
                        0xFF4CAF50.toInt(),
                        0xFFFFD700.toInt(),
                        0xFF2196F3.toInt()
                    ),
                    emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).perSecond(50),
                    position = Position.Relative(0.0, 1.0),
                    shapes = listOf(Shape.Circle),
                    size = listOf(Size(6), Size(8))
                ),
                // Правый фонтан
                Party(
                    speed = 10f,
                    maxSpeed = 30f,
                    damping = 0.9f,
                    angle = 135,
                    spread = 60,
                    colors = listOf(
                        0xFF4CAF50.toInt(),
                        0xFFFFD700.toInt(),
                        0xFF2196F3.toInt()
                    ),
                    emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).perSecond(50),
                    position = Position.Relative(1.0, 1.0),
                    shapes = listOf(Shape.Circle),
                    size = listOf(Size(6), Size(8))
                )
            )
        )
    }
}

/**
 * Минимальный конфетти-эффект для карточки привычки
 */
@Composable
fun MiniConfettiOverlay(
    showConfetti: Boolean,
    onConfettiComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isActive by remember { mutableStateOf(false) }
    
    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            isActive = true
            delay(1500)
            isActive = false
            onConfettiComplete()
        }
    }
    
    if (isActive) {
        KonfettiView(
            modifier = modifier.fillMaxSize(),
            parties = listOf(
                Party(
                    speed = 0f,
                    maxSpeed = 20f,
                    damping = 0.9f,
                    spread = 360,
                    colors = listOf(
                        0xFF4CAF50.toInt(),
                        0xFFFFD700.toInt(),
                        0xFF2196F3.toInt()
                    ),
                    emitter = Emitter(duration = 50, TimeUnit.MILLISECONDS).max(30),
                    position = Position.Relative(0.5, 0.5),
                    shapes = listOf(Shape.Circle),
                    size = listOf(Size(4), Size(6))
                )
            )
        )
    }
}
