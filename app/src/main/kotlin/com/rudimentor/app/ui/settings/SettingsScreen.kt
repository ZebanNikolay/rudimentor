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
import com.rudimentor.app.data.SettingsDraft
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
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
 * The latency is the one row that cannot be typed or dragged: it is only ever a
 * measurement, so it reads its value out and sends the learner to the calibration screen.
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
                SettingsValueRow(
                    label = stringResource(R.string.settings_latency_label),
                    value = stringResource(
                        R.string.practice_latency_value,
                        draft.latencyMs.roundToInt(),
                    ),
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
