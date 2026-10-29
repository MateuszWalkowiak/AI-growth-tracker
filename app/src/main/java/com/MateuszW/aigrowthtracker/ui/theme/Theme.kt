package com.MateuszW.aigrowthtracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Tryb motywu: LIGHT, DARK, SYSTEM ──────────────────────────────────────
enum class ThemeMode { LIGHT, DARK, SYSTEM }

// Globalny stan motywu - ustawiany z SettingsScreen, odczytywany w AIGrowthTrackerTheme
object ThemeState {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
}

private val DarkColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6B3A00),
    onPrimaryContainer = Orange80,
    secondary = Amber80,
    onSecondary = Color(0xFF473600),
    secondaryContainer = Color(0xFF614E00),
    onSecondaryContainer = Amber80,
    tertiary = Gold80,
    onTertiary = Color(0xFF3F3000),
    tertiaryContainer = Color(0xFF5A4600),
    onTertiaryContainer = Gold80,
    background = WarmGray,
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF1E1A17),
    onSurface = Color(0xFFEDE0D4),
    surfaceVariant = Color(0xFF3B3229),
    onSurfaceVariant = Color(0xFFD4C4B1),
    outline = Color(0xFF9D8E7E),
    outlineVariant = Color(0xFF4F4539),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF3A1700),
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0C7),
    onSecondaryContainer = Color(0xFF2A1F00),
    tertiary = Gold40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08C),
    onTertiaryContainer = Color(0xFF241A00),
    background = WarmCream,
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF5DFD0),
    onSurfaceVariant = Color(0xFF52443B),
    outline = Color(0xFF857469),
    outlineVariant = Color(0xFFD8C3B6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun AIGrowthTrackerTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = when (ThemeState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Tint the status bar to match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
