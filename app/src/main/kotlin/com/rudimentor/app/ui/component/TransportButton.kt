package com.rudimentor.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * The large Play/Stop transport button: a 92 dp pad with a 4 dp monolithic
 * slab, ripple in the Text color, and a steady halo while the metronome is
 * running. The visual is unchanged -- extracted from
 * ui/metronome/MetronomeControls into a reusable component.
 */
@Composable
fun TransportButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .size(TRANSPORT_SIZE)
            .drawBehind {
                val radius = 26.dp.toPx()
                if (playing) {
                    val glowRadius = size.minDimension * 0.95f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to RudiColors.BrickLit.copy(alpha = 0.11f),
                            1f to Color.Transparent,
                            center = center,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = center,
                    )
                }
                drawRoundRect(
                    color = if (playing) TransportLedgePlaying else TransportLedge,
                    topLeft = Offset(0f, 4.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            .clip(shape)
            .background(
                color = if (playing) RudiColors.Brick else RudiColors.Surface,
                shape = shape,
            )
            .border(
                width = RudiDimens.PadBorder,
                color = if (playing) RudiColors.BrickLit else RudiColors.Line,
                shape = shape,
            )
            .clickable(
                indication = ripple(color = RudiColors.Text),
                interactionSource = null,
                onClick = onClick,
            )
            .semantics { contentDescription = if (playing) "Stop" else "Start" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val tint = RudiColors.Text
            if (playing) {
                val inset = size.width * 0.12f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            } else {
                val triangle = Path().apply {
                    moveTo(size.width * 0.18f, 0f)
                    lineTo(size.width * 0.95f, size.height / 2f)
                    lineTo(size.width * 0.18f, size.height)
                    close()
                }
                drawPath(path = triangle, color = tint)
            }
        }
    }
}

private val TRANSPORT_SIZE = 92.dp
private val TransportLedge = Color(0xFF0A0A0A)
private val TransportLedgePlaying = Color(0xFF6B1414)
