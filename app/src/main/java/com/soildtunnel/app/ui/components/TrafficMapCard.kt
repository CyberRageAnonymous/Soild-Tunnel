package com.soildtunnel.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.HevTunnel
import com.soildtunnel.app.core.NetProbe
import com.soildtunnel.app.core.ShareBridge
import com.soildtunnel.app.core.SoildTunnelController
import com.soildtunnel.app.core.SocksTunBridge
import com.soildtunnel.app.ui.theme.NeonAmber
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.PanelRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

/**
 * The route drawn on a world outline, with packets crawling along it at the
 * rate the tunnel is actually moving.
 *
 * The outline is pre-simplified data (WorldMapData), so a frame costs a
 * handful of draw calls rather than a parse. The packet dots are decorative:
 * their density follows the measured throughput, and nothing here feeds
 * back into the connection.
 */
@Composable
fun TrafficMapCard(
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val ipInfo by SoildTunnelController.ipInfo.collectAsState()
    var homeCountry by remember { mutableStateOf<String?>(null) }
    var downRate by remember { mutableStateOf(0L) }
    var upRate by remember { mutableStateOf(0L) }

    // The badge only ever shows one address at a time, so while the tunnel is
    // up the operator's own country has to be looked up separately — once.
    LaunchedEffect(connected) {
        if (!connected) {
            homeCountry = ipInfo?.takeIf { !it.viaTunnel }?.countryCode
            return@LaunchedEffect
        }
        if (homeCountry == null) {
            homeCountry = withContext(Dispatchers.IO) {
                runCatching { NetProbe.fetchIpInfoDirect(6_000)?.countryCode }.getOrNull()
            }
        }
    }

    val exitCountry = ipInfo?.takeIf { it.viaTunnel }?.countryCode
    val idle = remember(connected, exitCountry, homeCountry) {
        !connected || exitCountry.isNullOrBlank() || homeCountry.isNullOrBlank()
    }

    LaunchedEffect(connected) {
        if (!connected) {
            downRate = 0L
            upRate = 0L
            return@LaunchedEffect
        }
        var lastDown = -1L
        var lastUp = -1L
        var lastAt = 0L
        while (true) {
            val hev = HevTunnel.traffic()
            val share = ShareBridge.traffic()
            val bridge = SocksTunBridge.active?.getStats()
            if (hev != null || bridge != null) {
                val down = (hev?.downloadBytes ?: 0L) + share.downloadBytes + (bridge?.rxBytes ?: 0L)
                val up = (hev?.uploadBytes ?: 0L) + share.uploadBytes + (bridge?.txBytes ?: 0L)
                val at = android.os.SystemClock.elapsedRealtime()
                if (lastAt > 0L && at > lastAt && lastDown >= 0L) {
                    val dt = at - lastAt
                    downRate = ((down - lastDown).coerceAtLeast(0L) * 1000L) / dt
                    upRate = ((up - lastUp).coerceAtLeast(0L) * 1000L) / dt
                }
                lastDown = down
                lastUp = up
                lastAt = at
            }
            kotlinx.coroutines.delay(1_000L)
        }
    }

    val travel = rememberInfiniteTransition(label = "routeFlow")
    val phase by travel.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "routePhase",
    )
    val pulse by travel.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
        label = "routePulse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonPanel(RoundedCornerShape(PanelRadius), edge = NeonMint.copy(alpha = 0.40f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = NeonMint,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.traffic_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (connected && !idle) {
                Row {
                    Text(
                        text = stringResource(R.string.traffic_down, formatRate(downRate)),
                        style = MaterialTheme.typography.labelMedium.copy(
                            textDirection = TextDirection.Ltr,
                            fontFeatureSettings = "tnum",
                        ),
                        fontWeight = FontWeight.Bold,
                        color = NeonMint,
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = stringResource(R.string.traffic_up, formatRate(upRate)),
                        style = MaterialTheme.typography.labelMedium.copy(
                            textDirection = TextDirection.Ltr,
                            fontFeatureSettings = "tnum",
                        ),
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp),
        ) {
            val scale = min(size.width / 360f, size.height / 180f)
            val originX = (size.width - 360f * scale) / 2f
            val originY = (size.height - 180f * scale) / 2f

            drawOutline(scale, originX, originY)

            val from = homeCountry?.let { WORLD_CENTERS[it.uppercase(Locale.US)] }
            val to = exitCountry?.let { WORLD_CENTERS[it.uppercase(Locale.US)] }
            val start = from?.let { Offset(originX + (it.first + 180f) * scale, originY + (90f - it.second) * scale) }
            val end = to?.let { Offset(originX + (it.first + 180f) * scale, originY + (90f - it.second) * scale) }

            if (start != null && end != null && !idle) {
                val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                val dx = end.x - start.x
                val dy = end.y - start.y
                val bend = Offset(mid.x - dy * 0.18f, mid.y + dx * 0.18f)

                val route = Path().apply {
                    moveTo(start.x, start.y)
                    // Drawn as segments instead of a curve call so the arc
                    // renders the same on every Compose version in the wild.
                    for (step in 1..ROUTE_SEGMENTS) {
                        val point = quad(start, bend, end, step / ROUTE_SEGMENTS.toFloat())
                        lineTo(point.x, point.y)
                    }
                }
                drawPath(route, color = NeonMint.copy(alpha = 0.5f), style = Stroke(1.6f * density))

                val packets = packetCount(downRate + upRate)
                repeat(packets) { index ->
                    val offset = (phase + index.toFloat() / packets) % 1f
                    val point = quad(start, bend, end, offset)
                    drawCircle(
                        color = NeonMint.copy(alpha = 0.25f + 0.75f * (1f - offset)),
                        radius = 3f * density,
                        center = point,
                    )
                }
                drawMarker(start, NeonAmber, pulse, hollow = true)
                drawMarker(end, NeonMint, pulse, hollow = false)
            } else if (start != null) {
                drawMarker(start, NeonAmber, pulse, hollow = true)
            } else if (end != null) {
                drawMarker(end, NeonMint, pulse, hollow = false)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (idle) {
                stringResource(R.string.traffic_idle)
            } else {
                stringResource(
                    R.string.traffic_route,
                    NetProbe.flagEmoji(homeCountry) + " " + (homeCountry ?: "?"),
                    NetProbe.flagEmoji(exitCountry) + " " + (exitCountry ?: "?"),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fills the cached outline every frame; cheap enough at this point count. */
private fun DrawScope.drawOutline(scale: Float, originX: Float, originY: Float) {
    val path = Path()
    var cursor = 0
    for (length in WORLD_RING_LENGTHS) {
        var point = 0
        while (point < length) {
            val lon = WORLD_POINTS[cursor]
            val lat = WORLD_POINTS[cursor + 1]
            cursor += 2
            val x = originX + (lon + 180f) * scale
            val y = originY + (90f - lat) * scale
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
            point++
        }
    }
    drawPath(path, color = landColor(), style = Stroke(1.1f * density))
}

private fun DrawScope.landColor(): Color = NeonMint.copy(alpha = 0.28f)

private fun DrawScope.drawMarker(center: Offset, color: Color, pulse: Float, hollow: Boolean) {
    val ring = (4f + pulse * 7f) * density
    drawCircle(color.copy(alpha = (1f - pulse) * 0.55f), radius = ring, center = center)
    if (hollow) {
        drawCircle(color, radius = 3.4f * density, center = center, style = Stroke(1.6f * density))
    } else {
        drawCircle(color, radius = 3.6f * density, center = center)
    }
}

private fun quad(start: Offset, control: Offset, end: Offset, t: Float): Offset {
    val inv = 1f - t
    val x = inv * inv * start.x + 2f * inv * t * control.x + t * t * end.x
    val y = inv * inv * start.y + 2f * inv * t * control.y + t * t * end.y
    return Offset(x, y)
}

/** A quiet tunnel draws nothing; a busy one fills the arc with dots. */
private fun packetCount(bytesPerSecond: Long): Int = when {
    bytesPerSecond <= 0L -> 0
    bytesPerSecond < 64_000L -> 2
    bytesPerSecond < 512_000L -> 4
    bytesPerSecond < 4_000_000L -> 6
    else -> 9
}

private fun formatRate(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private const val ROUTE_SEGMENTS = 24
