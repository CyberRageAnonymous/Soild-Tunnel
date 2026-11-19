package com.soildtunnel.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.DpiClass
import com.soildtunnel.app.core.DpiProbe
import com.soildtunnel.app.ui.theme.NeonAmber
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.NeonRed
import com.soildtunnel.app.ui.theme.NeonViolet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Asks the network, in the clear and outside the tunnel, which names it is
 * willing to complete a handshake for. The list is deliberately short —
 * every run is a handful of real connections from this device.
 */
@Composable
fun DpiProbePanel(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<DpiProbe.Report?>(null) }
    val scope = rememberCoroutineScope()

    val subtitle = when {
        running -> stringResource(R.string.dpi_running)
        report != null -> stringResource(dpiClassLabel(report!!.dpi))
        else -> ""
    }

    PanelCard(
        icon = Icons.Rounded.Radar,
        title = stringResource(R.string.dpi_title),
        subtitle = subtitle,
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier,
        accent = NeonViolet,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            report?.let { r ->
                r.samples.forEach { sample ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(dpiTargetLabel(sample.label)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = sample.sni,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = verdictText(sample),
                            style = MaterialTheme.typography.labelMedium.copy(
                                textDirection = TextDirection.Ltr,
                                fontFeatureSettings = "tnum",
                            ),
                            fontWeight = FontWeight.Bold,
                            color = verdictColor(sample.verdict),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (r.udpDnsOk) R.string.dpi_udp_ok else R.string.dpi_udp_fail,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.udpDnsOk) NeonMint else NeonAmber,
                )
                Text(
                    text = stringResource(R.string.dpi_class, stringResource(dpiClassLabel(r.dpi))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                enabled = !running,
                onClick = {
                    running = true
                    scope.launch {
                        try {
                            report = DpiProbe.run()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            report = null
                        } finally {
                            running = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (report == null) R.string.dpi_run else R.string.dpi_run_again,
                    ),
                )
            }
        }
    }
}

@Composable
private fun verdictText(sample: DpiProbe.Sample): String {
    val verdict = stringResource(verdictLabel(sample.verdict))
    return if (sample.verdict == DpiProbe.Verdict.OPEN) {
        stringResource(R.string.dpi_open_ms, sample.ms)
    } else {
        verdict
    }
}

private fun verdictLabel(verdict: DpiProbe.Verdict): Int = when (verdict) {
    DpiProbe.Verdict.OPEN -> R.string.dpi_verdict_open
    DpiProbe.Verdict.RESET -> R.string.dpi_verdict_reset
    DpiProbe.Verdict.TIMEOUT -> R.string.dpi_verdict_timeout
    DpiProbe.Verdict.REFUSED -> R.string.dpi_verdict_refused
    DpiProbe.Verdict.UNREACHABLE -> R.string.dpi_verdict_unreachable
}

private fun verdictColor(verdict: DpiProbe.Verdict): androidx.compose.ui.graphics.Color =
    when (verdict) {
        DpiProbe.Verdict.OPEN -> NeonMint
        DpiProbe.Verdict.RESET -> NeonRed
        DpiProbe.Verdict.TIMEOUT -> NeonAmber
        DpiProbe.Verdict.REFUSED -> NeonAmber
        DpiProbe.Verdict.UNREACHABLE -> NeonAmber
    }

private fun dpiTargetLabel(label: String): Int = when (label) {
    "control" -> R.string.dpi_target_control
    "microsoft" -> R.string.dpi_target_microsoft
    "apple" -> R.string.dpi_target_apple
    "wikipedia" -> R.string.dpi_target_wikipedia
    "github" -> R.string.dpi_target_github
    else -> R.string.dpi_target_control
}

private fun dpiClassLabel(dpi: DpiClass): Int = when (dpi) {
    DpiClass.OPEN -> R.string.dpi_class_open
    DpiClass.SNI_FILTERING -> R.string.dpi_class_sni
    DpiClass.UDP_THROTTLED -> R.string.dpi_class_udp
    DpiClass.HOSTILE -> R.string.dpi_class_hostile
}
