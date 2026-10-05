package com.soildtunnel.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.R
import com.soildtunnel.app.data.ThemeStore
import com.soildtunnel.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun ThemePanel(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val store = remember { ThemeStore(context) }
    val scope = rememberCoroutineScope()
    val current by store.mode.collectAsState(initial = ThemeMode.SYSTEM)

    fun pick(mode: ThemeMode) {
        if (mode == current) return
        // The write MUST finish before the rebuild: this scope dies with the
        // old activity, so recreating first cancels a still-running save and
        // the new activity reads back the old theme — stuck forever.
        scope.launch {
            store.setMode(mode)
            (context as? android.app.Activity)?.recreate()
        }
    }

    PanelCard(
        icon = Icons.Rounded.BrightnessAuto,
        title = stringResource(R.string.theme_title),
        subtitle = themeLabel(current),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ThemeRow(
                label = stringResource(R.string.theme_system),
                selected = current == ThemeMode.SYSTEM,
                onClick = { pick(ThemeMode.SYSTEM) },
            )
            ThemeRow(
                label = stringResource(R.string.theme_dark),
                selected = current == ThemeMode.DARK,
                onClick = { pick(ThemeMode.DARK) },
            )
            ThemeRow(
                label = stringResource(R.string.theme_light),
                selected = current == ThemeMode.LIGHT,
                onClick = { pick(ThemeMode.LIGHT) },
            )
        }
    }
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
}
