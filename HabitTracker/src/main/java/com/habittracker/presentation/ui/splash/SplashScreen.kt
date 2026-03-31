package com.habittracker.presentation.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.habittracker.BuildConfig
import com.habittracker.R
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.AuthState
import com.habittracker.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val isDarkTheme = isSystemInDarkTheme()
    val authState by viewModel.authState.collectAsState()
    
    // Анимация появления логотипа
    val logoScale = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    
    // Анимация появления текста
    val titleAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Анимация логотипа
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(800)
        )
        
        // Анимация заголовка
        delay(300)
        titleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(800)
        )
        
        // Анимация подзаголовка
        delay(200)
        subtitleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(800)
        )
        
        // Ждем завершения анимации, затем проверяем аутентификацию
        delay(2000)
        
        // Определяем, куда переходить в зависимости от состояния аутентификации
        when (authState) {
            is AuthState.Success -> {
                // Если пользователь уже вошел, переходим на главный экран
                navController.navigate(Screen.Habits.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
            else -> {
                // Если пользователь не вошел, переходим на экран логина
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        }
    }
    
    TahoeGradientBackground(
        isDarkTheme = isDarkTheme
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Логотип приложения (иконка)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value),
                    contentAlignment = Alignment.Center
                ) {
                    // Градиентный круг за иконкой
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .tahoeGlassEffect(isDarkTheme)
                    )
                    
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_round),
                        contentDescription = stringResource(R.string.cd_app_logo),
                        modifier = Modifier.size(100.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Название приложения с градиентом
                Box(
                    modifier = Modifier.alpha(titleAlpha.value)
                ) {
                    Text(
                        text = stringResource(R.string.app_name_display),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        Color(0xFF47D2A0),
                                        Color(0xFF736AF0)
                                    )
                                } else {
                                    listOf(
                                        Color(0xFF0AC2A6),
                                        Color(0xFFA78BFA)
                                    )
                                }
                            )
                        ),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Подзаголовок
                Text(
                    text = stringResource(R.string.splash_subtitle),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(subtitleAlpha.value)
                )
            }
            
            // Версия приложения внизу
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp)
                    .alpha(subtitleAlpha.value),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.version_name_format, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDarkTheme) {
                            Color.White.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                }
            }
        }
    }
}

// Extension для стеклянного эффекта
@Composable
fun Modifier.tahoeGlassEffect(isDarkTheme: Boolean): Modifier {
    return this
        .then(
            Modifier.drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDarkTheme) {
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = 0.8f),
                                Color.White.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        },
                        radius = size.minDimension / 2
                    )
                )
            }
        )
}
