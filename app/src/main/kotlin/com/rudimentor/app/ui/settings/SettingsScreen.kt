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
import com.rudimentor.app.ui.component.SettingsCard
import com.rudimentor.app.ui.component.SettingsCardDivider
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.SettingsSwitchRow
import com.rudimentor.app.ui.component.SettingsValueRow
import com.rudimentor.app.ui.component.SettingsWarning
import com.rudimentor.app.ui.component.WarningBanner
import com.rudimentor.app.ui.theme.RudiColors
import kotlin.math.roundToInt

/**
 * The settings of the app, off the main menu.
 *
 * Every change on this screen takes effect at once, the way the system settings of the phone
 * behave, and the back arrow simply closes it. There is no Save and no Cancel: the earlier
 * draft-and-Save contract (decision 154) meant a measurement taken on the calibration screen
 * had to be confirmed a second time on this one, so it produced two buttons for one
 * intention and a caption under each explaining the other (decision 166). A change here is
 * one tap to make and one tap to undo, so there is nothing a transaction protects.
 *
 * The drawer during an attempt behaves the same way, so a setting means the same thing in
 * both places.
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
    currentOutput: OutputDevice?,
    buildInfo: BuildInfo,
    onDraftChange: (SettingsDraft) -> Unit,
    onCalibrate: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }

    ToolbarScreen(
        toolbar = {
            AppToolbar(
                title = stringResource(R.string.settings_title),
                onBack = onClose,
            )
        },
    ) {
        // Two groups, told apart by the card: above it the settings that are a matter of
        // taste and hold everywhere, inside it everything the sound check measured for one
        // output. The screen used to be two look-alike lists, so there was no telling which
        // value would follow a change of headphones (decision 173).
        SettingsPanel(
            title = stringResource(R.string.settings_practice_title),
            buildLabel = buildInfo.displayLabel,
        ) {
            // The verdict word is the default; the millisecond offset is for tuning
            // the ear and the latency, so it is opt-in (decision 130).
            SettingsSwitchRow(
                label = stringResource(R.string.practice_offset_ms_label),
                checked = draft.showOffsetMs,
                onCheckedChange = { onDraftChange(draft.copy(showOffsetMs = it)) },
            )
            SettingsNote(text = stringResource(R.string.practice_offset_ms_note))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutputProfileCard(
            draft = draft,
            currentOutput = currentOutput,
            onDraftChange = onDraftChange,
            onCalibrate = onCalibrate,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Everything that belongs to one output: which output it is, and what was measured for it.
 *
 * The list is short on purpose -- the built-in profile plus two -- and the choice is made by
 * hand here, because Android cannot tell the app which of several attached outputs it is
 * really routing to. Calibration writes into whichever profile is selected here, and so do
 * the click switch and the microphone gate: a wired headset records through its own
 * microphone, so a gate measured on the phone does not hold there (decision 172).
 */
@Composable
private fun OutputProfileCard(
    draft: SettingsDraft,
    currentOutput: OutputDevice?,
    onDraftChange: (SettingsDraft) -> Unit,
    onCalibrate: () -> Unit,
) {
    var renaming by remember { mutableStateOf<OutputProfile?>(null) }
    // Deleting throws away a measured latency that took a minute to get; one tap next to
    // Rename should not be enough (decision 212).
    var deleting by remember { mutableStateOf<OutputProfile?>(null) }
    val selected = draft.selectedProfile

    SettingsCard(title = stringResource(R.string.settings_output_card_title, selected.name)) {
        draft.outputProfiles.forEach { profile ->
            OutputProfileRow(
                profile = profile,
                selected = profile.id == draft.selectedProfileId,
                onSelect = { onDraftChange(draft.withSelectedProfile(profile.id)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
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
                onClick = { deleting = selected },
                style = RudiButtonStyle.Secondary,
                enabled = selected.removable,
                modifier = Modifier.weight(1f),
            )
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

        SettingsCardDivider(text = stringResource(R.string.settings_output_measured_for))

        // Over the built-in output the microphone hears the click and scores it as a stroke,
        // which is the one thing on this screen worth a loud caption (decision 173) -- and,
        // since the dev.49 speaker log, worth the full panel rather than a caption
        // (decision 192). The switch is left alone: a learner with an odd routing setup may
        // have a reason, and a disabled switch cannot explain itself.
        SettingsSwitchRow(
            label = stringResource(R.string.practice_click_label),
            checked = draft.clickAudible,
            onCheckedChange = { onDraftChange(draft.withClickAudible(it)) },
        )
        // Either half is enough: the speaker profile is being edited, or nothing private is
        // attached right now, so the click leaves through the speaker whatever the profile says.
        if (draft.clickAudible && (selected.kind == OutputKind.Default || currentOutput == null)) {
            SettingsGap()
            WarningBanner(
                title = stringResource(R.string.practice_click_warning_title),
                body = stringResource(R.string.practice_click_warning),
                helpTitle = stringResource(R.string.practice_click_warning_help_title),
                helpBody = stringResource(R.string.practice_click_warning_help_body),
            )
        }

        SettingsGap()
        // Both measured numbers are read here and changed on the check, where each one has
        // the feedback that makes changing it meaningful: the gate has the live meter, the
        // latency has the round. A slider here moved one of them blind (decision 180).
        SettingsValueRow(
            label = stringResource(R.string.settings_latency_label),
            value = stringResource(
                R.string.practice_latency_value,
                draft.latencyMs.roundToInt(),
            ),
        )
        // The second half of the path, shown next to the whole: one number could not say
        // where the correction belongs, and while it did not, a level played by eye scored
        // zero (decision 188).
        SettingsValueRow(
            label = stringResource(R.string.settings_mic_share_label),
            value = if (draft.micLatencyMs > 0f) {
                stringResource(
                    R.string.practice_latency_value,
                    draft.micLatencyMs.roundToInt(),
                )
            } else {
                stringResource(R.string.settings_mic_share_unknown)
            },
        )
        SettingsValueRow(
            label = stringResource(R.string.settings_gate_label),
            value = stringResource(
                R.string.settings_gate_value,
                MicThreshold.decibels(draft.micThresholdLevel).roundToInt(),
            ),
        )
        SettingsGap()
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
        SettingsNote(text = stringResource(R.string.settings_latency_split_note))
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

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = RudiColors.Surface,
            title = { Text(text = stringResource(R.string.settings_output_delete_title)) },
            text = { Text(text = stringResource(R.string.settings_output_delete_body, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDraftChange(draft.withoutProfile(profile.id))
                        deleting = null
                    },
                ) {
                    Text(text = stringResource(R.string.settings_output_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(text = stringResource(R.string.settings_cancel))
                }
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
