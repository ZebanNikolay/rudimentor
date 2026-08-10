package com.rudimentor.app.ui.metronome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * Bottom sheet with the metronome's screen-level settings and a small build
 * label. Kept in its own file because it has no coupling to the metronome
 * transport and is likely to grow (theme, sound picker, count-in, ...).
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
    val switchCd = stringResource(R.string.metronome_show_hand_letters_cd)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RudiColors.SurfaceAlt,
        scrimColor = RudiColors.Scrim,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.metronome_settings_title),
                style = RudiTextStyles.Rubric,
                color = RudiColors.Muted,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.metronome_show_hand_letters),
                    color = RudiColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = showHandLetters,
                    onCheckedChange = onShowHandLettersChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RudiColors.Text,
                        checkedTrackColor = RudiColors.Brick,
                        checkedBorderColor = RudiColors.BrickLit,
                        uncheckedThumbColor = RudiColors.Muted,
                        uncheckedTrackColor = RudiColors.Surface,
                        uncheckedBorderColor = RudiColors.Line,
                    ),
                    modifier = Modifier.semantics { contentDescription = switchCd },
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = RudiColors.Line)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = buildInfo.displayLabel,
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
            )
        }
    }
}
