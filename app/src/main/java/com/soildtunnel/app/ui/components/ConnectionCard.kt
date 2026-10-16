package com.soildtunnel.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
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
import com.soildtunnel.app.ui.theme.AuraIndigo
import com.soildtunnel.app.ui.theme.CardSubSurface
import com.soildtunnel.app.ui.theme.CardTextDim
import com.soildtunnel.app.ui.theme.CardTextMuted
import com.soildtunnel.app.ui.theme.CardTextPrimary
import com.soildtunnel.app.ui.theme.NeonCyan
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.NeonRed
import com.soildtunnel.app.ui.theme.latencyColor

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
    socksPort: Int = TunnelConfig.SOCKS_PORT,
) {
    val accent = when {
        error -> ERROR_ACCENT
        connected -> NeonMint
        else -> IDLE_ACCENT
    }
    val stats = rememberTrafficStats(connectedSince = connectedSince, connected = connected)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .neonPanel(CARD_SHAPE, edge = accent.copy(alpha = 0.40f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusHeaderText(connected, error)
            StatusBlock(title = statusTitle, caption = statusCaption, accent = accent)
            SectionDivider()
            TimerBlock(connectedSince = connectedSince, connected = connected, accent = accent)
            ServerIpPill(connected = connected, ipInfo = ipInfo, ipLoading = ipLoading)
            SectionDivider()
            SpeedStrip(stats = stats)
            TrafficSparkline(history = stats.history, connected = connected)
            ProtocolStrip(connected = connected, socksPort = socksPort)
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(0.94f),
        thickness = 1.dp,
        color = Color(0x147C6BFF),
    )
}

@Composable
private fun StatusHeaderText(connected: Boolean, error: Boolean) {
    val color = when {
        error -> NeonRed
        connected -> NeonMint
        else -> NeonCyan
    }
    val touch by rememberInfiniteTransition(label = "hdrBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hdrBlinkA",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(Modifier.graphicsLayer { alpha = if (connected) touch else 1f }) {
                LedDot(color = color, size = 7.dp, glowing = true)
            }
            Text(
                text = stringResource(
                    when {
                        error -> R.string.console_fault
                        connected -> R.string.console_live
                        else -> R.string.console_standby
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.console_label),
            style = MaterialTheme.typography.labelSmall,
            color = CardTextDim,
        )
    }
}

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
                style = MaterialTheme.typography.displayMedium.copy(
                    brush = Brush.linearGradient(
                        listOf(
                            accent,
                            if (accent == NEUTRAL_ACCENT) CardTextPrimary else accent.copy(alpha = 0.72f),
                        ),
                    ),
                ),
                textAlign = TextAlign.Center,
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
                style = MaterialTheme.typography.bodyMedium,
                color = CardTextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TimerBlock(connectedSince: Long?, connected: Boolean, accent: Color) {
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
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.connected_for),
            style = MaterialTheme.typography.labelMedium,
            color = CardTextDim,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFeatureSettings = "tnum",
                color = if (connected) CardTextPrimary else CardTextDim,
            ),
        )
    }
}

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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (connected && ipInfo?.viaTunnel == true) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = NeonMint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = CardTextMuted)
        Spacer(Modifier.weight(1f))
        if (ipInfo != null) {
            Text(text = flag, fontSize = 15.sp)
        }
        if (country.isNotEmpty()) {
            Text(text = country, style = MaterialTheme.typography.labelMedium, color = CardTextMuted)
            Spacer(Modifier.width(6.dp))
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cardIp",
        ) { shown ->
            Text(
                text = shown,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = TextDirection.Ltr,
                    fontFeatureSettings = "tnum",
                ),
                color = CardTextPrimary,
            )
        }
    }
}

