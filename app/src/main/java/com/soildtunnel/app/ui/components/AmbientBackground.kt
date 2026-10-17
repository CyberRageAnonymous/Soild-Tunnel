package com.soildtunnel.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.soildtunnel.app.ui.theme.AuraIndigo
import com.soildtunnel.app.ui.theme.GlowPoolCyan
import com.soildtunnel.app.ui.theme.GlowPoolViolet
import com.soildtunnel.app.ui.theme.NeonBusy
import com.soildtunnel.app.ui.theme.NeonOk
import com.soildtunnel.app.ui.theme.NeonPrimary
import com.soildtunnel.app.ui.theme.Palette
import com.soildtunnel.app.ui.theme.Skin
import com.soildtunnel.app.ui.theme.Void
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientBackground(
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Palette.skin == Skin.NEON) {
        NeonBackdrop(accent, active, modifier)
        return
    }

    val drift = rememberInfiniteTransition(label = "auroraDrift").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "drift",
    )
    val sway = rememberInfiniteTransition(label = "auroraSway").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(31_000, easing = LinearEasing)),
        label = "sway",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Void)
                val t = drift.value
                val s = sway.value
                val liveAmp = if (active) 1.35f else 1f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AuraIndigo.copy(alpha = (if (active) 0.26f else 0.20f) * liveAmp),
                            Color.Transparent,
                        ),
                        center = Offset(
                            size.width * (0.10f + 0.08f * cos(t).toFloat()),
                            size.height * (0.05f + 0.05f * sin(t * 1.2f).toFloat()),
                        ),
                        radius = size.maxDimension * 1.60f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GlowPoolViolet.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(
                            size.width * (0.95f + 0.07f * sin(s).toFloat()),
                            size.height * (0.42f + 0.10f * cos(s * 0.9f).toFloat()),
                        ),
                        radius = size.maxDimension * 1.50f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GlowPoolCyan.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                        center = Offset(
                            size.width * (0.85f + 0.10f * cos(s * 1.1f).toFloat()),
                            size.height * (0.90f + 0.06f * sin(t).toFloat()),
                        ),
                        radius = size.maxDimension * 1.35f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = if (active) 0.10f else 0.05f),
                            Color.Transparent,
                        ),
                        startY = size.height * 0.34f,
                        endY = size.height * 0.66f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)),
                        radius = size.maxDimension * 1.30f,
                    ),
                )
            },
    )
}

@Composable
private fun NeonBackdrop(
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val grid = rememberInfiniteTransition(label = "neonGrid").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "grid",
    )
    val pulse = rememberInfiniteTransition(label = "neonPulse").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Void)
                val live = if (active) 1.45f else 1f
                val p = pulse.value

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonOk.copy(alpha = (if (active) 0.34f else 0.22f) * live),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 1.06f),
                        radius = size.maxDimension * 1.00f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonBusy.copy(alpha = (if (active) 0.20f else 0.13f) * live),
                            Color.Transparent,
                        ),
                        center = Offset(
                            size.width * (0.14f + 0.03f * sin(p).toFloat()),
                            size.height * (0.10f + 0.02f * cos(p).toFloat()),
                        ),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonPrimary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.26f),
                        radius = size.maxDimension * 0.45f,
                    ),
                )

                val stepX = size.width / 15f
                var gx = (grid.value * stepX) - stepX
                while (gx < size.width) {
                    drawRect(
                        color = NeonPrimary.copy(alpha = 0.045f),
                        topLeft = Offset(gx, 0f),
                        size = Size(1.5f, size.height),
                    )
                    gx += stepX
                }

                val pulseAlpha = (if (active) 0.55f else 0.22f) * (0.6f + 0.4f * abs(sin(p)).toFloat())
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonBusy.copy(alpha = pulseAlpha),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 2.5f),
                )

                val horizonTop = size.height * 0.74f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = (if (active) 0.24f else 0.12f) * live),
                            Color.Transparent,
                        ),
                        startY = horizonTop - size.height * 0.06f,
                        endY = horizonTop + size.height * 0.06f,
                    ),
                )
                drawRect(
                    color = NeonPrimary.copy(alpha = 0.42f * live),
                    topLeft = Offset(0f, size.height * 0.80f - 1f),
                    size = Size(size.width, 2f),
                )
                drawRect(
                    color = NeonBusy.copy(alpha = 0.30f * live),
                    topLeft = Offset(0f, size.height * 0.80f - 4f),
                    size = Size(size.width, 1.5f),
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        radius = size.maxDimension * 1.05f,
                    ),
                )
            },
    )
}
