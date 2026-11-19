package com.soildtunnel.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.LeakLab
import com.soildtunnel.app.core.TunnelConfig
import com.soildtunnel.app.core.TorManager
import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.model.ConnectionState
import com.soildtunnel.app.model.isConnected
import com.soildtunnel.app.ui.theme.NeonAmber
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.NeonRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Runs the leak checks on demand and shows each verdict on its own line.
 * Nothing here runs by itself: a check costs real traffic in both
 * directions, so the user asks for it with the button.
 */
@Composable
fun LeakLabPanel(
    state: ConnectionState,
    profile: ConnectionProfile,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val connected = state.isConnected
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<LeakLab.Check>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val subtitle = when {
        running -> stringResource(R.string.leak_running)
        results.isNotEmpty() ->
            stringResource(R.string.leak_summary, results.count { it.status == LeakLab.Status.PASS }, results.size)
        !connected -> stringResource(R.string.leak_need_connect)
        else -> ""
    }

    PanelCard(
        icon = Icons.Rounded.FactCheck,
        title = stringResource(R.string.leak_title),
        subtitle = subtitle,
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier,
        accent = NeonMint,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            if (results.isNotEmpty()) {
                results.forEach { check ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = statusIcon(check.status),
                            contentDescription = null,
                            tint = statusColor(check.status),
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = leakText(check),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                enabled = connected && !running,
                onClick = {
                    running = true
                    scope.launch {
                        try {
                            val socks = if (profile.protocol == com.soildtunnel.app.model.Protocol.TOR) {
                                TorManager.socksPort
                            } else {
                                TunnelConfig.SOCKS_PORT
                            }
                            results = LeakLab.run(profile, socks)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            results = emptyList()
                        } finally {
                            running = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (results.isEmpty()) R.string.leak_run else R.string.leak_run_again))
            }
        }
    }
}

@Composable
private fun leakText(check: LeakLab.Check): String {
    val args = check.args.toTypedArray()
    val id = when (check.id) {
        "exit_ok" -> R.string.leak_exit_ok
        "exit_same" -> R.string.leak_exit_same
        "exit_unknown" -> R.string.leak_exit_unknown
        "dns_ok" -> R.string.leak_dns_ok
        "dns_fail" -> R.string.leak_dns_fail
        "hijack_ok" -> R.string.leak_hijack_ok
        "hijack_warn" -> R.string.leak_hijack_warn
        "v6_none" -> R.string.leak_v6_none
        "v6_v4" -> R.string.leak_v6_v4
        "v6_ok" -> R.string.leak_v6_ok
        "v6_risk" -> R.string.leak_v6_risk
        else -> R.string.leak_exit_unknown
    }
    return stringResource(id, *args)
}

private fun statusIcon(status: LeakLab.Status): ImageVector = when (status) {
    LeakLab.Status.PASS -> Icons.Rounded.CheckCircle
    LeakLab.Status.WARN -> Icons.Rounded.Warning
    LeakLab.Status.FAIL -> Icons.Rounded.Error
}

@Composable
private fun statusColor(status: LeakLab.Status): androidx.compose.ui.graphics.Color = when (status) {
    LeakLab.Status.PASS -> NeonMint
    LeakLab.Status.WARN -> NeonAmber
    LeakLab.Status.FAIL -> NeonRed
}
