package com.soildtunnel.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.LocaleStore

@Composable
fun LanguagePanel(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val current = remember { LocaleStore.get(context) }

    fun pick(tag: String) {
        if (tag == current) return
        LocaleStore.set(context, tag)
        (context as? android.app.Activity)?.recreate()
    }

    PanelCard(
        icon = Icons.Rounded.Language,
        title = stringResource(R.string.lang_title),
        subtitle = languageLabel(current),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            LanguageRow(
                label = stringResource(R.string.lang_system),
                selected = current == LocaleStore.SYSTEM,
                onClick = { pick(LocaleStore.SYSTEM) },
            )
            LanguageRow(
                label = "English",
                selected = current == LocaleStore.ENGLISH,
                onClick = { pick(LocaleStore.ENGLISH) },
            )
            LanguageRow(
                label = "\u0641\u0627\u0631\u0633\u06CC",
                selected = current == LocaleStore.PERSIAN,
                onClick = { pick(LocaleStore.PERSIAN) },
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun languageLabel(tag: String): String = when (tag) {
    LocaleStore.ENGLISH -> "English"
    LocaleStore.PERSIAN -> "\u0641\u0627\u0631\u0633\u06CC"
    else -> stringResource(R.string.lang_system)
}
