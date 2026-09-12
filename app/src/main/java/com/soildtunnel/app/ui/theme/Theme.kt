package com.soildtunnel.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView

// Always-dark pinned scheme. Dynamic color is disabled.
private val ControlRoomScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00232B),
    secondary = NeonMint,
    onSecondary = Color(0xFF00291E),
    tertiary = NeonViolet,
    onTertiary = Color(0xFF1D1240),
    background = Void,
    onBackground = OnDark,
    surface = PanelBottom,
    onSurface = OnDark,
    surfaceVariant = CardSubSurface,
    onSurfaceVariant = OnDarkMuted,
    surfaceContainer = Color(0xFF0B0E14),
    surfaceContainerHigh = Color(0xFF11151C),
    surfaceContainerHighest = Color(0xFF161B24),
    error = NeonRed,
    onError = Color(0xFF2B040C),
    outline = EdgeNeon,
    outlineVariant = Color(0x1A35E0FF),
)

// Same brand, daylight version: ink text on paper surfaces, neon kept for
// accents and the states that carry meaning (go/caution/fault).
private val DayScheme = lightColorScheme(
    primary = Color(0xFF0086A8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF00755A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6A5ACD),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F4F6),
    onBackground = Color(0xFF10151B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10151B),
    surfaceVariant = Color(0xFFE8EDF1),
    onSurfaceVariant = Color(0xFF5B6B7A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF3F5F7),
    surfaceContainerHighest = Color(0xFFE9EDF1),
    error = Color(0xFFC81E45),
    onError = Color(0xFFFFFFFF),
    outline = Color(0x5935B8D8),
    outlineVariant = Color(0x5935B8D8),
)

@Composable
fun SoildTunnelTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // Must run before the colors below are read: every custom color in
    // Color.kt resolves through this flag at composition time.
    Palette.light = !dark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = if (dark) ControlRoomScheme else DayScheme,
        typography = SoildTunnelTypography,
        content = content,
    )
}
