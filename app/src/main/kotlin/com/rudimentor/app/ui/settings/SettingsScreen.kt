package com.rudimentor.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.OutputDevice
import com.rudimentor.app.data.OutputKind
import com.rudimentor.app.data.OutputProfile
import com.rudimentor.app.audio.MicThreshold
import com.rudimentor.app.data.SettingsDraft
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.ToolbarScreen
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
    currentOutput: OutputDevice?,
    buildInfo: BuildInfo,
    onDraftChange: (SettingsDraft) -> Unit,
    onCalibrate: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { onCancel() }

    ToolbarScreen(
        toolbar = {
            AppToolbar(
                title = stringResource(R.string.settings_title),
                onBack = onCancel,
            )
        },
    ) {

        OutputProfilePanel(
            draft = draft,
            currentOutput = currentOutput,
            onDraftChange = onDraftChange,
            onCalibrate = onCalibrate,
        )
        Spacer(modifier = Modifier.height(14.dp))

        SettingsPanel(
            title = stringResource(R.string.settings_practice_title),
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


/**
 * The saved outputs: one latency per pair of headphones (decision 161).
 *
 * The list is short on purpose -- the built-in profile plus two -- and the choice is made by
 * hand here, because Android cannot tell the app which of several attached outputs it is
 * really routing to. Calibration writes into whichever profile is selected here.
 */
@Composable
private fun OutputProfilePanel(
    draft: SettingsDraft,
    currentOutput: OutputDevice?,
    onDraftChange: (SettingsDraft) -> Unit,
    onCalibrate: () -> Unit,
) {
    var renaming by remember { mutableStateOf<OutputProfile?>(null) }

    SettingsPanel(title = stringResource(R.string.settings_output_title)) {
        SettingsNote(text = stringResource(R.string.settings_output_note))
        SettingsGap()
        draft.outputProfiles.forEach { profile ->
            OutputProfileRow(
                profile = profile,
                selected = profile.id == draft.selectedProfileId,
                onSelect = { onDraftChange(draft.withSelectedProfile(profile.id)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val selected = draft.selectedProfile
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RudiButton(
                text = stringResource(R.string.settings_output_rename),
                onClick = { renaming = selected },
                style = RudiButtonStyle.Secondary,
                enabled = selected.removable,
                modifier = Modifier.weight(1f),
            )
            RudiButton(
                text = stringResource(R.string.settings_output_delete),
                onClick = { onDraftChange(draft.withoutProfile(selected.id)) },
                style = RudiButtonStyle.Secondary,
                enabled = selected.removable,
                modifier = Modifier.weight(1f),
            )
        }
        if (!selected.removable) {
            SettingsGap()
            SettingsNote(text = stringResource(R.string.settings_output_default_note))
        }
        SettingsGap()
        val alreadySaved = currentOutput != null &&
            draft.outputProfiles.any { it.boundKey == currentOutput.key }
        RudiButton(
            text = if (currentOutput == null) {
                stringResource(R.string.settings_output_add_none)
            } else {
                stringResource(R.string.settings_output_add, currentOutput.name)
            },
            onClick = {
                currentOutput?.let {
                    onDraftChange(draft.withAddedProfile(it, System.currentTimeMillis()))
                }
            },
            style = RudiButtonStyle.Secondary,
            enabled = currentOutput != null && !alreadySaved && draft.canAddProfile,
            modifier = Modifier.fillMaxWidth(),
        )
        if (currentOutput != null && !alreadySaved && !draft.canAddProfile) {
            SettingsGap()
            SettingsNote(
                text = stringResource(
                    R.string.settings_output_full,
                    OutputProfile.MAX_PROFILES,
                ),
            )
        }

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
        SettingsNote(
            text = stringResource(R.string.settings_latency_profile, selected.name),
        )
        SettingsNote(text = stringResource(R.string.settings_latency_note))

        SettingsGap()
        // The gate is measured against the room, not against the output, so it only reports
        // itself here; it is set on the calibration screen where the meter is (decision 158).
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

    renaming?.let { profile ->
        RenameProfileDialog(
            profile = profile,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onDraftChange(draft.withRenamedProfile(profile.id, name))
                renaming = null
            },
        )
    }
}

@Composable
private fun OutputProfileRow(
    profile: OutputProfile,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) RudiColors.SurfaceAlt else RudiColors.Bg)
            .border(
                BorderStroke(1.dp, if (selected) RudiColors.BrickBright else RudiColors.Line),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (selected) RudiColors.Brick else RudiColors.Line),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = RudiColors.Text,
            )
            Text(
                text = stringResource(
                    if (profile.latencyCalibrated) {
                        R.string.settings_output_row_measured
                    } else {
                        R.string.settings_output_row_guessed
                    },
                    profile.latencyMs.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
        }
        Text(
            text = stringResource(profile.kind.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
    }
}

private fun OutputKind.labelRes(): Int = when (this) {
    OutputKind.Default -> R.string.settings_output_kind_default
    OutputKind.Bluetooth -> R.string.settings_output_kind_bluetooth
    OutputKind.Wired -> R.string.settings_output_kind_wired
    OutputKind.Usb -> R.string.settings_output_kind_usb
}

@Composable
private fun RenameProfileDialog(
    profile: OutputProfile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RudiColors.Surface,
        title = { Text(text = stringResource(R.string.settings_output_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(OutputProfile.MAX_NAME_LENGTH) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text(text = stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_cancel))
            }
        },
    )
}
