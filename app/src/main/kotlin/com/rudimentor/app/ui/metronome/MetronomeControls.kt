package com.rudimentor.app.ui.metronome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

/** What a stepper changes: the beats in the current row, or the rows of the drum. */
enum class Dimension {
    Beats,
    Rows,
}

/**
 * Icon-only dimension stepper: vertical strokes stand for beats, horizontal lines for rows.
 */
@Composable
fun DimensionStepper(
    dimension: Dimension,
    value: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (dimension == Dimension.Beats) "beats in row" else "rows"
    val shape = RoundedCornerShape(13.dp)
    // One compact housing: the glyph sits at the left edge, the keys and the number live
    // inside it, and the whole pill is laid out as a single centred block.
    Row(
        modifier = modifier
            .background(color = RudiColors.Surface, shape = shape)
            .border(width = RudiDimens.PadBorder, color = RudiColors.Line, shape = shape)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DimensionGlyph(dimension = dimension)
        Spacer(modifier = Modifier.width(3.dp))
        StepperButton(
            sign = -1,
            enabled = canDecrease,
            onClick = onDecrease,
            description = "One less $label",
        )
        Text(
            text = value.toString(),
            style = RudiTextStyles.StepperValue,
            color = RudiColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(19.dp),
        )
        StepperButton(
            sign = 1,
            enabled = canIncrease,
            onClick = onIncrease,
            description = "One more $label",
        )
    }
}

@Composable
private fun DimensionGlyph(dimension: Dimension) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 1.6.dp.toPx()
        val lines = 3
        repeat(lines) { index ->
            val fraction = (index + 0.5f) / lines
            if (dimension == Dimension.Beats) {
                val x = size.width * fraction
                drawLine(
                    color = RudiColors.Muted,
                    start = Offset(x, size.height * 0.08f),
                    end = Offset(x, size.height * 0.92f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            } else {
                val y = size.height * fraction
                drawLine(
                    color = RudiColors.Muted,
                    start = Offset(size.width * 0.08f, y),
                    end = Offset(size.width * 0.92f, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    sign: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
) {
    val shape = RoundedCornerShape(RudiDimens.StepperButtonCorner)
    Box(
        modifier = Modifier
            .size(27.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .background(color = RudiColors.Bg, shape = shape)
            .border(width = RudiDimens.PadBorder, color = RudiColors.Line, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val stroke = 1.8.dp.toPx()
            drawLine(
                color = RudiColors.Text,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (sign > 0) {
                drawLine(
                    color = RudiColors.Text,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Tempo readout with the two large round-cornered ± keys. */
@Composable
fun TempoControl(
    bpm: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keys hug the number instead of being pushed to the screen edges.
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        TempoKey(
            sign = -1,
            enabled = canDecrease,
            onClick = onDecrease,
            description = "Slower tempo",
        )
        Column(
            modifier = Modifier.width(128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = bpm.toString(),
                style = RudiTextStyles.BpmValue,
                color = RudiColors.Text,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "BPM", style = RudiTextStyles.Rubric, color = RudiColors.Muted)
        }
        TempoKey(
            sign = 1,
            enabled = canIncrease,
            onClick = onIncrease,
            description = "Faster tempo",
        )
    }
}

@Composable
private fun TempoKey(
    sign: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .background(color = RudiColors.Surface, shape = shape)
            .border(width = RudiDimens.PadBorder, color = RudiColors.Line, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(
                color = RudiColors.Text,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (sign > 0) {
                drawLine(
                    color = RudiColors.Text,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** The transport key: a large pad that lights up brick while the metronome runs. */
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
                // The physical key ledge: a solid 4 dp plate under the button, plus an
                // even halo while running.
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
            .background(
                color = if (playing) RudiColors.Brick else RudiColors.Surface,
                shape = shape,
            )
            .border(
                width = RudiDimens.PadBorder,
                color = if (playing) RudiColors.BrickLit else RudiColors.Line,
                shape = shape,
            )
            .clickable(onClick = onClick)
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
                    size = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2,
                        size.height - inset * 2,
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        3.dp.toPx(),
                        3.dp.toPx(),
                    ),
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

/** The handle that pulls up the settings sheet. */
@Composable
fun SettingsHandle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 14.dp)
            .semantics { contentDescription = "Open settings" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.width(18.dp).height(8.dp)) {
            val stroke = 1.8.dp.toPx()
            val inset = stroke / 2f
            drawLine(
                color = RudiColors.Muted,
                start = Offset(inset, size.height - inset),
                end = Offset(size.width / 2f, inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = RudiColors.Muted,
                start = Offset(size.width / 2f, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "SETTINGS",
            style = RudiTextStyles.Rubric,
            color = RudiColors.Muted,
        )
    }
}

/** Transport key metrics and the ledge colours that give it its physical depth. */
private val TRANSPORT_SIZE = 92.dp
private val TransportLedge = Color(0xFF0A0A0A)
private val TransportLedgePlaying = Color(0xFF6B1414)
