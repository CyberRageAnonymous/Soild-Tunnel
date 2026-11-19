package com.soildtunnel.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.Scene
import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.ui.theme.NeonViolet

/**
 * One-tap presets for the whole profile. Each row only rewrites the knobs
 * that shape a connection, so tapping one never touches servers, accounts
 * or the app lists the user picked by hand.
 */
@Composable
fun ScenePanel(
    profile: ConnectionProfile,
    onProfileChange: (ConnectionProfile) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    PanelCard(
        icon = Icons.Rounded.AutoAwesome,
        title = stringResource(R.string.scene_title),
        subtitle = sceneSubtitle(profile),
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier,
        accent = NeonViolet,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(10.dp))
            Scene.entries.forEach { scene ->
                val active = profile.scene == scene.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onProfileChange(scene.apply(profile)) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (active) NeonViolet else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(sceneTitle(scene)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            },
                        )
                        Text(
                            text = stringResource(sceneDesc(scene)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sceneSubtitle(profile: ConnectionProfile): String {
    val scene = Scene.byId(profile.scene) ?: return ""
    return stringResource(sceneTitle(scene))
}

private fun sceneTitle(scene: Scene): Int = when (scene) {
    Scene.BALANCED -> R.string.scene_balanced
    Scene.STEALTH -> R.string.scene_stealth
    Scene.SPEED -> R.string.scene_speed
    Scene.DOUBLE_HOP -> R.string.scene_doublehop
    Scene.TOR_ONLY -> R.string.scene_toronly
}

private fun sceneDesc(scene: Scene): Int = when (scene) {
    Scene.BALANCED -> R.string.scene_balanced_desc
    Scene.STEALTH -> R.string.scene_stealth_desc
    Scene.SPEED -> R.string.scene_speed_desc
    Scene.DOUBLE_HOP -> R.string.scene_doublehop_desc
    Scene.TOR_ONLY -> R.string.scene_toronly_desc
}
