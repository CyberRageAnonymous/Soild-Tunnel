package com.soildtunnel.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Skin { AURORA, NEON, VOLT }

object Palette {
    var light: Boolean by mutableStateOf(false)
    var skin: Skin by mutableStateOf(Skin.AURORA)
}

val AuraPrimary = Color(0xFF4CC9FF)
val AuraBusy = Color(0xFF7C6BFF)
val AuraOk = Color(0xFF46F2B8)
val AuraTertiary = Color(0xFF9D7BFF)
val AuraWarn = Color(0xFFFFC24B)
val AuraError = Color(0xFFFF5470)

val NeonPrimary = Color(0xFF3D7BFF)
val NeonBusy = Color(0xFFFF3B3B)
val NeonOk = Color(0xFF35A0FF)
val NeonTertiary = Color(0xFFFF5257)
val NeonWarn = Color(0xFFFF7043)
val NeonError = Color(0xFFFF2E43)

// Champagne gold on warm obsidian: one accent for chrome, jade for "go",
// amber while it works, rose when it faults.
val VoltPrimary = Color(0xFFE8C87E)
val VoltBusy = Color(0xFFF0B27A)
val VoltOk = Color(0xFF6ED6A8)
val VoltTertiary = Color(0xFFC9A45C)
val VoltWarn = Color(0xFFF0C56B)
val VoltError = Color(0xFFF2677E)

private val neon: Boolean get() = Palette.skin == Skin.NEON
private val volt: Boolean get() = Palette.skin == Skin.VOLT

val AuraIndigo: Color get() = when {
    volt -> VoltBusy
    neon -> NeonBusy
    else -> AuraBusy
}

val NeonCyan: Color get() = when {
    volt -> VoltPrimary
    neon -> NeonPrimary
    else -> AuraPrimary
}

val NeonMint: Color get() = when {
    volt -> VoltOk
    neon -> NeonOk
    else -> AuraOk
}

val NeonViolet: Color get() = when {
    volt -> VoltTertiary
    neon -> NeonTertiary
    else -> AuraTertiary
}

val NeonAmber: Color get() = when {
    volt -> VoltWarn
    neon -> NeonWarn
    else -> AuraWarn
}

val NeonRed: Color get() = when {
    volt -> VoltError
    neon -> NeonError
    else -> AuraError
}

val SoildTunnelBlue: Color get() = NeonCyan
val SoildTunnelCyan: Color get() = NeonViolet
val SoildTunnelMint: Color get() = NeonMint
val SoildTunnelGlowCyan: Color get() = NeonViolet
val SoildTunnelError: Color get() = NeonRed

private val LightInk = Color(0xFF121621)
private val LightInkMuted = Color(0xFF5D6679)

val OnDark: Color get() = when {
    volt -> Color(0xFFF6F2EA)
    neon -> Color(0xFFF2F4F8)
    Palette.light -> LightInk
    else -> Color(0xFFF4F6FB)
}

val OnDarkMuted: Color get() = when {
    volt -> Color(0xFFA9A296)
    neon -> Color(0xFF9BA6C2)
    Palette.light -> LightInkMuted
    else -> Color(0xFF9AA3BD)
}

val CardTextPrimary: Color get() = when {
    volt -> Color(0xFFF6F2EA)
    neon -> Color(0xFFF2F4F8)
    Palette.light -> LightInk
    else -> Color(0xFFF4F6FB)
}

val CardTextMuted: Color get() = when {
    volt -> Color(0xFFA9A296)
    neon -> Color(0xFF9BA6C2)
    Palette.light -> LightInkMuted
    else -> Color(0xFF9AA3BD)
}

val CardTextDim: Color get() = when {
    volt -> Color(0xFF736D63)
    neon -> Color(0xFF5A6379)
    Palette.light -> Color(0xFF8891A5)
    else -> Color(0xFF5C6478)
}

private val LightVoid = Color(0xFFF4F5F8)
private val LightPanelTop = Color(0xD1FFFFFF)
private val LightPanelBottom = Color(0xF0F3F5F8)
private val LightCardSub = Color(0xE6FBFCFE)

val Void: Color get() = when {
    volt -> Color(0xFF0B0B0D)
    neon -> Color(0xFF030407)
    Palette.light -> LightVoid
    else -> Color(0xFF04050B)
}

val PanelTop: Color get() = when {
    volt -> Color(0xF21B1B21)
    neon -> Color(0xFF0E1322)
    Palette.light -> LightPanelTop
    else -> Color(0xAA14172B)
}

