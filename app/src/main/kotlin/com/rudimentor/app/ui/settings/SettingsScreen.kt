package com.rudimentor.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.audio.MicThreshold
import com.rudimentor.app.data.SettingsDraft
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.SettingsSliderRow
import com.rudimentor.app.ui.component.SettingsSwitchRow
import com.rudimentor.app.ui.component.SettingsValueRow
import com.rudimentor.app.ui.theme.RudiColors
import kotlin.math.roundToInt

/**
 * The settings of the app, off the main menu.
 *
 * Everything on this screen is edited in a [SettingsDraft] and written nowhere until the
 * learner presses Save; Cancel, the back arrow and the system back gesture all drop the
 * draft (decision 154). The drawer during an attempt keeps the same contract, so a
 * setting is never changed by accident in either place.
 *
 * The latency can be dragged as well as measured. Calibration is the honest way to get it,
 * but a learner who knows their headphones -- or wants to check what 30 ms more feels like --
 * should not have to sit through a round to try a number (decision 158).
 *
 * The panel opens from the levels screen, next to the rank button, because these are the
 * settings of practising. The main menu no longer carries them: the metronome has its own
 * sheet, and the two were being confused for each other (decision 158).
 */
@Composable
fun SettingsScreen(
    draft: SettingsDraft,
    settings: AppSettings,
    headphonesConnected: Boolean,
    buildInfo: BuildInfo,
    onDraftChange: (SettingsDraft) -> Unit,
    onCalibrate: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { onCancel() }

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(
                title = stringResource(R.string.settings_title),
                onBack = onCancel,
            )
            Spacer(modifier = Modifier.height(16.dp))

            SettingsPanel(
                title = stringResource(R.string.settings_title),
                buildLabel = buildInfo.displayLabel,
            ) {
                SettingsSwitchRow(
                    label = stringResource(R.string.practice_click_label),
                    checked = draft.effectiveClickAudible(headphonesConnected),
                    onCheckedChange = { onDraftChange(draft.withClickAudible(it)) },
                )
                if (draft.clickFollowsHeadphones) {
                    SettingsNote(text = stringResource(R.string.practice_click_auto_note))
                } else {
                    val note = if (draft.clickAudible) {
                        R.string.practice_click_warning
                    } else {
                        R.string.practice_click_manual_note
                    }
                    SettingsNote(text = stringResource(note))
                    SettingsGap()
                    RudiButton(
                        text = stringResource(R.string.practice_click_auto_restore),
                        onClick = {
                            onDraftChange(draft.copy(clickFollowsHeadphones = true))
                        },
                        style = RudiButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingsGap()
                // The verdict word is the default; the millisecond offset is for tuning
                // the ear and the latency, so it is opt-in (decision 130).
                SettingsSwitchRow(
                    label = stringResource(R.string.practice_offset_ms_label),
                    checked = draft.showOffsetMs,
                    onCheckedChange = { onDraftChange(draft.copy(showOffsetMs = it)) },
                )
                SettingsNote(text = stringResource(R.string.practice_offset_ms_note))

                SettingsGap()
                SettingsSliderRow(
                    label = stringResource(R.string.settings_latency_label),
                    valueLabel = stringResource(
                        R.string.practice_latency_value,
                        draft.latencyMs.roundToInt(),
                    ),
                    value = draft.latencyMs,
                    valueRange = 0f..AppSettings.LATENCY_MAX_MS,
                    onValueChange = { onDraftChange(draft.withLatency(it)) },
                )
                SettingsNote(
                    text = stringResource(
                        if (draft.latencyCalibrated) {
                            R.string.settings_latency_measured
                        } else {
                            R.string.settings_latency_guessed
                        },
                    ),
                )
                SettingsNote(text = stringResource(R.string.settings_latency_note))

                SettingsGap()
                // The gate is measured against the room, so it is set on the calibration
                // screen where the meter is; here it only reports itself (decision 158).
                SettingsValueRow(
                    label = stringResource(R.string.settings_gate_label),
                    value = stringResource(
                        R.string.settings_gate_value,
                        MicThreshold.decibels(draft.micThresholdLevel).roundToInt(),
                    ),
                )
                SettingsNote(text = stringResource(R.string.settings_gate_note))

                SettingsGap()
                RudiButton(
                    text = stringResource(R.string.settings_calibrate),
                    onClick = onCalibrate,
                    style = RudiButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            if (draft.differsFrom(settings)) {
                SettingsNote(text = stringResource(R.string.settings_unsaved_note))
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RudiButton(
                    text = stringResource(R.string.settings_save),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
                RudiButton(
                    text = stringResource(R.string.settings_cancel),
                    onClick = onCancel,
                    style = RudiButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
