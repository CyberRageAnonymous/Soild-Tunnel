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
import com.soildtunnel.app.ui.theme.NeonOk
import com.soildtunnel.app.ui.theme.NeonPrimary
import com.soildtunnel.app.ui.theme.NeonTertiary
import com.soildtunnel.app.ui.theme.Palette
import com.soildtunnel.app.ui.theme.Skin
import com.soildtunnel.app.ui.theme.Void
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
    val scan = rememberInfiniteTransition(label = "neonScan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "scan",
    )
    val shimmer = rememberInfiniteTransition(label = "neonPulse").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)),
        label = "shimmer",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Void)
                val live = if (active) 1.40f else 1f
                val sh = shimmer.value

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonTertiary.copy(alpha = 0.14f * live),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.08f, size.height * 0.80f),
                        radius = size.maxDimension * 0.70f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonPrimary.copy(alpha = (if (active) 0.22f else 0.15f) * live),
                            Color.Transparent,
                        ),
                        center = Offset(
                            size.width * 0.24f,
                            size.height * (0.13f + 0.02f * cos(sh).toFloat()),
                        ),
                        radius = size.maxDimension * 0.78f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonOk.copy(alpha = 0.09f * live),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.90f),
                        radius = size.maxDimension * 0.52f,
                    ),
                )

                val step = size.height / 46f
                var sy = (scan.value * step) - step
                while (sy < size.height) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.028f),
                        topLeft = Offset(0f, sy),
                        size = Size(size.width, 1.5f),
                    )
                    sy += step
                }

                val horizonTop = size.height * 0.55f
                val horizonBottom = size.height * 0.67f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = (if (active) 0.26f else 0.13f) * live),
                            Color.Transparent,
                        ),
                        startY = horizonTop,
                        endY = horizonBottom,
                    ),
                )
                drawRect(
                    color = NeonPrimary.copy(alpha = 0.38f * live),
                    topLeft = Offset(0f, size.height * 0.61f - 1f),
                    size = Size(size.width, 2f),
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
