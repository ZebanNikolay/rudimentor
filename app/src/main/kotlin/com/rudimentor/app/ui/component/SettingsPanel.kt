package com.rudimentor.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = RudiColors.Text,
                activeTrackColor = RudiColors.Brick,
                inactiveTrackColor = RudiColors.Line,
            ),
        )
    }
}

/** A short warning or hint under the row it belongs to. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = RudiColors.WindowGood,
        modifier = modifier,
    )
}

/** The gap between the blocks of the panel. */
@Composable
fun SettingsGap() {
    Spacer(modifier = Modifier.height(SECTION_GAP))
}

private val SECTION_GAP = 18.dp
private val LABEL_GAP = 14.dp
