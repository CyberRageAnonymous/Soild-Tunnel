package com.soildtunnel.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object Palette {
    var light: Boolean by mutableStateOf(false)
}

val AuraIndigo = Color(0xFF7C6BFF)
val NeonCyan = Color(0xFF4CC9FF)
val NeonMint = Color(0xFF46F2B8)
val NeonViolet = Color(0xFF9D7BFF)
val NeonAmber = Color(0xFFFFC24B)
val NeonRed = Color(0xFFFF5470)

val SoildTunnelBlue = NeonCyan
val SoildTunnelCyan = NeonViolet
val SoildTunnelMint = NeonMint
val SoildTunnelGlowCyan = NeonViolet
val SoildTunnelError = NeonRed

private val LightInk = Color(0xFF121621)
private val LightInkMuted = Color(0xFF5D6679)

val OnDark: Color get() = if (Palette.light) LightInk else Color(0xFFF4F6FB)
val OnDarkMuted: Color get() = if (Palette.light) LightInkMuted else Color(0xFF9AA3BD)

val CardTextPrimary: Color get() = if (Palette.light) LightInk else Color(0xFFF4F6FB)
val CardTextMuted: Color get() = if (Palette.light) LightInkMuted else Color(0xFF9AA3BD)
val CardTextDim: Color get() = if (Palette.light) Color(0xFF8891A5) else Color(0xFF5C6478)

private val LightVoid = Color(0xFFF4F5F8)
private val LightPanelTop = Color(0xD1FFFFFF)
private val LightPanelBottom = Color(0xF0F3F5F8)
private val LightCardSub = Color(0xE6FBFCFE)

val Void: Color get() = if (Palette.light) LightVoid else Color(0xFF04050B)
val PanelTop: Color get() = if (Palette.light) LightPanelTop else Color(0xAA14172B)
val PanelBottom: Color get() = if (Palette.light) LightPanelBottom else Color(0xE60B0D18)
val CardSubSurface: Color get() = if (Palette.light) LightCardSub else Color(0xDE141726)

val CardSurfaceTop: Color get() = PanelTop
val CardSurfaceBottom: Color get() = PanelBottom

val EdgeNeon: Color get() = if (Palette.light) Color(0x597C6BFF) else Color(0x267C6BFF)
val EdgeNeonBright: Color get() = if (Palette.light) Color(0x807C6BFF) else Color(0x407C6BFF)
val GlassEdge: Color get() = if (Palette.light) Color(0x28000000) else Color(0x2EFFFFFF)
val GlassEdgeBright: Color get() = if (Palette.light) Color(0x40000000) else Color(0x45FFFFFF)

val GlowPoolCyan: Color get() = if (Palette.light) Color(0xFFC5E9F7) else Color(0xFF14496B)
val GlowPoolViolet: Color get() = if (Palette.light) Color(0xFFD9D2F9) else Color(0xFF33286E)

val GlassFillTop: Color get() = if (Palette.light) Color(0x1A000000) else Color(0x2EFFFFFF)
val GlassFillBottom: Color get() = if (Palette.light) Color(0x0D000000) else Color(0x10FFFFFF)
val GlassSheenTop: Color get() = if (Palette.light) Color(0x15000000) else Color(0x26FFFFFF)

val DrawerGlass: Color get() = if (Palette.light) Color(0xF7F6F8FA) else Color(0xF5060710)
val SheetGlass: Color get() = if (Palette.light) Color(0xFAF6F8FA) else Color(0xFA080A12)
val ChipFillTop: Color get() = if (Palette.light) Color(0x14000000) else Color(0x24FFFFFF)
val ChipFillBottom: Color get() = if (Palette.light) Color(0x0A000000) else Color(0x12FFFFFF)

fun latencyColor(ms: Long): Color = when {
    ms < 0 -> CardTextDim
    ms < 90 -> NeonMint
    ms < 220 -> NeonAmber
    else -> NeonRed
}