val PanelBottom: Color get() = when {
    volt -> Color(0xE6101015)
    neon -> Color(0xFF070A12)
    Palette.light -> LightPanelBottom
    else -> Color(0xE60B0D18)
}

val CardSubSurface: Color get() = when {
    volt -> Color(0xDE212129)
    neon -> Color(0xFF0F1424)
    Palette.light -> LightCardSub
    else -> Color(0xDE141726)
}

val CardSurfaceTop: Color get() = PanelTop
val CardSurfaceBottom: Color get() = PanelBottom

val EdgeNeon: Color get() = when {
    volt -> Color(0x40E8C87E)
    neon -> Color(0x593D7BFF)
    Palette.light -> Color(0x597C6BFF)
    else -> Color(0x267C6BFF)
}

val EdgeNeonBright: Color get() = when {
    volt -> Color(0x80E8C87E)
    neon -> Color(0x993D7BFF)
    Palette.light -> Color(0x807C6BFF)
    else -> Color(0x407C6BFF)
}

val GlassEdge: Color get() = when {
    volt -> Color(0x33E8C87E)
    neon -> Color(0x16FFFFFF)
    Palette.light -> Color(0x28000000)
    else -> Color(0x2EFFFFFF)
}

val GlassEdgeBright: Color get() = when {
    volt -> Color(0x66E8C87E)
    neon -> Color(0x2BFFFFFF)
    Palette.light -> Color(0x40000000)
    else -> Color(0x45FFFFFF)
}

val GlowPoolCyan: Color get() = when {
    volt -> Color(0xFF2B2416)
    neon -> Color(0xFF122A55)
    Palette.light -> Color(0xFFC5E9F7)
    else -> Color(0xFF14496B)
}

val GlowPoolViolet: Color get() = when {
    volt -> Color(0xFF1B1A2B)
    neon -> Color(0xFF3B1220)
    Palette.light -> Color(0xFFD9D2F9)
    else -> Color(0xFF33286E)
}

val GlassFillTop: Color get() = when {
    volt -> Color(0x22FFFFFF)
    neon -> Color(0x08FFFFFF)
    Palette.light -> Color(0x1A000000)
    else -> Color(0x2EFFFFFF)
}

val GlassFillBottom: Color get() = when {
    volt -> Color(0x0FFFFFFF)
    neon -> Color(0x04FFFFFF)
    Palette.light -> Color(0x0D000000)
    else -> Color(0x10FFFFFF)
}

val GlassSheenTop: Color get() = when {
    volt -> Color(0x1FFFFFFF)
    neon -> Color.Transparent
    Palette.light -> Color(0x15000000)
    else -> Color(0x26FFFFFF)
}

val DrawerGlass: Color get() = when {
    volt -> Color(0xF7101014)
    neon -> Color(0xF505070D)
    Palette.light -> Color(0xF7F6F8FA)
    else -> Color(0xF5060710)
}

val SheetGlass: Color get() = when {
    volt -> Color(0xFA121217)
    neon -> Color(0xFA0A0F18)
    Palette.light -> Color(0xFAF6F8FA)
    else -> Color(0xFA080A12)
}

val ChipFillTop: Color get() = when {
    volt -> Color(0x1AE8C87E)
    neon -> Color(0x0FFFFFFF)
    Palette.light -> Color(0x14000000)
    else -> Color(0x24FFFFFF)
}

val ChipFillBottom: Color get() = when {
    volt -> Color(0x0AE8C87E)
    neon -> Color(0x07FFFFFF)
    Palette.light -> Color(0x0A000000)
    else -> Color(0x12FFFFFF)
}

val PanelRadius: Dp get() = when {
    volt -> 20.dp
    neon -> 12.dp
    else -> 24.dp
}

val InnerCardRadius: Dp get() = when {
    volt -> 16.dp
    neon -> 6.dp
    else -> 18.dp
}

val SheetRadius: Dp get() = when {
    volt -> 26.dp
    neon -> 12.dp
    else -> 28.dp
}

val ChipIconRadius: Dp get() = when {
    volt -> 14.dp
    neon -> 4.dp
    else -> 12.dp
}

/** Radius of the small home-screen pills and header chips. */
val ChipRadius: Dp get() = when {
    volt -> 999.dp
    else -> 16.dp
}

fun latencyColor(ms: Long): Color = when {
    ms < 0 -> CardTextDim
    ms < 90 -> NeonMint
    ms < 220 -> NeonAmber
    else -> NeonRed
}
