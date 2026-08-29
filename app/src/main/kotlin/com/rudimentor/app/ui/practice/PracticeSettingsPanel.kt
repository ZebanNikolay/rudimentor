package com.rudimentor.app.ui.practice

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.OutputKind
import com.rudimentor.app.data.SettingsDraft
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.SettingsWarning
import com.rudimentor.app.ui.component.SettingsSwitchRow
import com.rudimentor.app.ui.component.SettingsValueRow
import kotlin.math.roundToInt

/**
 * Contents of the side settings drawer: the shared [SettingsPanel] with the rows
 * the practice screen owns (decision 101).
 *
 * The panel edits a [SettingsDraft] and commits it on Done only, so swiping the drawer
 * shut leaves the stored settings exactly as they were (decision 154). Its host recreates
 * it every time the drawer opens, which is what makes the drop of an abandoned draft
 * automatic.
 *
 * The latency is reported, not edited: the only honest value is a measured one, and it is
 * measured by the sound check (decision 154). So the row says the same two things the
 * settings screen says -- measured, or not measured yet -- and carries the way to fix it,
 * which the drawer used to leave as a dead end right after the run that exposed it
 * (decision 179).
 *
 * The click is the output's own switch: over the speaker the microphone hears it and
 * scores it as a stroke, so a new profile for the built-in output starts silent and one
 * for headphones starts audible (decision 172). Only that first case is captioned, and
 * it is the one warning on the panel -- hints elsewhere are quiet (decision 173).
 */
@Composable
fun PracticeSettingsPanel(
    settings: AppSettings,
    /** True when the sound goes through an output with no profile of its own. */
    unknownOutput: Boolean,
    buildInfo: BuildInfo,
    onApply: (SettingsDraft) -> Unit,
    /** Leaves for the sound check: the only place the latency can honestly be changed. */
    onSoundCheck: () -> Unit,
    onDone: () -> Unit,
) {
    var draft by remember { mutableStateOf(SettingsDraft.from(settings)) }
    // Instant apply, as on the settings screen: a change here is a change made, and Done
    // only closes the drawer (decision 166).
    val update: (SettingsDraft) -> Unit = {
        draft = it
        onApply(it)
    }
    SettingsPanel(
        title = stringResource(R.string.practice_settings_title),
        buildLabel = buildInfo.displayLabel,
    ) {
        // Which output these numbers belong to. One that was never calibrated keeps the
        // previous latency, and that is the one thing worth a warning here (decision 161).
        if (unknownOutput) {
            SettingsWarning(
                text = stringResource(
                    R.string.practice_output_unknown,
                    draft.selectedProfile.name,
                ),
            )
            SettingsGap()
        }
        SettingsSwitchRow(
            label = stringResource(R.string.practice_click_label),
            checked = draft.clickAudible,
            onCheckedChange = { update(draft.withClickAudible(it)) },
        )
        if (draft.clickAudible && draft.selectedProfile.kind == OutputKind.Default) {
            SettingsWarning(text = stringResource(R.string.practice_click_warning))
        }
        SettingsGap()
        // Same label and same two verdicts as the settings screen: the drawer said
        // "Input latency" and always claimed the number was measured for this output.
        SettingsValueRow(
            label = stringResource(R.string.settings_latency_label),
            value = stringResource(R.string.practice_latency_value, draft.latencyMs.roundToInt()),
        )
        if (draft.latencyCalibrated) {
            SettingsNote(
                text = stringResource(R.string.practice_output_current, draft.selectedProfile.name),
            )
        } else {
            SettingsWarning(text = stringResource(R.string.settings_latency_guessed))
        }
        SettingsGap()
        RudiButton(
            text = stringResource(R.string.settings_calibrate),
            onClick = onSoundCheck,
            style = RudiButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsGap()
        // The verdict word is the default; the millisecond offset is for tuning the ear
        // and the latency, so it is opt-in (decision 130).
        SettingsSwitchRow(
            label = stringResource(R.string.practice_offset_ms_label),
            checked = draft.showOffsetMs,
            onCheckedChange = { update(draft.copy(showOffsetMs = it)) },
        )
        SettingsGap()
        RudiButton(
            text = stringResource(R.string.practice_settings_done),
            onClick = onDone,
            style = RudiButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
