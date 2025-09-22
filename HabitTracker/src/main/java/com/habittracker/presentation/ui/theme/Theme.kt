package com.habittracker.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Legacy color schemes (kept for compatibility)
private val LegacyDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LegacyLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// Tahoe "Liquid Glass" Dark Theme
private val TahoeDarkColorScheme = darkColorScheme(
    primary = TahoeRoyalBlue,
    onPrimary = Color.White,
    primaryContainer = TahoeElectricBlue,
    onPrimaryContainer = TahoeLightBlue,
    
    secondary = TahoeLavender,
    onSecondary = Color.White,
    secondaryContainer = TahoeDeepPurple,
    onSecondaryContainer = TahoePalePurple,
    
    tertiary = TahoeSkyBlue,
    onTertiary = TahoeDeepBlue,
    tertiaryContainer = TahoeMidnightBlue,
    onTertiaryContainer = TahoeLightBlue,
    
    background = TahoeDeepBlue,
    onBackground = Color(0xFFE2E8F0),
    
    surface = Color(0xFF0F1729),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1A2742),
    onSurfaceVariant = Color(0xFFCBD5E1),
    
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFCA5A5),
    
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    
    scrim = Color.Black.copy(alpha = 0.5f),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = TahoeDeepBlue,
    inversePrimary = TahoeElectricBlue,
    
    surfaceTint = TahoeRoyalBlue
)

// Tahoe "Liquid Glass" Light Theme
private val TahoeLightColorScheme = lightColorScheme(
    primary = TahoeLightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF001551),
    
    secondary = TahoeLightPurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF21005D),
    
    tertiary = Color(0xFF5E5CE6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0DFFF),
    onTertiaryContainer = Color(0xFF0D0D60),
    
    background = TahoeLightBackground,
    onBackground = Color(0xFF1A1C3D),
    
    surface = Color(0xFFFAFBFF),
    onSurface = Color(0xFF1A1C3D),
    surfaceVariant = TahoeLightSurface,
    onSurfaceVariant = Color(0xFF44464F),
    
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    
    scrim = Color.Black.copy(alpha = 0.3f),
    inverseSurface = Color(0xFF2F3146),
    inverseOnSurface = Color(0xFFF1F2F6),
    inversePrimary = TahoeSkyBlue,
    
    surfaceTint = TahoeLightAccent
)

@Composable
fun HabitTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Отключаем по умолчанию для использования Tahoe темы
    useTahoeTheme: Boolean = true, // Флаг для использования Tahoe темы
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useTahoeTheme -> {
            if (darkTheme) TahoeDarkColorScheme else TahoeLightColorScheme
        }
        darkTheme -> LegacyDarkColorScheme
        else -> LegacyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}