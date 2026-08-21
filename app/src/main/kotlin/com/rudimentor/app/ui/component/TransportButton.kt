package com.rudimentor.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * The two sizes of the transport button. [Large] is the metronome's centre-piece;
 * [Small] is the one every other screen uses -- the level card and the floating
 * corner button of an attempt (decision 102).
 */
enum class TransportSize(val buttonSize: Dp) {
    Large(92.dp),
    Small(64.dp),
}

/**
 * The Play/Stop transport button: a pad with a 4 dp monolithic slab, ripple in the
 * Text color, and a steady halo while it is running. The glyphs are the stock
 * Material icons, not hand-drawn paths (decision 102).
 *
 * There is exactly one such button in the app and exactly two sizes of it, so the
 * metronome, the level card and the attempt cannot drift apart.
 *
 * [accentIdle] paints the idle button brick instead of Surface, so Play reads as
 * the call to action.
 */
@Composable
fun TransportButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: TransportSize = TransportSize.Large,
    accentIdle: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val buttonSize = size.buttonSize
    val accented = enabled && (playing || accentIdle)
    val corner = buttonSize * CORNER_RATIO
    val shape = RoundedCornerShape(corner)
    val fallbackCd =
        stringResource(if (playing) R.string.transport_stop else R.string.transport_start)
    val cd = contentDescription ?: fallbackCd
    Box(
        modifier = modifier
            .size(buttonSize)
            .alpha(if (enabled) 1f else 0.55f)
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
                    size = this.size,
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
                enabled = enabled,
                indication = ripple(color = RudiColors.Text),
                interactionSource = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = if (enabled) RudiColors.Text else RudiColors.RowNumber,
            modifier = Modifier.size(buttonSize * ICON_RATIO),
        )
    }
}

private const val CORNER_RATIO = 0.28f
private const val ICON_RATIO = 0.46f
private val TransportLedge = Color(0xFF0A0A0A)
private val TransportLedgePlaying = Color(0xFF6B1414)
