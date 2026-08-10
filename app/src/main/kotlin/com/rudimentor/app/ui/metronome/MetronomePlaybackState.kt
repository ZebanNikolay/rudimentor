package com.rudimentor.app.ui.metronome

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.Metronome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Snapshot of everything the screen needs to render the transport row and the
 * playing highlight on the drum. Passed to Compose as a single `State` value.
 */
data class PlaybackSnapshot(
    val running: Boolean,
    val tick: Long,
    val elapsedSeconds: Int,
    val errorMessage: String?,
)

/**
 * Stable holder that owns the [Metronome] instance and the transport-related
 * UI state. Keeping this outside the composable removes ~40 lines of ad-hoc
 * `remember`/`LaunchedEffect` bookkeeping from [MetronomeScreen] and gives the
 * transport a well-defined lifecycle: start, stop, and one dispose.
 *
 * The class is `@Stable` because its public surface only exposes read-only
 * accessors and idempotent commands; recomposition can skip anything that only
 * reads the holder itself.
 */
@Stable
class MetronomePlaybackState internal constructor(
    private val metronome: Metronome,
    private val scope: CoroutineScope,
    private val elapsedClock: () -> Long,
) {
    private var running by mutableStateOf(false)
    private var tick by mutableLongStateOf(0L)
    private var elapsedSeconds by mutableIntStateOf(0)
    private var errorMessage by mutableStateOf<String?>(null)

    private var elapsedJob: Job? = null
    private var startElapsedMillis: Long = 0L

    /** Snapshot for composables to read. */
    val snapshot: PlaybackSnapshot
        get() = PlaybackSnapshot(running, tick, elapsedSeconds, errorMessage)

    internal fun onTick(value: Long) {
        tick = value
    }

    fun setBpm(bpm: Int) = metronome.setBpm(bpm)

    fun setGrid(grid: BeatGrid) = metronome.setGrid(grid)

    /**
     * Starts playback with the current bpm/grid already pushed to the engine.
     * The elapsed timer runs off a monotonic clock so a paused foreground or
     * a dropped delay tick cannot bleed off seconds.
     */
    fun start(bpm: Int, grid: BeatGrid, missingAudioError: String) {
        metronome.setBpm(bpm)
        metronome.setGrid(grid)
        val started = metronome.start()
        if (!started) {
            errorMessage = missingAudioError
            return
        }
        errorMessage = null
        running = true
        startElapsedMillis = elapsedClock()
        elapsedSeconds = 0
        elapsedJob?.cancel()
        elapsedJob = scope.launch {
            while (true) {
                delay(200L)
                elapsedSeconds = ((elapsedClock() - startElapsedMillis) / 1_000L).toInt()
            }
        }
    }

    fun stop() {
        elapsedJob?.cancel()
        elapsedJob = null
        metronome.stop()
        running = false
        tick = 0L
        elapsedSeconds = 0
    }

    internal fun dispose() {
        stop()
    }
}

/**
 * Create and remember a [MetronomePlaybackState] bound to the composition. The
 * engine is stopped when the composable leaves the tree, and tick collection
 * runs for the lifetime of the holder.
 */
@Composable
fun rememberMetronomePlaybackState(): MetronomePlaybackState {
    val scope = rememberCoroutineScope()
    val holder = remember(scope) {
        val metronome = Metronome(scope)
        MetronomePlaybackState(
            metronome = metronome,
            scope = scope,
            elapsedClock = { SystemClock.elapsedRealtime() },
        ).also { state ->
            scope.launch {
                metronome.ticks.collect { state.onTick(it) }
            }
        }
    }
    DisposableEffect(holder) {
        onDispose { holder.dispose() }
    }
    return holder
}

/** Push settings changes to the native engine while the screen is composed. */
@Composable
fun MetronomePlaybackState.SyncWithSettings(bpm: Int, grid: BeatGrid) {
    LaunchedEffect(grid) { setGrid(grid) }
    LaunchedEffect(bpm) { setBpm(bpm) }
}
