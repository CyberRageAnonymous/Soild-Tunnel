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
private val AuroraVoidDark = Color(0xFF04050B)
private val AuroraPanelDark = Color(0xE60B0D18)
private val AuroraCardSubDark = Color(0xDE141726)
private val AuroraInk = Color(0xFFF4F6FB)
private val AuroraInkMuted = Color(0xFF9AA3BD)
private val AuroraEdge = Color(0x267C6BFF)

private val ControlRoomScheme = darkColorScheme(
    primary = AuraPrimary,
    onPrimary = Color(0xFF00232B),
    secondary = AuraOk,
    onSecondary = Color(0xFF00291E),
    tertiary = AuraTertiary,
    onTertiary = Color(0xFF1D1240),
    background = AuroraVoidDark,
    onBackground = AuroraInk,
    surface = AuroraPanelDark,
    onSurface = AuroraInk,
    surfaceVariant = AuroraCardSubDark,
    onSurfaceVariant = AuroraInkMuted,
    surfaceContainer = Color(0xFF0B0E14),
    surfaceContainerHigh = Color(0xFF11151C),
    surfaceContainerHighest = Color(0xFF161B24),
    error = AuraError,
    onError = Color(0xFF2B040C),
    outline = AuroraEdge,
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

private val NeonScheme = darkColorScheme(
    primary = NeonPrimary,
    onPrimary = Color(0xFF2B0011),
    secondary = NeonOk,
    onSecondary = Color(0xFF16250A),
    tertiary = NeonTertiary,
    onTertiary = Color(0xFF220633),
    background = Color(0xFF050508),
    onBackground = Color(0xFFF8F8FA),
    surface = Color(0xFF0A0A0E),
    onSurface = Color(0xFFF8F8FA),
    surfaceVariant = Color(0xFF17171E),
    onSurfaceVariant = Color(0xFFA7ACBF),
    surfaceContainer = Color(0xFF0C0C10),
    surfaceContainerHigh = Color(0xFF111118),
    surfaceContainerHighest = Color(0xFF16161D),
    error = NeonError,
    onError = Color(0xFF2B070C),
    outline = Color(0x66FF3D81),
    outlineVariant = Color(0x26FF3D81),
)

@Composable
fun SoildTunnelTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.NEON -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    Palette.skin = if (themeMode == ThemeMode.NEON) Skin.NEON else Skin.AURORA
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
        colorScheme = when {
            themeMode == ThemeMode.NEON -> NeonScheme
            dark -> ControlRoomScheme
            else -> DayScheme
        },
        typography = SoildTunnelTypography,
        content = content,
    )
}
