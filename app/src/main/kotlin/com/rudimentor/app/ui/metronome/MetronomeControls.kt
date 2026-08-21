package com.rudimentor.app.ui.metronome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.component.SquareIconButton
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

// BackButton, TransportButton and SettingsHandle now live in ui/component;
// TempoKey and StepperButton are thin wrappers over SquareIconButton. The
// `Dimension` enum lives in its own file so it can be referenced from callers
// without pulling in the whole controls module.

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
    val label = stringResource(
        if (dimension == Dimension.Beats) R.string.dimension_beats else R.string.dimension_rows,
    )
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
            description = stringResource(R.string.stepper_less, label),
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
            description = stringResource(R.string.stepper_more, label),
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
        PlusMinusGlyph(sign = sign, glyphSize = 14.dp)
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
            description = stringResource(R.string.transport_slower),
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
            Text(
                text = stringResource(R.string.metronome_bpm_label),
                style = RudiTextStyles.Rubric,
                color = RudiColors.Muted,
            )
        }
        TempoKey(
            sign = 1,
            enabled = canIncrease,
            onClick = onIncrease,
            description = stringResource(R.string.transport_faster),
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
        PlusMinusGlyph(sign = sign, glyphSize = 22.dp)
    }
}

/** The stock Material plus and minus, so the keys match the rest of the icons. */
@Composable
private fun PlusMinusGlyph(sign: Int, glyphSize: androidx.compose.ui.unit.Dp) {
    Icon(
        imageVector = if (sign > 0) Icons.Filled.Add else Icons.Filled.Remove,
        contentDescription = null,
        tint = RudiColors.Text,
        modifier = Modifier.size(glyphSize),
    )
}
