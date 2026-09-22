package com.soildtunnel.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soildtunnel.app.R
import com.soildtunnel.app.ui.theme.CardSubSurface

enum class ButtonMode { IDLE, BUSY, CONNECTED, ERROR }

@Composable
fun ConnectButton(
    mode: ButtonMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (mode) {
        ButtonMode.IDLE -> NeonTokens.idle
        ButtonMode.BUSY -> NeonTokens.busy
        ButtonMode.CONNECTED -> NeonTokens.connected
        ButtonMode.ERROR -> NeonTokens.error
    }
    val animatedAccent by animateColorAsState(accent, tween(600), label = "accent")

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(150),
        label = "pressScale",
    )

    val breathe = if (mode == ButtonMode.CONNECTED) {
        val t = rememberInfiniteTransition(label = "btnBreathe")
        t.animateFloat(
            initialValue = 1f,
            targetValue = 1.025f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathe",
        ).value
    } else 1f

    val label = when (mode) {
        ButtonMode.IDLE -> stringResource(R.string.tap_to_connect)
        ButtonMode.BUSY -> stringResource(R.string.state_connecting)
        ButtonMode.CONNECTED -> stringResource(R.string.tap_to_disconnect)
        ButtonMode.ERROR -> stringResource(R.string.state_error)
    }
    val glyph: ImageVector = when (mode) {
        ButtonMode.BUSY -> Icons.Rounded.Autorenew
        ButtonMode.CONNECTED -> Icons.Rounded.Check
        else -> Icons.Rounded.PowerSettingsNew
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale * breathe),
    ) {
        Box(
            modifier = Modifier
                .width(PILL_WIDTH)
                .height(PILL_HEIGHT)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(PILL_HEIGHT / 2),
                    ambientColor = animatedAccent.copy(alpha = 0.45f),
                    spotColor = animatedAccent.copy(alpha = 0.45f),
                )
                .background(color = CardSubSurface, shape = RoundedCornerShape(PILL_HEIGHT / 2))
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = 0.15f),
                            animatedAccent.copy(alpha = 0.75f),
                            animatedAccent.copy(alpha = 0.15f),
                        ),
                    ),
                    shape = RoundedCornerShape(PILL_HEIGHT / 2),
                )
                .clip(RoundedCornerShape(PILL_HEIGHT / 2))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (mode == ButtonMode.IDLE) {
                ShineSweep(accent = animatedAccent)
            }
            if (mode == ButtonMode.ERROR) {
                ErrorWash()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = animatedAccent.copy(alpha = 0.16f),
                            shape = CircleShape,
                        )
                        .border(
                            1.dp,
                            animatedAccent.copy(alpha = 0.5f),
                            CircleShape,
                        ),
                ) {
                    if (mode == ButtonMode.BUSY) {
                        SpinningGlyph(glyph, animatedAccent)
                    } else {
                        Icon(
                            imageVector = glyph,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(
                            color = animatedAccent.copy(
                                alpha = if (mode == ButtonMode.CONNECTED) 0.5f else 0.25f,
                            ),
                            blurRadius = 12f,
                        ),
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ShineSweep(accent: Color) {
    val shift = rememberInfiniteTransition(label = "btnShine").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing)),
        label = "shift",
    )
    Box(
        modifier = Modifier
            .width(PILL_WIDTH)
            .height(PILL_HEIGHT)
            .offset(x = (shift.value * PILL_WIDTH.value).dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.14f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun ErrorWash() {
    val blink = rememberInfiniteTransition(label = "btnErrorWash").animateFloat(
        initialValue = 0.04f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blink",
    )
    Box(
        modifier = Modifier
            .width(PILL_WIDTH)
            .height(PILL_HEIGHT)
            .background(color = NeonTokens.error.copy(alpha = blink.value)),
    )
}

@Composable
private fun SpinningGlyph(glyph: ImageVector, tint: Color) {
    val rotation = rememberInfiniteTransition(label = "btnSpin").animateFloat(
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
            .size(20.dp)
            .rotate(rotation.value),
    )
}

private object NeonTokens {
    val idle = Color(0xFF35E0FF)
    val busy = Color(0xFF35E0FF)
    val connected = Color(0xFF3DFFC8)
    val error = Color(0xFFFF4D6F)
}

private val PILL_WIDTH = 228.dp
private val PILL_HEIGHT = 68.dp
