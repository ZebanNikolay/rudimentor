package com.rudimentor.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The one settings panel of the app.
 *
 * The metronome sheet is the reference: title, rows, divider, build label. The
 * practice screen shows the very same panel, only turned on its side by its host
 * (a bottom sheet in portrait, the side drawer in landscape), so the two screens
 * cannot drift apart any more (decision 101).
 *
 * The panel only lays the rows out; the host provides the surface and the padding.
 */
@Composable
fun SettingsPanel(
    title: String,
    modifier: Modifier = Modifier,
    buildLabel: String? = null,
    rows: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = RudiTextStyles.Rubric,
            color = RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(SECTION_GAP))
        rows()
        if (buildLabel != null) {
            Spacer(modifier = Modifier.height(SECTION_GAP))
            HorizontalDivider(color = RudiColors.Line)
            Spacer(modifier = Modifier.height(LABEL_GAP))
            Text(
                text = buildLabel,
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
            )
        }
    }
}

/** A label with a switch on the trailing edge. */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Text,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RudiColors.Text,
                checkedTrackColor = RudiColors.Brick,
                checkedBorderColor = RudiColors.BrickLit,
                uncheckedThumbColor = RudiColors.Muted,
                uncheckedTrackColor = RudiColors.Surface,
                uncheckedBorderColor = RudiColors.Line,
            ),
            modifier = if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        )
    }
}

/** A label with its current value, and the slider that changes it underneath. */
@Composable
fun SettingsSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Called once the finger leaves the track. Settings take effect as they are changed,
     * so a slider that writes on every frame would write a hundred times per drag
     * (decision 166); the caller keeps the dragged value and stores it here.
     */
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.Text,
            )
            Text(
                text = valueLabel,
                style = RudiTextStyles.StepperValue,
                color = RudiColors.Text,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = RudiColors.Text,
                activeTrackColor = RudiColors.Brick,
                inactiveTrackColor = RudiColors.Line,
            ),
        )
    }
}

/**
 * A label with a value the panel only reports: nothing to drag, nothing to switch.
 *
 * The latency reads like this now. It is measured on the calibration screen and
 * nowhere else, so a slider next to it would only invite guessing (decision 154).
 */
@Composable
fun SettingsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Text,
        )
        Text(
            text = value,
            style = RudiTextStyles.StepperValue,
            color = RudiColors.Text,
        )
    }
}

/**
 * A short hint under the row it belongs to, in the muted grey of secondary text.
 *
 * It used to be written in the orange of the metronome window, which reads as a warning:
 * a screen full of orange captions says everything is dangerous, so nothing does. Hints
 * are quiet now and only a real warning is loud -- see [SettingsWarning] (decision 173).
 */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = RudiColors.Muted,
        modifier = modifier,
    )
}

/**
 * A caption for the one thing on the screen that can spoil a measurement.
 *
 * Kept rare on purpose: there is exactly one of these -- the click playing out of the
 * speaker, which the microphone then hears and scores as a stroke (decision 173).
 */
@Composable
fun SettingsWarning(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = RudiColors.BrickLit,
        modifier = modifier,
    )
}

/**
 * A panel with a lit edge, for the settings that belong to one output rather than to the app.
 *
 * The screen holds two kinds of setting and used to show them as two look-alike lists, so
 * there was no telling which value would follow a change of headphones. The card is the
 * answer: everything inside it was measured for the output named in its title, everything
 * outside it is a matter of taste and holds everywhere (decision 173).
 */
@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RudiColors.SurfaceAlt)
            .border(BorderStroke(1.dp, RudiColors.Brick), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = RudiTextStyles.Rubric,
            color = RudiColors.BrickLit,
        )
        Spacer(modifier = Modifier.height(SECTION_GAP))
        content()
    }
}

/** The line inside a [SettingsCard] that says the rest was measured for this output. */
@Composable
fun SettingsCardDivider(text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(SECTION_GAP))
        HorizontalDivider(color = RudiColors.Line)
        Spacer(modifier = Modifier.height(LABEL_GAP))
        Text(
            text = text,
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(LABEL_GAP))
    }
}

/** The gap between the blocks of the panel. */
@Composable
fun SettingsGap() {
    Spacer(modifier = Modifier.height(SECTION_GAP))
}

private val SECTION_GAP = 18.dp
private val LABEL_GAP = 14.dp
