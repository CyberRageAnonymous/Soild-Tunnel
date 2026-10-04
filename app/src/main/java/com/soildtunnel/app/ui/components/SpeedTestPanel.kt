package com.soildtunnel.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.SpeedTest
import com.soildtunnel.app.model.ConnectionState
import com.soildtunnel.app.model.isConnected
import com.soildtunnel.app.ui.theme.NeonMint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SpeedTestPanel(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var liveMbps by remember { mutableStateOf(0.0) }
    var result by remember { mutableStateOf<SpeedTest.Result?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val connected = state.isConnected

    val subtitle = when {
        running -> stringResource(R.string.speed_running)
        failed -> stringResource(R.string.speed_failed)
        result != null -> {
            val r = result!!
            stringResource(
                R.string.speed_result,
                String.format(Locale.US, "%.1f", r.mbps),
                r.handshakeMs.toString(),
            )
        }
        !connected -> stringResource(R.string.speed_need_connect)
        else -> ""
    }

    PanelCard(
        icon = Icons.Rounded.Speed,
        title = stringResource(R.string.speed_title),
        subtitle = subtitle,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
        accent = NeonMint,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = connected && !running,
                    onClick = {
                        failed = false
                        result = null
                        liveMbps = 0.0
                        running = true
                        scope.launch {
                            try {
                                result = SpeedTest.run { m -> liveMbps = m }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                running = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.speed_run))
                }
                if (running && liveMbps > 0.0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f Mbps", liveMbps),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NeonMint,
                    )
                }
            }
        }
    }
}
