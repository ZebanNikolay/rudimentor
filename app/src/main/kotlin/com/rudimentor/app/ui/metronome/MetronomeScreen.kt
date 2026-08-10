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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.Metronome
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlinx.coroutines.delay

@Composable
fun MetronomeScreen(
    settings: AppSettings,
    buildInfo: BuildInfo,
    onCycleBeat: (Int) -> Unit,
    onToggleHand: (Int) -> Unit,
    onAddBeat: () -> Unit,
    onRemoveBeat: () -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: () -> Unit,
    onSelectRow: (Int) -> Unit,
    onBpmDelta: (Int) -> Unit,
    onShowLettersChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val metronome = remember(scope) { Metronome(scope) }
    var running by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val grid = settings.grid
    val position = if (running && tick > 0) grid.locate((tick - 1).toInt()) else null
    val visibleRow = position?.row ?: settings.safeActiveRow

    fun stop() {
        metronome.stop()
        running = false
        tick = 0
        elapsedSeconds = 0
    }

    BackHandler {
        stop()
        onBack()
    }
    DisposableEffect(metronome) {
        onDispose { metronome.stop() }
    }
    LaunchedEffect(metronome) {
        metronome.ticks.collect { tick = it }
    }
    LaunchedEffect(grid) {
        metronome.setGrid(grid)
    }
    LaunchedEffect(settings.bpm) {
        metronome.setBpm(settings.bpm)
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }

    if (showSettings) {
        SettingsSheet(
            showHandLetters = settings.showHandLetters,
            buildInfo = buildInfo,
            onShowHandLettersChange = onShowLettersChange,
            onDismiss = { showSettings = false },
        )
    }

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "RUDIMENTOR",
                    style = RudiTextStyles.Rubric,
                    color = RudiColors.Muted,
                )
                Text(
                    text = formatElapsed(elapsedSeconds),
                    style = RudiTextStyles.Timer,
                    color = if (running) RudiColors.Text else RudiColors.Muted,
                )
            }

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
                    editable = !running,
                    onSelectRow = onSelectRow,
                    onCycleBeat = onCycleBeat,
                    onToggleHand = onToggleHand,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val activeLength = grid.rows[settings.safeActiveRow].size
                DimensionStepper(
                    dimension = Dimension.Beats,
                    value = activeLength,
                    canDecrease = activeLength > BeatRow.MIN_BEATS,
                    canIncrease = activeLength < BeatRow.MAX_BEATS,
                    onDecrease = onRemoveBeat,
                    onIncrease = onAddBeat,
                )
                DimensionStepper(
                    dimension = Dimension.Rows,
                    value = grid.rowCount,
                    canDecrease = grid.rowCount > BeatGrid.MIN_ROWS,
                    canIncrease = grid.rowCount < BeatGrid.MAX_ROWS,
                    onDecrease = onRemoveRow,
                    onIncrease = onAddRow,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            TempoControl(
                bpm = settings.bpm,
                canDecrease = Bpm.canDecrease(settings.bpm),
                canIncrease = Bpm.canIncrease(settings.bpm),
                onDecrease = { onBpmDelta(-Bpm.STEP) },
                onIncrease = { onBpmDelta(Bpm.STEP) },
            )

            Spacer(modifier = Modifier.height(18.dp))
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = RudiTextStyles.Rubric,
                    color = RudiColors.BrickLit,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            TransportButton(
                playing = running,
                onClick = {
                    if (running) {
                        stop()
                    } else {
                        metronome.setBpm(settings.bpm)
                        metronome.setGrid(grid)
                        running = metronome.start()
                        errorMessage = if (running) null else "No audio output"
                    }
                },
            )

            Spacer(modifier = Modifier.height(6.dp))
            SettingsHandle(onClick = { showSettings = true })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(text = "SETTINGS", style = RudiTextStyles.Rubric, color = RudiColors.Muted)
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "R / L letters",
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
                    modifier = Modifier.semantics {
                        contentDescription = "Show right and left hand letters"
                    },
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

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
