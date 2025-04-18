package com.example.skywalk.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SpaceBlue,
    onPrimary = Color.White,
    primaryContainer = SpaceBlue.copy(alpha = 0.1f),
    onPrimaryContainer = SpaceBlue,
    secondary = CosmicPurple,
    onSecondary = Color.White,
    secondaryContainer = CosmicPurple.copy(alpha = 0.1f),
    onSecondaryContainer = CosmicPurple,
    tertiary = StarYellow,
    onTertiary = Color.Black,
    tertiaryContainer = StarYellow.copy(alpha = 0.1f),
    onTertiaryContainer = Color(0xFF6F5500),
    background = Color.White,
    onBackground = TextPrimaryLight,
    surface = Color.White,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = TextSecondaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),  // Lighter blue for better contrast in dark mode
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0D47A1).copy(alpha = 0.5f),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFFCE93D8),  // Lighter purple for dark mode
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF6A1B9A).copy(alpha = 0.5f),
    onSecondaryContainer = Color(0xFFE1BEE7),
    tertiary = StarYellow,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF795548).copy(alpha = 0.5f),
    onTertiaryContainer = Color(0xFFFFE082),
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondaryDark
)

@Composable
fun SkyWalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Use dark theme by default for the space app
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}