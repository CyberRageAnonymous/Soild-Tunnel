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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soildtunnel.app.ui.theme.CardTextDim
import com.soildtunnel.app.ui.theme.CardTextMuted
import com.soildtunnel.app.ui.theme.CardTextPrimary
import com.soildtunnel.app.ui.theme.EdgeNeon
import com.soildtunnel.app.ui.theme.EdgeNeonBright
import com.soildtunnel.app.ui.theme.GlassEdge
import com.soildtunnel.app.ui.theme.GlassEdgeBright
import com.soildtunnel.app.ui.theme.GlassFillBottom
import com.soildtunnel.app.ui.theme.GlassFillTop
import com.soildtunnel.app.ui.theme.GlassSheenTop
import com.soildtunnel.app.ui.theme.NeonCyan
import com.soildtunnel.app.ui.theme.PanelBottom
import com.soildtunnel.app.ui.theme.PanelTop

// Surface material system: neonPanel (console panels), glassPane (floating chips).

/** The console panel: dark translucent body + hairline neon edge. */
fun Modifier.neonPanel(
    shape: Shape,
    edge: Color = EdgeNeon,
): Modifier = this
    .background(
        brush = Brush.verticalGradient(listOf(PanelTop, PanelBottom)),
        shape = shape,
    )
    .background(
        // Sheen across the top edge (stronger at the rim, gone by ~42% depth)
        // plus a faint mirror at the bottom rim — the two light catchers a real
        // slab of glass shows.
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to GlassSheenTop,
                0.42f to Color.Transparent,
                0.84f to Color.Transparent,
                1.00f to GlassSheenTop.copy(alpha = 0.45f),
            ),
        ),
        shape = shape,
    )
    .border(1.dp, edge, shape)

/** Glass pane for floating chips (top-bar buttons). */
fun Modifier.glassPane(
    shape: Shape,
    brightRim: Boolean = false,
): Modifier = this
    .background(
        brush = Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)),
        shape = shape,
    )
    .background(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to GlassSheenTop,
                0.42f to Color.Transparent,
                0.84f to Color.Transparent,
                1.00f to GlassSheenTop.copy(alpha = 0.45f),
            ),
        ),
        shape = shape,
    )
    .border(1.dp, if (brightRim) GlassEdgeBright else GlassEdge, shape)

/** A finished neon panel with rounded corners and standard inner padding. */
@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
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

/** Small circular chip used behind standalone icons (top-bar buttons). */
fun Modifier.glassChip(): Modifier = this.glassPane(RoundedCornerShape(50), brightRim = true)

// decorations

/** Four corner bracket lines inside the bounds. */
fun Modifier.neonBrackets(
    color: Color,
    length: Dp = 14.dp,
    inset: Dp = 3.dp,
    strokeWidth: Dp = 2.dp,
): Modifier = drawWithCache {
    val len = length.toPx()
    val gap = inset.toPx()
    val w = strokeWidth.toPx()
    onDrawBehind {
        val right = size.width - gap
        val bottom = size.height - gap
        // top-left
        drawLine(color, Offset(gap, gap), Offset(gap + len, gap), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(gap, gap), Offset(gap, gap + len), strokeWidth = w, cap = StrokeCap.Round)
        // top-right
        drawLine(color, Offset(right, gap), Offset(right - len, gap), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(right, gap), Offset(right, gap + len), strokeWidth = w, cap = StrokeCap.Round)
        // bottom-left
        drawLine(color, Offset(gap, bottom), Offset(gap + len, bottom), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(gap, bottom), Offset(gap, bottom - len), strokeWidth = w, cap = StrokeCap.Round)
        // bottom-right
        drawLine(color, Offset(right, bottom), Offset(right - len, bottom), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - len), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/** Status LED with a soft phosphor halo. */
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

/** Uppercase console section header: tick bar + letterspaced mono label. */
@Composable
fun NeonSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Spacer(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The one collapsible panel shell every drawer/sheet card uses: neon slab,
 * icon badge, mono uppercase title, subtitle, rotating chevron.
 */
@Composable
fun PanelCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    subtitleColor: Color = CardTextDim,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(300), label = "panelArrow")
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
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.09f), RoundedCornerShape(10.dp))
                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                    color = CardTextPrimary,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = CardTextMuted,
                modifier = Modifier.rotate(arrow),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
