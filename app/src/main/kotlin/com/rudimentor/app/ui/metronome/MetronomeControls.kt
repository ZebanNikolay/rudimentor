package com.rudimentor.app.ui.metronome

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.component.SquareIconButton
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

// BackButton и TransportButton теперь живут в ui/component; TempoKey и StepperButton —
// обёртки над SquareIconButton.

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

/** Small square ± key inside the stepper pill (27 dp, radius StepperButtonCorner, Bg fill). */
@Composable
private fun StepperButton(
    sign: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
) {
    SquareIconButton(
        onClick = onClick,
        contentDescription = description,
        size = 27.dp,
        corner = RudiDimens.StepperButtonCorner,
        background = RudiColors.Bg,
        border = RudiColors.Line,
        enabled = enabled,
    ) {
        PlusMinusGlyph(sign = sign, glyphSize = 12.dp, stroke = 1.8.dp)
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

/** Large square ± key next to the BPM number (52 dp, radius 14, Surface fill). */
@Composable
private fun TempoKey(
    sign: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
) {
    SquareIconButton(
        onClick = onClick,
        contentDescription = description,
        size = 52.dp,
        corner = 14.dp,
        background = RudiColors.Surface,
        border = RudiColors.Line,
        enabled = enabled,
    ) {
        PlusMinusGlyph(sign = sign, glyphSize = 20.dp, stroke = 2.dp)
    }
}

@Composable
private fun PlusMinusGlyph(
    sign: Int,
    glyphSize: androidx.compose.ui.unit.Dp,
    stroke: androidx.compose.ui.unit.Dp,
) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val strokePx = stroke.toPx()
        drawLine(
            color = RudiColors.Text,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        if (sign > 0) {
            drawLine(
                color = RudiColors.Text,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
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
            .clickable(
                indication = ripple(color = RudiColors.Text),
                interactionSource = null,
                onClick = onClick,
            )
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
