package com.habittracker.presentation.ui.habits.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.presentation.ui.theme.GlassBlack10
import com.habittracker.presentation.ui.theme.GlassWhite20
import com.habittracker.presentation.ui.theme.TahoeDeepBlue
import com.habittracker.presentation.ui.theme.TahoeElectricBlue
import com.habittracker.presentation.ui.theme.TahoeRoyalBlue
import com.habittracker.presentation.ui.theme.TahoeSkyBlue

/**
 * Tahoe Dock Bar в стиле macOS Dock
 * Glassmorphic bottom bar с blur эффектом и анимациями
 */
@Composable
fun TahoeDockBar(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 20.dp)
    ) {
        // Основной контейнер с glassmorphic эффектом
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Backdrop blur effect simulation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .blur(radius = 20.dp)
                    .background(
                        if (isDarkTheme) {
                            TahoeDeepBlue.copy(alpha = 0.6f)
                        } else {
                            Color.White.copy(alpha = 0.6f)
                        }
                    )
            )
            
            // Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}

/**
 * Отдельная кнопка для Dock Bar с анимацией
 */
@Composable
fun TahoeDockItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String? = null,
    badge: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Анимация масштаба при нажатии (как в macOS)
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.85f
            selected -> 1.1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 300f
        ),
        label = "dock_item_scale"
    )
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .scale(scale)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 48.dp else 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) {
                        if (isDarkTheme) {
                            Brush.linearGradient(
                                colors = listOf(
                                    TahoeRoyalBlue.copy(alpha = 0.3f),
                                    TahoeSkyBlue.copy(alpha = 0.2f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    TahoeElectricBlue.copy(alpha = 0.15f),
                                    TahoeRoyalBlue.copy(alpha = 0.1f)
                                )
                            )
                        }
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent
                            )
                        )
                    }
                )
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (badge != null && badge > 0) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (badge > 99) "99+" else badge.toString(),
                                fontSize = 10.sp
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) {
                            if (isDarkTheme) TahoeSkyBlue else TahoeElectricBlue
                        } else {
                            if (isDarkTheme) {
                                Color.White.copy(alpha = 0.9f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            }
                        }
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) {
                        if (isDarkTheme) TahoeSkyBlue else TahoeElectricBlue
                    } else {
                        if (isDarkTheme) {
                            Color.White.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        }
                    }
                )
            }
        }
        
        if (label != null) {
            //Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                modifier = Modifier.offset(y = (-4).dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDarkTheme) {
                    TahoeSkyBlue
                } else {
                    TahoeElectricBlue
                },
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Разделитель для Dock Bar
 */
@Composable
fun TahoeDockDivider(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    Spacer(
        modifier = modifier
            .width(1.dp)
            .height(52.dp)
            .background(
                if (isDarkTheme) {
                    GlassWhite20
                } else {
                    GlassBlack10
                }
            )
    )
}
