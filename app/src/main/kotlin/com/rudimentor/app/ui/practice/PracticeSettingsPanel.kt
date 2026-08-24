package com.rudimentor.app.ui.practice

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.SettingsSliderRow
import com.rudimentor.app.ui.component.SettingsSwitchRow
import kotlin.math.roundToInt

/**
 * Contents of the side settings drawer: the shared [SettingsPanel] with the rows
 * the practice screen owns (decision 101).
 *
 * The click follows the headphones on its own: with the speaker open the microphone
 * hears the click and scores it as a stroke (decision 88), so it stays silent there.
 * Touching the switch takes the click off automatic, and the panel then offers the
 * way back (decision 114).
 */
@Composable
fun PracticeSettingsPanel(
    clickAudible: Boolean,
    onClickAudible: (Boolean) -> Unit,
    clickFollowsHeadphones: Boolean,
    onClickFollowsHeadphones: (Boolean) -> Unit,
    latencyMs: Float,
    onLatencyMs: (Float) -> Unit,
    showOffsetMs: Boolean,
    onShowOffsetMs: (Boolean) -> Unit,
    buildInfo: BuildInfo,
    onDone: () -> Unit,
) {
    SettingsPanel(
        title = stringResource(R.string.practice_settings_title),
        buildLabel = buildInfo.displayLabel,
    ) {
        SettingsSwitchRow(
            label = stringResource(R.string.practice_click_label),
            checked = clickAudible,
            onCheckedChange = onClickAudible,
        )
        if (clickFollowsHeadphones) {
            SettingsNote(text = stringResource(R.string.practice_click_auto_note))
        } else {
            val note = if (clickAudible) {
                R.string.practice_click_warning
            } else {
                R.string.practice_click_manual_note
            }
            SettingsNote(text = stringResource(note))
            SettingsGap()
            RudiButton(
                text = stringResource(R.string.practice_click_auto_restore),
                onClick = { onClickFollowsHeadphones(true) },
                style = RudiButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsGap()
        SettingsSliderRow(
            label = stringResource(R.string.practice_latency_label),
            valueLabel = stringResource(R.string.practice_latency_value, latencyMs.roundToInt()),
            value = latencyMs,
            valueRange = AppSettings.LATENCY_MIN_MS..AppSettings.LATENCY_MAX_MS,
            onValueChange = onLatencyMs,
        )
        SettingsGap()
        // The verdict word is the default; the millisecond offset is for tuning the ear
        // and the latency, so it is opt-in (decision 130).
        SettingsSwitchRow(
            label = stringResource(R.string.practice_offset_ms_label),
            checked = showOffsetMs,
            onCheckedChange = onShowOffsetMs,
        )
        SettingsNote(text = stringResource(R.string.practice_offset_ms_note))
        SettingsGap()
        RudiButton(
            text = stringResource(R.string.practice_settings_done),
            onClick = onDone,
            style = RudiButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
