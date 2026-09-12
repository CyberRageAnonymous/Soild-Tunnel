package com.soildtunnel.app.ui.theme

import androidx.compose.ui.graphics.Color

// Control Room palette. Every screen reads colors through these same names;
// the light theme only changes which value each name resolves to, so no
// screen has to care which theme is active. (Some names still say "dark" —
// they date from when the app was dark-only and were kept to avoid churn.)

/** Flipped by the theme setting before composition; the colors follow it. */
object Palette {
    var light: Boolean = false
}

// ---- Neon accents (same ink in both themes) --------------------------------

/** Idle/standby accent, borders, focus states. */
val NeonCyan = Color(0xFF35E0FF)
/** "GO" state: tunnel up. Kept in the mint family for brand continuity. */
val NeonMint = Color(0xFF3DFFC8)
/** Secondary light: used for upload, secondary glow bands, gradients. */
val NeonViolet = Color(0xFF9D7BFF)
/** Caution: slow latency, degraded links. */
val NeonAmber = Color(0xFFFFC24B)
/** Fault: errors, unreachable nodes, kill-switch. */
val NeonRed = Color(0xFFFF4D6F)

// Back-compat aliases (older panels still import these).
val SoildTunnelBlue = NeonCyan
val SoildTunnelCyan = NeonViolet
val SoildTunnelMint = NeonMint
val SoildTunnelGlowCyan = NeonViolet
val SoildTunnelError = NeonRed

// ---- Text ------------------------------------------------------------------

private val LightInk = Color(0xFF10151B)
private val LightInkMuted = Color(0xFF5B6B7A)

val OnDark: Color get() = if (Palette.light) LightInk else Color(0xFFF2F8FC)
val OnDarkMuted: Color get() = if (Palette.light) LightInkMuted else Color(0xFF93A4B4)

val CardTextPrimary: Color get() = if (Palette.light) LightInk else Color(0xFFF2F8FC)
val CardTextMuted: Color get() = if (Palette.light) LightInkMuted else Color(0xFF93A4B4)
val CardTextDim: Color get() = if (Palette.light) Color(0xFF93A4B4) else Color(0xFF5A6875)

// ---- Console surfaces ------------------------------------------------------

private val LightVoid = Color(0xFFF2F4F6)
private val LightPanelTop = Color(0xD9FFFFFF)
private val LightPanelBottom = Color(0xF2F3F5F7)
private val LightCardSub = Color(0xFFFBFCFD)

/** Void behind everything: near-black with slight blue tint. */
val Void: Color get() = if (Palette.light) LightVoid else Color(0xFF030408)

/** Translucent console panel, top of the vertical gradient. */
val PanelTop: Color get() = if (Palette.light) LightPanelTop else Color(0xB310141B)
/** Same panel, bottom: slightly more opaque so text always sits on enough ink. */
val PanelBottom: Color get() = if (Palette.light) LightPanelBottom else Color(0xE607090C)
/** Sub-containers inside a panel (IP pill, speed strip, meta strip). */
val CardSubSurface: Color get() = if (Palette.light) LightCardSub else Color(0xFF0B0E14)

// Back-compat aliases.
val CardSurfaceTop: Color get() = PanelTop
val CardSurfaceBottom: Color get() = PanelBottom

// ---- Hairline edges --------------------------------------------------------

/** Standard 1px edge on panels: faint cyan. */
val EdgeNeon: Color get() = if (Palette.light) Color(0x5935B8D8) else Color(0x2935E0FF)
/** Brighter edge for elevated surfaces (power orb ring, drawer). */
val EdgeNeonBright: Color get() = if (Palette.light) Color(0x8035B8D8) else Color(0x4035E0FF)
/** Hairline used inside glass chips: white on dark, ink on light. */
val GlassEdge: Color get() = if (Palette.light) Color(0x30000000) else Color(0x30FFFFFF)
val GlassEdgeBright: Color get() = if (Palette.light) Color(0x47000000) else Color(0x47FFFFFF)

// ---- Backdrop --------------------------------------------------------------

/** Grid lines over the background. Alpha-first so it stays whisper-quiet. */
val GridLine: Color get() = if (Palette.light) Color(0x2E0E9DBC) else Color(0x1135E0FF)
/** Corner glow pools behind everything: neon on dark, pastel on light. */
val GlowPoolCyan: Color get() = if (Palette.light) Color(0xFFBFE9F5) else Color(0xFF1E6E85)
val GlowPoolViolet: Color get() = if (Palette.light) Color(0xFFD9CFF7) else Color(0xFF4A3B8C)

// ---- Liquid-glass fills (white lift on dark, ink shade on light) -----------

val GlassFillTop: Color get() = if (Palette.light) Color(0x14000000) else Color(0x26FFFFFF)
val GlassFillBottom: Color get() = if (Palette.light) Color(0x08000000) else Color(0x0DFFFFFF)
val GlassSheenTop: Color get() = if (Palette.light) Color(0x0D000000) else Color(0x17FFFFFF)

/** Drawer surface: nearly opaque so logs stay readable. */
val DrawerGlass: Color get() = if (Palette.light) Color(0xF7F6F8FA) else Color(0xF5030508)
/** Bottom-sheet surface, slightly more opaque for legibility. */
val SheetGlass: Color get() = if (Palette.light) Color(0xFAF6F8FA) else Color(0xFA070A0F)
/** Small circular chips behind the top-bar icons. */
val ChipFillTop: Color get() = if (Palette.light) Color(0x14000000) else Color(0x21FFFFFF)
val ChipFillBottom: Color get() = if (Palette.light) Color(0x0A000000) else Color(0x0FFFFFFF)

// ---- Latency badges --------------------------------------------------------

fun latencyColor(ms: Long): Color = when {
    ms < 0 -> CardTextDim
    ms < 90 -> NeonMint
    ms < 220 -> NeonAmber
    else -> NeonRed
}
