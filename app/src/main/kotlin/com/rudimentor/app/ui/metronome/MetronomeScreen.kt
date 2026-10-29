package com.rudimentor.app.ui.metronome

import androidx.activity.compose.BackHandler
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.util.OnForegrounded
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.SettingsHandle
import com.rudimentor.app.ui.component.SettingsHandleEdge
import com.rudimentor.app.ui.component.TransportButton
import com.rudimentor.app.ui.component.TransportSize
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import com.rudimentor.app.ui.util.KeepScreenOn
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.ui.util.formatElapsed
import com.rudimentor.app.util.AppLog

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

    // Keep the screen awake while the metronome is running, through the one helper every
    // playing screen shares (decisions 46 and 198).
    KeepScreenOn(active = snapshot.running)

    // The screen is not disposed when the app goes to the background, so the click
    // would keep playing behind another app. There is no foreground service here,
    // so the honest behaviour is to stop.
    OnBackgrounded {
        if (snapshot.running) {
            AppLog.trace("metronome") { "backgrounded while running, stopped" }
            playback.stop()
        }
    }

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
    val lostAudioError = stringResource(R.string.metronome_audio_lost)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    MetronomeOrientation(landscape = landscape)

    val back = {
        playback.stop()
        onBack()
    }
    val toolbar: @Composable () -> Unit = {
        AppToolbar(
            title = stringResource(R.string.metronome_title),
            onBack = back,
            rightContent = {
                Text(
                    text = formatElapsed(snapshot.elapsedSeconds),
                    style = RudiTextStyles.Timer,
                    color = if (snapshot.running) RudiColors.Text else RudiColors.Muted,
                )
            },
        )
    }
    val drum: @Composable (Float) -> Unit = { scale ->
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
            sizeScale = scale,
        )
    }
    val steppers: @Composable () -> Unit = {
        Row(
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
    }
    val tempo: @Composable () -> Unit = {
        TempoControl(
            bpm = settings.bpm,
            canDecrease = Bpm.canDecrease(settings.bpm),
            canIncrease = Bpm.canIncrease(settings.bpm),
            onDecrease = { actions.bpmDelta(-Bpm.STEP) },
            onIncrease = { actions.bpmDelta(Bpm.STEP) },
        )
    }
    val transport: @Composable () -> Unit = {
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
            // The large size lives here and nowhere else (decision 102).
            size = TransportSize.Large,
            onClick = {
                if (snapshot.running) {
                    playback.stop()
                } else {
                    playback.start(
                        bpm = settings.bpm,
                        grid = grid,
                        missingAudioError = missingAudioError,
                        lostAudioError = lostAudioError,
                    )
                }
            },
        )
    }

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        if (landscape) {
            // Landscape (decision 234): the drum takes the whole left side and grows to the
            // height it has there, the controls stand in one column on the right, and the settings
            // handle turns on its side on the trailing edge, as on the practice stage.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .stageSafePadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    toolbar()
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        drum(drumScale(maxHeight, grid.rowCount))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // The column is as wide as its widest control and no wider. The tempo row
                // fills the width it is given, so left unbounded it took the whole screen
                // and squeezed the drum to nothing (2026-10-30).
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    steppers()
                    Spacer(modifier = Modifier.height(14.dp))
                    tempo()
                    Spacer(modifier = Modifier.height(14.dp))
                    transport()
                }
                SettingsHandle(
                    onClick = { showSettings = true },
                    edge = SettingsHandleEdge.End,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                toolbar()
                Spacer(modifier = Modifier.height(10.dp))
                // Portrait grows the drum too: with a few beats the pads hit their size cap
                // long before the width ran out, and the height above the controls sat empty.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    drum(drumScale(maxHeight, grid.rowCount))
                }
                Spacer(modifier = Modifier.height(10.dp))
                steppers()
                Spacer(modifier = Modifier.height(18.dp))
                tempo()
                Spacer(modifier = Modifier.height(18.dp))
                transport()
                // The control cluster belongs with the drum, not with the settings
                // handle: the leftover height is split so it floats between the two.
                Spacer(modifier = Modifier.weight(0.55f))
                SettingsHandle(
                    onClick = { showSettings = true },
                    edge = SettingsHandleEdge.Bottom,
                )
            }
        }
    }
}

/**
 * The metronome follows the phone into either orientation, even with auto-rotate off --
 * the same way the practice stage takes landscape on its own (decision 234). In landscape
 * the system bars step aside like on the stage; a swipe brings them back.
 */
@Composable
private fun MetronomeOrientation(landscape: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val window = activity?.window
    DisposableEffect(window, landscape) {
        if (window == null || !landscape) return@DisposableEffect onDispose { }
        val insets = WindowInsetsControllerCompat(window, window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insets.show(WindowInsetsCompat.Type.systemBars()) }
    }
    OnForegrounded {
        if (window == null || !landscape) return@OnForegrounded
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * How much the drum may grow to fill [height]: the slots take the height, the pads follow
 * them, and the width still caps a long row inside [BeatRowPads] (decision 234). Never
 * below the base size, so a short screen keeps the drum it always had.
 */
private fun drumScale(height: Dp, rowCount: Int): Float {
    val slots = if (rowCount == 1) 1f else DrumTuning.VISIBLE_SLOTS.toFloat()
    return (height / (RudiDimens.DrumSlotHeight * slots)).coerceIn(1f, MAX_DRUM_SCALE)
}

/** Past this the pads stop reading as a grid and become tiles. */
private const val MAX_DRUM_SCALE = 2.2f
