package com.soildtunnel.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Skin { AURORA, NEON }

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

val NeonPrimary = Color(0xFFFF3D81)
val NeonBusy = Color(0xFFE1379E)
val NeonOk = Color(0xFFC6FF4D)
val NeonTertiary = Color(0xFFB24BF3)
val NeonWarn = Color(0xFFFFD84D)
val NeonError = Color(0xFFFF3D54)

private val neon: Boolean get() = Palette.skin == Skin.NEON

val AuraIndigo: Color get() = if (neon) NeonBusy else AuraBusy
val NeonCyan: Color get() = if (neon) NeonPrimary else AuraPrimary
val NeonMint: Color get() = if (neon) NeonOk else AuraOk
val NeonViolet: Color get() = if (neon) NeonTertiary else AuraTertiary
val NeonAmber: Color get() = if (neon) NeonWarn else AuraWarn
val NeonRed: Color get() = if (neon) NeonError else AuraError

val SoildTunnelBlue: Color get() = NeonCyan
val SoildTunnelCyan: Color get() = NeonViolet
val SoildTunnelMint: Color get() = NeonMint
val SoildTunnelGlowCyan: Color get() = NeonViolet
val SoildTunnelError: Color get() = NeonRed

private val LightInk = Color(0xFF121621)
private val LightInkMuted = Color(0xFF5D6679)

val OnDark: Color get() = when {
    neon -> Color(0xFFF8F8FA)
    Palette.light -> LightInk
    else -> Color(0xFFF4F6FB)
}

val OnDarkMuted: Color get() = when {
    neon -> Color(0xFFA7ACBF)
    Palette.light -> LightInkMuted
    else -> Color(0xFF9AA3BD)
}

val CardTextPrimary: Color get() = when {
    neon -> Color(0xFFF8F8FA)
    Palette.light -> LightInk
    else -> Color(0xFFF4F6FB)
}

val CardTextMuted: Color get() = when {
    neon -> Color(0xFFA7ACBF)
    Palette.light -> LightInkMuted
    else -> Color(0xFF9AA3BD)
}

val CardTextDim: Color get() = when {
    neon -> Color(0xFF565D71)
    Palette.light -> Color(0xFF8891A5)
    else -> Color(0xFF5C6478)
}

private val LightVoid = Color(0xFFF4F5F8)
private val LightPanelTop = Color(0xD1FFFFFF)
private val LightPanelBottom = Color(0xF0F3F5F8)
private val LightCardSub = Color(0xE6FBFCFE)

val Void: Color get() = when {
    neon -> Color(0xFF050508)
    Palette.light -> LightVoid
    else -> Color(0xFF04050B)
}

val PanelTop: Color get() = when {
    neon -> Color(0xFF14141A)
    Palette.light -> LightPanelTop
    else -> Color(0xAA14172B)
}

val PanelBottom: Color get() = when {
    neon -> Color(0xFF0A0A0E)
    Palette.light -> LightPanelBottom
    else -> Color(0xE60B0D18)
}

val CardSubSurface: Color get() = when {
    neon -> Color(0xFF17171E)
    Palette.light -> LightCardSub
    else -> Color(0xDE141726)
}

val CardSurfaceTop: Color get() = PanelTop
val CardSurfaceBottom: Color get() = PanelBottom

val EdgeNeon: Color get() = when {
    neon -> Color(0x66FF3D81)
    Palette.light -> Color(0x597C6BFF)
    else -> Color(0x267C6BFF)
}

val EdgeNeonBright: Color get() = when {
    neon -> Color(0xB3FF3D81)
    Palette.light -> Color(0x807C6BFF)
    else -> Color(0x407C6BFF)
}

val GlassEdge: Color get() = when {
    neon -> Color(0x16FFFFFF)
    Palette.light -> Color(0x28000000)
    else -> Color(0x2EFFFFFF)
}

val GlassEdgeBright: Color get() = when {
    neon -> Color(0x2BFFFFFF)
    Palette.light -> Color(0x40000000)
    else -> Color(0x45FFFFFF)
}

val GlowPoolCyan: Color get() = when {
    neon -> Color(0xFF1E3C45)
    Palette.light -> Color(0xFFC5E9F7)
    else -> Color(0xFF14496B)
}

val GlowPoolViolet: Color get() = when {
    neon -> Color(0xFF45202E)
    Palette.light -> Color(0xFFD9D2F9)
    else -> Color(0xFF33286E)
}

val GlassFillTop: Color get() = when {
    neon -> Color(0x08FFFFFF)
    Palette.light -> Color(0x1A000000)
    else -> Color(0x2EFFFFFF)
}

val GlassFillBottom: Color get() = when {
    neon -> Color(0x04FFFFFF)
    Palette.light -> Color(0x0D000000)
    else -> Color(0x10FFFFFF)
}

val GlassSheenTop: Color get() = when {
    neon -> Color.Transparent
    Palette.light -> Color(0x15000000)
    else -> Color(0x26FFFFFF)
}

val DrawerGlass: Color get() = when {
    neon -> Color(0xF508080C)
    Palette.light -> Color(0xF7F6F8FA)
    else -> Color(0xF5060710)
}

val SheetGlass: Color get() = when {
    neon -> Color(0xFA0C0C11)
    Palette.light -> Color(0xFAF6F8FA)
    else -> Color(0xFA080A12)
}

val ChipFillTop: Color get() = when {
    neon -> Color(0x0FFFFFFF)
    Palette.light -> Color(0x14000000)
    else -> Color(0x24FFFFFF)
}

val ChipFillBottom: Color get() = when {
    neon -> Color(0x07FFFFFF)
    Palette.light -> Color(0x0A000000)
    else -> Color(0x12FFFFFF)
}

val PanelRadius: Dp get() = if (neon) 14.dp else 24.dp
val InnerCardRadius: Dp get() = if (neon) 10.dp else 18.dp
val SheetRadius: Dp get() = if (neon) 16.dp else 28.dp
val ChipIconRadius: Dp get() = if (neon) 8.dp else 12.dp

fun latencyColor(ms: Long): Color = when {
    ms < 0 -> CardTextDim
    ms < 90 -> NeonMint
    ms < 220 -> NeonAmber
    else -> NeonRed
}
