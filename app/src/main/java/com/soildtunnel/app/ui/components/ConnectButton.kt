package com.soildtunnel.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soildtunnel.app.R
import com.soildtunnel.app.ui.theme.AuraIndigo
import com.soildtunnel.app.ui.theme.CardSubSurface
import com.soildtunnel.app.ui.theme.CardTextDim
import com.soildtunnel.app.ui.theme.NeonCyan
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.NeonRed

enum class ButtonMode { IDLE, BUSY, CONNECTED, ERROR }

@Composable
fun ConnectButton(
    mode: ButtonMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (mode) {
        ButtonMode.IDLE -> NeonCyan
        ButtonMode.BUSY -> AuraIndigo
        ButtonMode.CONNECTED -> NeonMint
        ButtonMode.ERROR -> NeonRed
    }
    val animatedAccent by animateColorAsState(accent, tween(600), label = "accent")

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(150),
        label = "pressScale",
    )

    val breathe = if (mode == ButtonMode.CONNECTED) {
        rememberInfiniteTransition(label = "orbBreathe").animateFloat(
            initialValue = 1f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathe",
        )
    } else null

    val label = when (mode) {
        ButtonMode.IDLE -> stringResource(R.string.tap_to_connect)
        ButtonMode.BUSY -> stringResource(R.string.state_connecting)
        ButtonMode.CONNECTED -> stringResource(R.string.tap_to_disconnect)
        ButtonMode.ERROR -> stringResource(R.string.state_error)
    }
    val hint = when (mode) {
        ButtonMode.IDLE -> stringResource(R.string.btn_hint_idle)
        ButtonMode.BUSY -> stringResource(R.string.btn_hint_busy)
        ButtonMode.CONNECTED -> stringResource(R.string.btn_hint_connected)
        ButtonMode.ERROR -> stringResource(R.string.btn_hint_error)
    }
    val glyph: ImageVector = when (mode) {
        ButtonMode.CONNECTED -> Icons.Rounded.Check
        else -> Icons.Rounded.PowerSettingsNew
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale * (breathe?.value ?: 1f)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ORB_EXT),
        ) {
            OrbHalo(animatedAccent, strong = mode == ButtonMode.CONNECTED)
            if (mode == ButtonMode.CONNECTED) {
                PulseRings(animatedAccent)
            }
            OrbRing(animatedAccent, spinning = mode == ButtonMode.BUSY)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ORB_IN)
                    .shadow(
                        elevation = 26.dp,
                        shape = CircleShape,
                        ambientColor = animatedAccent.copy(alpha = 0.50f),
                        spotColor = animatedAccent.copy(alpha = 0.50f),
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                animatedAccent.copy(alpha = 0.20f),
                                CardSubSurface,
                            ),
                        ),
                        shape = CircleShape,
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.White.copy(alpha = 0.22f),
                                0.18f to Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.04f),
                            ),
                        ),
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    ),
            ) {
                if (mode == ButtonMode.BUSY) {
                    SpinningGlyph(Icons.Rounded.Autorenew, animatedAccent)
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(58.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        animatedAccent.copy(alpha = 0.26f),
                                        animatedAccent.copy(alpha = 0.10f),
                                    ),
                                ),
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = glyph,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                OrbStatusDot(mode, animatedAccent, Modifier.align(Alignment.BottomCenter))
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (mode == ButtonMode.CONNECTED) {
                Spacer(Modifier.width(8.dp))
                EqualizerBars(animatedAccent)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = CardTextDim,
        )
    }
}

@Composable
private fun OrbHalo(accent: Color, strong: Boolean) {
    Canvas(Modifier.size(ORB_EXT)) {
        val r = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    accent.copy(alpha = if (strong) 0.30f else 0.15f),
                    Color.Transparent,
                ),
                radius = r,
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                radius = r * 0.6f,
                center = Offset(size.width * 0.26f, size.height * 0.24f),
            ),
        )
    }
}

@Composable
private fun PulseRings(accent: Color) {
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing)),
        label = "pulseT",
    )
    for (i in 0 until 2) {
        val p = (t + i * 0.5f) % 1f
        Box(
            modifier = Modifier
                .size(ORB_IN)
                .graphicsLayer {
                    scaleX = 1f + p * 0.42f
                    scaleY = 1f + p * 0.42f
                    alpha = (1f - p) * 0.30f
                }
                .border(1.5.dp, accent, CircleShape),
        )
    }
}

@Composable
private fun OrbRing(accent: Color, spinning: Boolean) {
    val spinT by rememberInfiniteTransition(label = "orbSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (spinning) 1_300 else 9_000, easing = LinearEasing),
        ),
        label = "spinT",
    )
    val gap = if (spinning) 0.70f else 0.30f
    Box(
        modifier = Modifier
            .size(ORB_RING)
            .rotate(spinT),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - RING_STROKE.toPx() / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    gap / 2f to accent.copy(alpha = 0.12f),
                    0.5f to accent,
                    0.5f + (1f - gap) / 2f to accent.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                ),
                radius = r,
                center = c,
                style = Stroke(RING_STROKE.toPx()),
            )
        }
    }
}

@Composable
private fun OrbStatusDot(
    mode: ButtonMode,
    accent: Color,
    modifier: Modifier,
) {
    val dot by rememberInfiniteTransition(label = "orbDot").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot",
    )
    val color = when (mode) {
        ButtonMode.CONNECTED, ButtonMode.ERROR -> accent
        else -> accent.copy(alpha = 0.35f + 0.65f * dot)
    }
    Box(
        modifier = modifier
            .offset(y = 9.dp),
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.65f), Color.Transparent),
                    radius = size.minDimension / 2f,
                ),
            )
            drawCircle(color, radius = size.minDimension * 0.30f)
        }
    }
}

@Composable
private fun EqualizerBars(tint: Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    val durations = intArrayOf(480, 620, 760)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(14.dp),
    ) {
        for (i in 0 until 3) {
            val h by transition.animateFloat(
                initialValue = 5f,
                targetValue = 13f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durations[i], easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eqBar$i",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .background(tint, CircleShape),
            )
        }
    }
}

@Composable
private fun SpinningGlyph(glyph: ImageVector, tint: Color) {
    val rotation = rememberInfiniteTransition(label = "orbGlyphSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "spin",
    )
    Icon(
        imageVector = glyph,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(26.dp)
            .rotate(rotation.value),
    )
}

private val ORB_EXT = 196.dp
private val ORB_RING = 176.dp
private val ORB_IN = 160.dp
private val RING_STROKE = 4.dp
