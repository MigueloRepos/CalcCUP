package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF003258),
    primaryContainer = CubanBluePrimary,
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = CubanGold,
    onSecondary = Color(0xFF3E2E04),
    secondaryContainer = Color(0xFF5A4308),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFF80CBC4),
    background = Color(0xFF0B141E),
    surface = Color(0xFF101C2B),
    surfaceVariant = Color(0xFF1E2D40),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF334155)
)

private val LightColorScheme = lightColorScheme(
    primary = CubanBluePrimary,
    onPrimary = Color.White,
    primaryContainer = CubanBlueLight,
    onPrimaryContainer = CubanBlueDark,
    secondary = CubanGoldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF00796B),
    background = SurfaceLight,
    surface = SurfaceCardLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = TextDark,
    onSurface = TextDark,
    error = CubanRed,
    outline = LineBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
