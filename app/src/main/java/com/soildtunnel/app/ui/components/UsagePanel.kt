package com.soildtunnel.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.UsageStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsagePanel(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    // Re-read whenever expansion opens.
    val sessions = remember(expanded) { UsageStore.history() }
    val live = remember(expanded) { UsageStore.liveSession() }
    val dayStart = remember(expanded) { startOfToday() }
    val todayRx = sessions.filter { it.startedAt >= dayStart }.sumOf { it.rxBytes } +
        (live?.takeIf { it.startedAt >= dayStart }?.rxBytes ?: 0L)
    val todayTx = sessions.filter { it.startedAt >= dayStart }.sumOf { it.txBytes } +
        (live?.takeIf { it.startedAt >= dayStart }?.txBytes ?: 0L)

    PanelCard(
        icon = Icons.Rounded.DataUsage,
        title = stringResource(R.string.usage_title),
        subtitle = stringResource(R.string.usage_today, formatBytes(todayRx + todayTx)),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            UsageRow(
                label = stringResource(R.string.usage_today_down),
                value = formatBytes(todayRx),
            )
            UsageRow(
                label = stringResource(R.string.usage_today_up),
                value = formatBytes(todayTx),
            )

            if (sessions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.usage_recent),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                val fmt = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()) }
                sessions.take(7).forEach { s ->
                    val dur = ((if (s.endedAt > 0L) s.endedAt else System.currentTimeMillis()) - s.startedAt) / 1000L
                    Text(
                        text = "%s  ·  %s  ↓%s ↑%s".format(
                            fmt.format(Date(s.startedAt)),
                            formatDuration(dur),
                            formatBytes(s.rxBytes),
                            formatBytes(s.txBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.usage_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun startOfToday(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
