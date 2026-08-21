package com.rudimentor.app.ui.metronome

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.SettingsSwitchRow
import com.rudimentor.app.ui.theme.RudiColors

/**
 * Bottom sheet with the metronome's screen-level settings and a small build
 * label. The rows come from the shared [SettingsPanel] so the metronome and the
 * practice screen cannot drift apart (decision 98); only the host differs -- a
 * bottom sheet here, the side drawer in landscape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    showHandLetters: Boolean,
    buildInfo: BuildInfo,
    onShowHandLettersChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RudiColors.SurfaceAlt,
        scrimColor = RudiColors.Scrim,
    ) {
        SettingsPanel(
            title = stringResource(R.string.metronome_settings_title),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            buildLabel = buildInfo.displayLabel,
        ) {
            SettingsSwitchRow(
                label = stringResource(R.string.metronome_show_hand_letters),
                checked = showHandLetters,
                onCheckedChange = onShowHandLettersChange,
                contentDescription = stringResource(R.string.metronome_show_hand_letters_cd),
            )
        }
    }
}
