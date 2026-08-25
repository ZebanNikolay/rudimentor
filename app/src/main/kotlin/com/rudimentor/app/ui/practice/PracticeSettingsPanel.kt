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
import com.rudimentor.app.data.SettingsDraft
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
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
 * measured on the calibration screen in Settings (decision 154).
 *
 * The click follows the headphones on its own: with the speaker open the microphone
 * hears the click and scores it as a stroke (decision 88), so it stays silent there.
 * Touching the switch takes the click off automatic, and the panel then offers the
 * way back (decision 114).
 */
@Composable
fun PracticeSettingsPanel(
    settings: AppSettings,
    headphonesConnected: Boolean,
    buildInfo: BuildInfo,
    onApply: (SettingsDraft) -> Unit,
    onDone: () -> Unit,
) {
    var draft by remember { mutableStateOf(SettingsDraft.from(settings)) }
    SettingsPanel(
        title = stringResource(R.string.practice_settings_title),
        buildLabel = buildInfo.displayLabel,
    ) {
        SettingsSwitchRow(
            label = stringResource(R.string.practice_click_label),
            checked = draft.effectiveClickAudible(headphonesConnected),
            onCheckedChange = { draft = draft.withClickAudible(it) },
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
                onClick = { draft = draft.copy(clickFollowsHeadphones = true) },
                style = RudiButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsGap()
        SettingsValueRow(
            label = stringResource(R.string.practice_latency_label),
            value = stringResource(R.string.practice_latency_value, draft.latencyMs.roundToInt()),
        )
        SettingsNote(text = stringResource(R.string.practice_latency_settings_note))
        SettingsGap()
        // The verdict word is the default; the millisecond offset is for tuning the ear
        // and the latency, so it is opt-in (decision 130).
        SettingsSwitchRow(
            label = stringResource(R.string.practice_offset_ms_label),
            checked = draft.showOffsetMs,
            onCheckedChange = { draft = draft.copy(showOffsetMs = it) },
        )
        SettingsNote(text = stringResource(R.string.practice_offset_ms_note))
        SettingsGap()
        if (draft.differsFrom(settings)) {
            SettingsNote(text = stringResource(R.string.settings_unsaved_note))
            SettingsGap()
        }
        RudiButton(
            text = stringResource(R.string.practice_settings_done),
            onClick = {
                onApply(draft)
                onDone()
            },
            style = RudiButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
