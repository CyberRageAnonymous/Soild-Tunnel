package com.soildtunnel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soildtunnel.app.ui.theme.EdgeNeon
import com.soildtunnel.app.ui.theme.GlassEdge
import com.soildtunnel.app.ui.theme.GlassEdgeBright
import com.soildtunnel.app.ui.theme.GlassFillBottom
import com.soildtunnel.app.ui.theme.GlassFillTop
import com.soildtunnel.app.ui.theme.GlassSheenTop
import com.soildtunnel.app.ui.theme.NeonCyan
import com.soildtunnel.app.ui.theme.PanelBottom
import com.soildtunnel.app.ui.theme.PanelTop

fun Modifier.neonPanel(shape: Shape, edge: Color = EdgeNeon): Modifier = this
    .background(
        brush = Brush.verticalGradient(listOf(PanelTop, PanelBottom)),
        shape = shape,
    )
    .background(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to GlassSheenTop,
                0.35f to Color.Transparent,
                0.80f to Color.Transparent,
                1.00f to GlassSheenTop.copy(alpha = 0.35f),
            ),
        ),
        shape = shape,
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(edge, edge.copy(alpha = edge.alpha * 0.30f)),
        ),
        shape = shape,
    )

fun Modifier.glassPane(shape: Shape, brightRim: Boolean = false): Modifier = this
    .background(
        brush = Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)),
        shape = shape,
    )
    .background(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to GlassSheenTop,
                0.35f to Color.Transparent,
                0.80f to Color.Transparent,
                1.00f to GlassSheenTop.copy(alpha = 0.35f),
            ),
        ),
        shape = shape,
    )
    .border(1.dp, if (brightRim) GlassEdgeBright else GlassEdge, shape)

@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: Dp = 16.dp,
    edge: Color = EdgeNeon,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .neonPanel(shape, edge)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    NeonPanel(modifier = modifier, shape = shape, contentPadding = contentPadding, content = content)
}

fun Modifier.glassChip(): Modifier = this.glassPane(RoundedCornerShape(50), brightRim = true)

@Composable
fun LedDot(color: Color, size: Dp = 8.dp, glowing: Boolean = true) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
        if (glowing) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.55f), Color.Transparent),
                    radius = this.size.minDimension / 2f,
                ),
            )
        }
        drawCircle(color, radius = this.size.minDimension * 0.28f)
    }
}

@Composable
fun NeonSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
            drawCircle(accent, radius = this.size.minDimension / 2f)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PanelCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    subtitleColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(320), label = "panelArrow")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .neonPanel(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f)),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitleColor == Color.Unspecified) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            subtitleColor
                        },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(50)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(arrow),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