@Composable
private fun SpeedStrip(stats: TrafficStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
            tint = AuraIndigo,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = formatRate(rate),
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = TextDirection.Ltr,
                    fontFeatureSettings = "tnum",
                ),
                color = CardTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.traffic_total, formatBytes(total)),
                style = MaterialTheme.typography.labelSmall,
                color = CardTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(color = tint.copy(alpha = 0.14f), CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .height(3.dp)
                        .background(color = tint, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun ProtocolStrip(connected: Boolean, socksPort: Int) {
    val meta by EngineMeta.state.collectAsState()
    val ping by PingMonitor.state.collectAsState()

    LaunchedEffect(connected) {
        while (connected) {
            PingMonitor.pingOnce(viaTunnel = true, socksPort = socksPort)
            delay(LATENCY_REFRESH_MS)
        }
    }

    val dash = "—"
    val protocol = if (connected) meta.protocol ?: dash else dash
    val endpoint = if (connected) meta.endpoint ?: "…" else dash
    val latency = when {
        !connected -> dash
        ping.ms >= 0 -> "${ping.ms} ms"
        ping.running -> "…"
        else -> dash
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE)
            .padding(horizontal = 8.dp, vertical = 12.dp),
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
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardTextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                LedDot(color = dotColor, size = 8.dp, glowing = true)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    textDirection = TextDirection.Ltr,
                ),
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CellDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(30.dp)
            .background(DIVIDER),
    )
}

private data class TrafficStats(
    val downRate: Long = 0L,
    val upRate: Long = 0L,
    val downTotal: Long = 0L,
    val upTotal: Long = 0L,
    val history: List<Long> = emptyList(),
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
                stats = TrafficStats(downRate, upRate, down, up, (stats.history + downRate).takeLast(60))
                lastDown = down
                lastUp = up
                lastAt = at
            }
            delay(1_000L)
        }
    }

    return stats
}

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

@Composable
private fun TrafficSparkline(history: List<Long>, connected: Boolean) {
    val accent = if (connected) NeonMint else NeonCyan
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sparkline_label),
                style = MaterialTheme.typography.labelSmall,
                color = CardTextDim,
            )
            Spacer(Modifier.weight(1f))
            LedDot(color = accent, size = 6.dp, glowing = connected)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .drawWithCache {
                    val n = history.size
                    val maxV = (history.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
                    val line = Path()
                    val closed = Path()
                    var lastX = 0f
                    var lastY = 0f
                    if (n >= 2) {
                        val stepX = size.width / (n - 1)
                        val usable = size.height - 8f
                        var prevX = 0f
                        var prevY = 0f
                        for (i in history.indices) {
                            val x = i * stepX
                            val y = size.height - 4f - (history[i] / maxV) * usable
                            if (i == 0) {
                                line.moveTo(x, y)
                            } else {
                                val midX = (prevX + x) / 2f
                                val midY = (prevY + y) / 2f
                                line.quadraticBezierTo(prevX, prevY, midX, midY)
                            }
                            prevX = x
                            prevY = y
                            lastX = x
                            lastY = y
                        }
                        line.lineTo(prevX, prevY)
                        closed.apply {
                            addPath(line)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                    }
                    onDrawBehind {
                        drawLine(
                            color = SUB_BORDER,
                            start = Offset(0f, size.height - 1f),
                            end = Offset(size.width, size.height - 1f),
                            strokeWidth = 1.dp.toPx(),
                        )
                        if (n >= 2) {
                            drawPath(
                                path = closed,
                                brush = Brush.verticalGradient(
                                    listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.02f)),
                                ),
                            )
                            drawPath(
                                path = line,
                                color = accent,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            )
                            drawCircle(
                                color = accent.copy(alpha = 0.25f),
                                radius = 6.dp.toPx(),
                                center = Offset(lastX, lastY),
                            )
                            drawCircle(
                                color = accent,
                                radius = 2.5.dp.toPx(),
                                center = Offset(lastX, lastY),
                            )
                        }
                    }
                },
        )
    }
}

private fun formatBytes(v: Long): String {
    if (v < 1024L) return "$v B"
    val kb = v / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private fun formatRate(v: Long): String = formatBytes(v) + "/s"

private val CARD_SHAPE = RoundedCornerShape(24.dp)
private val SUB_SHAPE = RoundedCornerShape(16.dp)
private val SUB_BORDER = Color(0x227C6BFF)
private val DIVIDER = Color(0x1FFFFFFF)
private val IDLE_ACCENT = NeonCyan
private val ERROR_ACCENT = NeonRed
private val NEUTRAL_ACCENT = NeonCyan
private const val LATENCY_REFRESH_MS = 4_000L
