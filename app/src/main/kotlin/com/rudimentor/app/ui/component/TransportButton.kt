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
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * The large Play/Stop transport button: a pad with a 4 dp monolithic slab,
 * ripple in the Text color, and a steady halo while it is running. The visual is
 * unchanged -- extracted from ui/metronome/MetronomeControls into a reusable
 * component. [buttonSize] only scales it: the practice screen floats a smaller copy
 * of the very same button in the corner, it never draws its own.
 *
 * [accentIdle] paints the idle button brick instead of Surface. The practice screen
 * uses it so Play reads as the call to action, like on the level map.
 */
@Composable
fun TransportButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = TRANSPORT_SIZE,
    accentIdle: Boolean = false,
) {
    val accented = playing || accentIdle
    val corner = buttonSize * CORNER_RATIO
    val shape = RoundedCornerShape(corner)
    val cd = stringResource(if (playing) R.string.transport_stop else R.string.transport_start)
    Box(
        modifier = modifier
            .size(buttonSize)
            .drawBehind {
                val radius = corner.toPx()
                if (playing) {
                    val glowRadius = this.size.minDimension * 0.95f
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
                    color = if (accented) TransportLedgePlaying else TransportLedge,
                    topLeft = Offset(0f, 4.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            .clip(shape)
            .background(
                color = if (accented) RudiColors.Brick else RudiColors.Surface,
                shape = shape,
            )
            .border(
                width = RudiDimens.PadBorder,
                color = if (accented) RudiColors.BrickLit else RudiColors.Line,
                shape = shape,
            )
            .clickable(
                indication = ripple(color = RudiColors.Text),
                interactionSource = null,
                onClick = onClick,
            )
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(buttonSize * ICON_RATIO)) {
            val tint = RudiColors.Text
            val iconWidth = this.size.width
            val iconHeight = this.size.height
            if (playing) {
                val inset = iconWidth * 0.12f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(inset, inset),
                    size = Size(iconWidth - inset * 2, iconHeight - inset * 2),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            } else {
                val triangle = Path().apply {
                    moveTo(iconWidth * 0.18f, 0f)
                    lineTo(iconWidth * 0.95f, iconHeight / 2f)
                    lineTo(iconWidth * 0.18f, iconHeight)
                    close()
                }
                drawPath(path = triangle, color = tint)
            }
        }
    }
}

private val TRANSPORT_SIZE = 92.dp
private const val CORNER_RATIO = 0.28f
private const val ICON_RATIO = 0.35f
private val TransportLedge = Color(0xFF0A0A0A)
private val TransportLedgePlaying = Color(0xFF6B1414)
