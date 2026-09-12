package com.soildtunnel.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soildtunnel.app.R
import com.soildtunnel.app.core.NetProbe
import com.soildtunnel.app.core.TorDefaults
import com.soildtunnel.app.ui.theme.CardSubSurface
import com.soildtunnel.app.ui.theme.CardTextDim
import com.soildtunnel.app.ui.theme.CardTextMuted
import com.soildtunnel.app.ui.theme.CardTextPrimary
import com.soildtunnel.app.ui.theme.EdgeNeon
import com.soildtunnel.app.ui.theme.NeonCyan
import com.soildtunnel.app.ui.theme.NeonMint
import com.soildtunnel.app.ui.theme.SheetGlass

/** Exit-country picker for Tor mode. Switches the live session when connected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorExitSheet(
    selected: String,
    connected: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetGlass,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
        ) {
            NeonSectionLabel(stringResource(R.string.tor_exit_title), accent = NeonCyan)
            Text(
                text = stringResource(
                    if (connected) R.string.tor_exit_sub_live else R.string.tor_exit_sub,
                ),
                fontSize = 12.sp,
                color = CardTextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(430.dp),
            ) {
                itemsIndexed(
                    items = listOf("") + TorDefaults.EXIT_COUNTRIES,
                    key = { _, code -> code.ifBlank { "auto" } },
                ) { _, code ->
                    ExitRow(
                        code = code,
                        selected = selected == code,
                        onSelect = {
                            onSelect(code)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExitRow(
    code: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border by animateColorAsState(
        targetValue = if (selected) NeonMint.copy(alpha = 0.65f) else EdgeNeon,
        animationSpec = tween(250),
        label = "exitBorder",
    )
    val title = if (code.isBlank()) {
        stringResource(R.string.tor_exit_auto)
    } else {
        NetProbe.countryName(code).ifBlank { code }
    }
    val flag = if (code.isBlank()) "" else NetProbe.flagEmoji(code)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(color = CardSubSurface, shape = shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (flag.isNotEmpty()) {
            Text(text = flag, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.2.sp,
                color = if (selected) NeonMint else CardTextPrimary,
            )
            if (code.isNotBlank()) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = CardTextDim,
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = NeonMint,
            )
        }
    }
}
