package com.soildtunnel.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay
import com.soildtunnel.app.R
import com.soildtunnel.app.core.EngineMeta
import com.soildtunnel.app.core.TunnelConfig
import com.soildtunnel.app.core.HevTunnel
import com.soildtunnel.app.core.SocksTunBridge
import com.soildtunnel.app.core.IpEndpoint
import com.soildtunnel.app.core.NetProbe
import com.soildtunnel.app.core.PingMonitor
import com.soildtunnel.app.core.ShareBridge
import com.soildtunnel.app.ui.theme.CardSubSurface
import com.soildtunnel.app.ui.theme.CardTextDim
import com.soildtunnel.app.ui.theme.CardTextMuted
import com.soildtunnel.app.ui.theme.CardTextPrimary
import com.soildtunnel.app.ui.theme.NeonCyan
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.NeonRed
import com.soildtunnel.app.ui.theme.latencyColor

/** Telemetry console — state, timer, IP, speeds, protocol info. */
@Composable
fun ConnectionCard(
    connected: Boolean,
    statusTitle: String,
    statusCaption: String,
    connectedSince: Long?,
    ipInfo: IpEndpoint?,
    ipLoading: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
    // Tor mode listens on its own loopback port, not the engine's.
    socksPort: Int = TunnelConfig.SOCKS_PORT,
) {
    val accent = when {
        error -> ERROR_ACCENT
        connected -> NeonMint
        else -> IDLE_ACCENT
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .neonPanel(CARD_SHAPE, edge = accent.copy(alpha = 0.45f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                    ),
                    shape = CARD_SHAPE,
                ),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConsoleHeader(connected = connected, error = error)
            StatusBlock(title = statusTitle, caption = statusCaption, accent = accent)
            SectionDivider()
            TimerBlock(connectedSince = connectedSince, connected = connected)
            ServerIpPill(connected = connected, ipInfo = ipInfo, ipLoading = ipLoading)
            SectionDivider()
            SpeedStrip(connectedSince = connectedSince, connected = connected)
            ProtocolStrip(connected = connected, socksPort = socksPort)
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(0.92f),
        thickness = 1.dp,
        color = Color(0x1435E0FF),
    )
}

// 0. header

@Composable
private fun ConsoleHeader(connected: Boolean, error: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedDot(
            color = when {
                error -> NeonRed
                connected -> NeonMint
                else -> NeonCyan
            },
            glowing = true,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.console_label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            ),
            color = CardTextDim,
        )
    }
}

// 1. status

@Composable
private fun StatusBlock(title: String, caption: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cardStatus",
        ) { value ->
            Text(
                text = value,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = accent,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = accent.copy(alpha = 0.55f),
                        blurRadius = 18f,
                    ),
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = caption,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cardCaption",
        ) { value ->
            Text(
                text = value,
                fontSize = 13.sp,
                color = CardTextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// 2. timer

@Composable
private fun TimerBlock(connectedSince: Long?, connected: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedSince) {
        if (connectedSince == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val elapsed = if (connectedSince == null) 0L else (now - connectedSince).coerceAtLeast(0L) / 1000L
    val text = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        elapsed / 3600,
        (elapsed % 3600) / 60,
        elapsed % 60,
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.connected_for),
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            fontFamily = FontFamily.Monospace,
            color = CardTextDim,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 38.sp,
            letterSpacing = 2.sp,
            color = if (connected) CardTextPrimary else CardTextDim,
            style = if (connected) {
                MaterialTheme.typography.headlineLarge.copy(
                    shadow = Shadow(
                        color = NeonCyan.copy(alpha = 0.35f),
                        blurRadius = 16f,
                    ),
                )
            } else {
                MaterialTheme.typography.headlineLarge
            },
        )
    }
}

// 3. server IP

@Composable
private fun ServerIpPill(connected: Boolean, ipInfo: IpEndpoint?, ipLoading: Boolean) {
    val label = stringResource(
        if (connected) R.string.ip_server_label else R.string.ip_your_label,
    )
    val flag = NetProbe.flagEmoji(ipInfo?.countryCode)
    val country = if (connected && ipInfo?.viaTunnel == true) NetProbe.countryName(ipInfo.countryCode) else ""
    val value = when {
        ipLoading && ipInfo == null -> stringResource(R.string.ip_checking)
        ipInfo != null -> ipInfo.ip
        else -> stringResource(R.string.ip_unavailable)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Text(text = label, fontSize = 12.sp, color = CardTextMuted)
        if (ipInfo != null) {
            Text(text = flag, fontSize = 15.sp)
        }
        if (country.isNotEmpty()) {
            Text(text = country, fontSize = 11.sp, color = CardTextMuted)
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cardIp",
        ) { shown ->
            Text(
                text = shown,
                // BiDi: an address is LTR technical text even in the Persian UI.
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = CardTextPrimary,
            )
        }
    }
}

// 4. speeds

