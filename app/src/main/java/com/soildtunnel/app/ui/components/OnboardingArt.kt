package com.soildtunnel.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.ui.theme.AuraIndigo
import com.soildtunnel.app.ui.theme.EdgeNeon
import com.soildtunnel.app.ui.theme.NeonCyan
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.NeonRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OnboardingHero(page: Int, modifier: Modifier = Modifier) {
    when (page) {
        0 -> ShieldCore(modifier)
        1 -> LadderSignal(modifier)
        else -> PrivacyVeil(modifier)
    }
}

@Composable
private fun ShieldCore(modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "obSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing)),
        label = "spin",
    )
    val pulse = rememberInfiniteTransition(label = "obPulse").animateFloat(
        initialValue = 0.84f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f

        rotate(spin.value, pivot = c) {
            val arcR = r * 0.88f
            var a = 0f
            while (a < 360f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(NeonCyan, AuraIndigo, NeonCyan)),
                    startAngle = a,
                    sweepAngle = 42f,
                    useCenter = false,
                    topLeft = Offset(c.x - arcR, c.y - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
                a += 76f
            }
        }

        drawCircle(
            color = EdgeNeon,
            radius = r * 0.70f,
            center = c,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AuraIndigo.copy(alpha = 0.20f), Color.Transparent),
                center = c,
                radius = r * 0.70f,
            ),
            radius = r * 0.70f,
            center = c,
        )

        val hexR = r * 0.44f * pulse.value
        val hex = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians((60.0 * i - 30.0))
            val x = c.x + hexR * cos(angle).toFloat()
            val y = c.y + hexR * sin(angle).toFloat()
            if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
        drawPath(
            path = hex,
            brush = Brush.radialGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.34f), AuraIndigo.copy(alpha = 0.12f), Color.Transparent),
                center = c,
                radius = hexR,
            ),
        )
        drawPath(path = hex, color = NeonCyan, style = Stroke(width = 2.dp.toPx()))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.60f), Color.Transparent),
                center = c,
                radius = hexR * 0.85f,
            ),
        )
        drawCircle(color = Color.White.copy(alpha = 0.88f), radius = hexR * 0.11f, center = c)
    }
}

@Composable
private fun LadderSignal(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "obLadder")
    val bars = listOf(0, 1, 2, 3).map { index ->
        transition.animateFloat(
            initialValue = 0.22f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900 + index * 260, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$index",
        )
    }
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_400, easing = LinearEasing)),
        label = "sweep",
    )
    Canvas(modifier) {
        val count = 4
        val gap = 12.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val baseline = size.height * 0.82f
        val top = size.height * 0.16f
        for (i in 0 until count) {
            val value = bars[i].value
            val barHeight = (baseline - top) * (0.34f + 0.66f * (i + 1) / count) * value
            val left = i * (barWidth + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(NeonCyan.copy(alpha = 0.95f), AuraIndigo.copy(alpha = 0.35f)),
                ),
                topLeft = Offset(left, baseline - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
            drawCircle(
                color = NeonCyan.copy(alpha = 0.85f),
                radius = barWidth * 0.16f,
                center = Offset(left + barWidth / 2f, baseline - barHeight),
            )
        }
        drawLine(
            color = EdgeNeon.copy(alpha = 0.35f),
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )
        val sweepX = sweep.value * size.width
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, NeonMint.copy(alpha = 0.55f), Color.Transparent),
            ),
            start = Offset(sweepX - 26.dp.toPx(), top - 8.dp.toPx()),
            end = Offset(sweepX + 26.dp.toPx(), baseline + 8.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun PrivacyVeil(modifier: Modifier = Modifier) {
    val scan = rememberInfiniteTransition(label = "obScan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing)),
        label = "scan",
    )
    val blink = rememberInfiniteTransition(label = "obBlink").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blink",
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val eyeW = size.minDimension * 0.78f
        val eyeH = eyeW * 0.52f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonMint.copy(alpha = 0.16f * blink.value), Color.Transparent),
                center = c,
                radius = size.minDimension * 0.52f,
            ),
            radius = size.minDimension * 0.52f,
            center = c,
        )

        val eye = Path().apply {
            moveTo(c.x - eyeW / 2f, c.y)
            quadraticBezierTo(c.x, c.y - eyeH, c.x + eyeW / 2f, c.y)
            quadraticBezierTo(c.x, c.y + eyeH, c.x - eyeW / 2f, c.y)
            close()
        }
        drawPath(path = eye, color = NeonMint, style = Stroke(width = 2.5.dp.toPx()))
        drawCircle(
            color = NeonMint.copy(alpha = 0.85f * blink.value),
            radius = eyeH * 0.24f,
            center = c,
        )

        val slash = size.minDimension * 0.62f
        drawLine(
            color = NeonRed,
            start = Offset(c.x - slash / 2f, c.y + slash / 2f),
            end = Offset(c.x + slash / 2f, c.y - slash / 2f),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val lineY = c.y - eyeH * 1.15f + scan.value * (eyeH * 2.3f)
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, NeonCyan.copy(alpha = 0.45f), Color.Transparent),
            ),
            start = Offset(c.x - eyeW * 0.62f, lineY),
            end = Offset(c.x + eyeW * 0.62f, lineY),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
fun OnboardingProgress(
    page: Int,
    pages: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.height(6.dp),
    ) {
        for (index in 0 until pages) {
            val on = index == page
            Box(
                modifier = Modifier
                    .width(if (on) 34.dp else 16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        brush = if (on) {
                            Brush.horizontalGradient(listOf(NeonCyan, AuraIndigo))
                        } else {
                            Brush.horizontalGradient(listOf(EdgeNeon, EdgeNeon))
                        },
                    ),
            )
        }
    }
}
