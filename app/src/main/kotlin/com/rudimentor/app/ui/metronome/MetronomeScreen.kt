package com.rudimentor.app.ui.metronome

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.TransportButton
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import com.rudimentor.app.ui.util.formatElapsed

/**
 * The metronome screen.
 *
 * Owns very little state directly. Transport (running, tick, elapsed, error)
 * lives in [MetronomePlaybackState]; every mutation to [AppSettings] is
 * delegated to [MetronomeActions]. What stays here is layout, back-handling,
 * and the `showSettings` toggle for the bottom sheet.
 */
@Composable
fun MetronomeScreen(
    settings: AppSettings,
    buildInfo: BuildInfo,
    actions: MetronomeActions,
    onBack: () -> Unit,
) {
    val playback = rememberMetronomePlaybackState()
    val snapshot = playback.snapshot
    playback.SyncWithSettings(bpm = settings.bpm, grid = settings.grid)

    var showSettings by remember { mutableStateOf(false) }

    val grid = settings.grid
    val position = if (snapshot.running && snapshot.tick > 0) {
        grid.locate((snapshot.tick - 1).toInt())
    } else {
        null
    }
    val visibleRow = position?.row ?: settings.safeActiveRow

    BackHandler {
        playback.stop()
        onBack()
    }

    if (showSettings) {
        SettingsSheet(
            showHandLetters = settings.showHandLetters,
            buildInfo = buildInfo,
            onShowHandLettersChange = actions.showLettersChange,
            onDismiss = { showSettings = false },
        )
    }

    val missingAudioError = stringResource(R.string.metronome_no_audio_output)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppToolbar(
                title = stringResource(R.string.metronome_title),
                onBack = {
                    playback.stop()
                    onBack()
                },
                rightContent = {
                    Text(
                        text = formatElapsed(snapshot.elapsedSeconds),
                        style = RudiTextStyles.Timer,
                        color = if (snapshot.running) RudiColors.Text else RudiColors.Muted,
                    )
                },
            )

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                BeatDrum(
                    grid = grid,
                    activeRow = visibleRow,
                    bpm = settings.bpm,
                    showLetters = settings.showHandLetters,
                    playingRow = position?.row,
                    playingBeat = position?.beat,
                    // Editing stays live during playback: hearing the change is the point.
                    editable = true,
                    rowSwipeEnabled = !snapshot.running,
                    onSelectRow = actions.selectRow,
                    onCycleBeat = actions.cycleBeat,
                    onToggleHand = actions.toggleHand,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The stepper always describes the row in the focus slot, so its
                // value follows the drum instead of a stale stored index.
                val activeLength = grid.rows[visibleRow].size
                DimensionStepper(
                    dimension = Dimension.Beats,
                    value = activeLength,
                    canDecrease = activeLength > BeatRow.MIN_BEATS,
                    canIncrease = activeLength < BeatRow.MAX_BEATS,
                    onDecrease = { actions.removeBeat(visibleRow) },
                    onIncrease = { actions.addBeat(visibleRow) },
                )
                DimensionStepper(
                    dimension = Dimension.Rows,
                    value = grid.rowCount,
                    canDecrease = grid.rowCount > BeatGrid.MIN_ROWS,
                    canIncrease = grid.rowCount < BeatGrid.MAX_ROWS,
                    onDecrease = actions.removeRow,
                    onIncrease = actions.addRow,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            TempoControl(
                bpm = settings.bpm,
                canDecrease = Bpm.canDecrease(settings.bpm),
                canIncrease = Bpm.canIncrease(settings.bpm),
                onDecrease = { actions.bpmDelta(-Bpm.STEP) },
                onIncrease = { actions.bpmDelta(Bpm.STEP) },
            )

            Spacer(modifier = Modifier.height(18.dp))
            snapshot.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = RudiTextStyles.Rubric,
                    color = RudiColors.BrickLit,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            TransportButton(
                playing = snapshot.running,
                onClick = {
                    if (snapshot.running) {
                        playback.stop()
                    } else {
                        playback.start(
                            bpm = settings.bpm,
                            grid = grid,
                            missingAudioError = missingAudioError,
                        )
                    }
                },
            )

            // The control cluster belongs with the drum, not with the settings
            // handle: the leftover height is split so it floats between the two.
            Spacer(modifier = Modifier.weight(0.55f))
            SettingsHandle(onClick = { showSettings = true })
        }
    }
}
