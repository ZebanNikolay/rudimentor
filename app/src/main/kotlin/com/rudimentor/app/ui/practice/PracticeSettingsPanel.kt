package com.rudimentor.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.roundToInt

/**
 * Contents of the side settings drawer.
 *
 * The click is off by default and the headphone warning only shows when it is on:
 * with the speaker open the microphone hears the click and scores it as a stroke
 * (decision 88).
 */
@Composable
fun ColumnScope.PracticeSettingsPanel(
    clickAudible: Boolean,
    onClickAudible: (Boolean) -> Unit,
    latencyMs: Float,
    onLatencyMs: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.practice_settings_title),
        style = RudiTextStyles.Rubric,
        color = RudiColors.Muted,
    )
    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.practice_click_label),
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Text,
        )
        Switch(
            checked = clickAudible,
            onCheckedChange = onClickAudible,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RudiColors.Text,
                checkedTrackColor = RudiColors.Brick,
                uncheckedThumbColor = RudiColors.Muted,
                uncheckedTrackColor = RudiColors.SurfaceAlt,
                uncheckedBorderColor = RudiColors.Line,
            ),
        )
    }
    if (clickAudible) {
        Text(
            text = stringResource(R.string.practice_click_warning),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.WindowGood,
        )
    }

    Spacer(modifier = Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.practice_latency_label),
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Text,
        )
        Text(
            text = stringResource(R.string.practice_latency_value, latencyMs.roundToInt()),
            style = RudiTextStyles.StepperValue,
            color = RudiColors.Text,
        )
    }
    Slider(
        value = latencyMs,
        onValueChange = onLatencyMs,
        valueRange = LATENCY_MIN..LATENCY_MAX,
        colors = SliderDefaults.colors(
            thumbColor = RudiColors.Text,
            activeTrackColor = RudiColors.Brick,
            inactiveTrackColor = RudiColors.Line,
        ),
    )

    Spacer(modifier = Modifier.height(18.dp))
    RudiButton(
        text = stringResource(R.string.practice_settings_done),
        onClick = onDone,
        style = RudiButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val LATENCY_MIN = 0f
private const val LATENCY_MAX = 80f