@Composable
private fun SpeedStrip(connectedSince: Long?, connected: Boolean) {
    val stats = rememberTrafficStats(connectedSince = connectedSince, connected = connected)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sum = (stats.downRate + stats.upRate).coerceAtLeast(1L)
        SpeedCell(
            icon = Icons.Rounded.ArrowDownward,
            tint = NeonMint,
            label = stringResource(R.string.traffic_download),
            rate = stats.downRate,
            total = stats.downTotal,
            fraction = stats.downRate.toFloat() / sum,
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        SpeedCell(
            icon = Icons.Rounded.ArrowUpward,
            tint = NeonCyan,
            label = stringResource(R.string.traffic_upload),
            rate = stats.upRate,
            total = stats.upTotal,
            fraction = stats.upRate.toFloat() / sum,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedCell(
    icon: ImageVector,
    tint: Color,
    label: String,
    rate: Long,
    total: Long,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "speedShare",
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.background(color = tint.copy(alpha = 0.12f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .padding(5.dp)
                    .size(15.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 9.dp)) {
            Text(
                text = formatRate(rate),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = CardTextPrimary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.traffic_total, formatBytes(total)),
                fontSize = 10.sp,
                color = CardTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(color = tint.copy(alpha = 0.14f), shape = CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .height(3.dp)
                        .background(color = tint, shape = CircleShape),
                )
            }
        }
    }
}

// 5. protocol

@Composable
private fun ProtocolStrip(connected: Boolean, socksPort: Int) {
    val meta by EngineMeta.state.collectAsState()
    val ping by PingMonitor.state.collectAsState()

    // Live latency, exactly like the desktop edition: one cheap TCP handshake
    // through the tunnel every few seconds, serialised by PingMonitor.
    LaunchedEffect(connected) {
        while (connected) {
            PingMonitor.pingOnce(viaTunnel = true, socksPort = socksPort)
            delay(LATENCY_REFRESH_MS)
        }
    }

    val dash = "\u2014"
    val protocol = if (connected) meta.protocol ?: dash else dash
    val endpoint = if (connected) meta.endpoint ?: "\u2026" else dash
    val latency = when {
        !connected -> dash
        ping.ms >= 0 -> "${ping.ms} ms"
        ping.running -> "\u2026"
        else -> dash
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaCell(stringResource(R.string.meta_protocol), protocol, Modifier.weight(1f))
        CellDivider()
        MetaCell(stringResource(R.string.meta_endpoint), endpoint, Modifier.weight(1f))
        CellDivider()
        MetaCell(
            stringResource(R.string.meta_latency),
            latency,
            Modifier.weight(1f),
            valueColor = if (connected && ping.ms >= 0) latencyColor(ping.ms) else CardTextPrimary,
            dotColor = if (connected && ping.ms >= 0) latencyColor(ping.ms) else null,
        )
    }
}

@Composable
private fun MetaCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = CardTextPrimary,
    dotColor: Color? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace,
            color = CardTextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                LedDot(color = dotColor, size = 8.dp, glowing = true)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
            )
        }
    }
}

@Composable
private fun CellDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(DIVIDER),
    )
}

// traffic feed

/** Instantaneous rates + session totals, polled once per second. */
private data class TrafficStats(
    val downRate: Long = 0L,
    val upRate: Long = 0L,
    val downTotal: Long = 0L,
    val upTotal: Long = 0L,
)

@Composable
private fun rememberTrafficStats(connectedSince: Long?, connected: Boolean): TrafficStats {
    var stats by remember(connectedSince) { mutableStateOf(TrafficStats()) }

    LaunchedEffect(connectedSince, connected) {
        if (!connected) return@LaunchedEffect
        var lastDown = -1L
        var lastUp = -1L
        var lastAt = 0L
        while (true) {
            val hev = HevTunnel.traffic()
            val share = ShareBridge.traffic()
            val bridge = SocksTunBridge.active?.getStats()
            if (hev != null || ShareBridge.active.value || bridge != null) {
                // Bridge counters: tx = TUN reads (device -> network), rx =
                // TUN writes (network -> device), matching hev's mapping.
                val down = (hev?.downloadBytes ?: 0L) + share.downloadBytes + (bridge?.rxBytes ?: 0L)
                val up = (hev?.uploadBytes ?: 0L) + share.uploadBytes + (bridge?.txBytes ?: 0L)
                val at = SystemClock.elapsedRealtime()
                var downRate = stats.downRate
                var upRate = stats.upRate
                if (lastAt > 0L && at > lastAt) {
                    val dt = at - lastAt
                    downRate = ((down - lastDown).coerceAtLeast(0L) * 1000L) / dt
                    upRate = ((up - lastUp).coerceAtLeast(0L) * 1000L) / dt
                }
                stats = TrafficStats(downRate, upRate, down, up)
                lastDown = down
                lastUp = up
                lastAt = at
            }
            delay(1_000L)
        }
    }

    return stats
}

/** 1px low-opacity neon rim for sub-containers. */
private fun Modifier.subEdge(shape: CornerBasedShape): Modifier = drawWithCache {
    val hairline = 1.dp.toPx()
    val inset = hairline / 2f
    val radius = shape.topStart.toPx(size, this)
    val outline = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
    onDrawBehind {
        drawPath(outline, color = SUB_BORDER, style = Stroke(hairline))
    }
}

// helpers

private fun formatBytes(v: Long): String {
    if (v < 1024L) return "$v B"
    val kb = v / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private fun formatRate(v: Long): String = formatBytes(v) + "/s"

private val CARD_RADIUS = 24.dp
private val CARD_SHAPE = RoundedCornerShape(CARD_RADIUS)
private val SUB_SHAPE = RoundedCornerShape(14.dp)
private val SUB_BORDER = Color(0x2435E0FF)
private val DIVIDER = Color(0x1FFFFFFF)
private val IDLE_ACCENT = NeonCyan
private val ERROR_ACCENT = NeonRed
private const val LATENCY_REFRESH_MS = 4_000L
