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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.ui.theme.GlowPoolViolet
import com.soildtunnel.app.ui.theme.GridLine
import com.soildtunnel.app.ui.theme.Void
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientBackground(
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryGlow = if (active) ACTIVE_GLOW else accent
    val drift = rememberInfiniteTransition(label = "ambientDrift").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "drift",
    )
    val sway = rememberInfiniteTransition(label = "ambientSway").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(34_000, easing = LinearEasing)),
        label = "sway",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Void)
                drawGrid()
                val t = drift.value
                val s = sway.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(
                            size.width * (0.12f + 0.10f * cos(t).toFloat()),
                            size.height * (0.08f + 0.06f * sin(t * 1.3f).toFloat()),
                        ),
                        radius = size.maxDimension * 0.95f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(VIOLET_GLOW.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(
                            size.width * (0.92f + 0.08f * sin(s).toFloat()),
                            size.height * (0.88f + 0.06f * cos(s * 0.8f).toFloat()),
                        ),
                        radius = size.maxDimension * 0.85f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(MINT_GLOW.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(
                            size.width * (0.80f + 0.12f * cos(s * 1.1f).toFloat()),
                            size.height * (0.18f + 0.08f * sin(t).toFloat()),
                        ),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryGlow.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        startY = size.height * 0.30f,
                        endY = size.height * 0.62f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
    )
}

/** Blueprint grid: hairline verticals + horizontals on a fixed pitch. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid() {
    val step = GRID_STEP.toPx()
    if (step <= 0f) return
    val color = GridLine
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

private val ACTIVE_GLOW = Color(0xFF2BE8C0)
private val VIOLET_GLOW = GlowPoolViolet
private val MINT_GLOW = Color(0xFF3DFFC8)
private val GRID_STEP = 44.dp
