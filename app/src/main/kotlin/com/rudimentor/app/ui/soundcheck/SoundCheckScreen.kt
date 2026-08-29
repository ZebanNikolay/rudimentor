package com.rudimentor.app.ui.soundcheck

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.audio.LatencyCalibration
import com.rudimentor.app.audio.MicLab
import com.rudimentor.app.audio.MicThreshold
import com.rudimentor.app.audio.ThresholdProbe
import com.rudimentor.app.telemetry.CalibrationHeader
import com.rudimentor.app.telemetry.CalibrationTelemetry
import com.rudimentor.app.telemetry.FLOW_SOUND_CHECK
import com.rudimentor.app.telemetry.PracticeLogStore
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.MicLevelMeter
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.ToolbarScreen
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.util.DevLog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The three steps of the sound check, in the order they are walked.
 *
 * The order is a technical constraint, not a preference: the latency round only counts
 * strokes that clear the loudness gate, so the gate has to be measured before the click
 * round can be trusted. That is why the pad step runs first even though the headphones are
 * what the player came to check (decision 169).
 */
enum class SoundCheckStep { Pad, Headphones, FirstClick }

/** What the microphone is being used for right now. */
private enum class Stage { Idle, ProbeNoise, ProbeStrokes, Latency, Play }

/**
 * The onboarding node of the level map: it proves the phone can hear the pad, measures how
 * late the click reaches the player, and lets them play eight strokes with nothing at stake.
 *
 * Two things this screen deliberately does not do. It never says the word "latency" and
 * never shows a millisecond figure to the player: the number is a fact about the headphones,
 * and every attempt to explain it turned the first minute of the app into a lecture. And it
 * never scores anything -- the third step exists so the first click of the player's life
 * happens without a verdict on it.
 *
 * The engines are the ones the calibration screen uses; only the wrapping is new, so a fix
 * to the measurement lands in both places at once.
 */
@Composable
fun SoundCheckScreen(
    /** Output the measurement will be written for, shown so it is never a surprise. */
    profileName: String,
    micThresholdLevel: Float,
    /** Compensation stored before this visit, logged so two visits can be compared. */
    latencyMs: Float,
    latencyCalibrated: Boolean,
    headphonesConnected: Boolean,
    buildInfo: BuildInfo,
    /** Start on this step: a freshly connected pair of headphones needs only its own step. */
    startStep: SoundCheckStep = SoundCheckStep.Pad,
    /** Round trip and the skew it was measured under (both null when nothing was measured), gate. */
    onApply: (Float?, Float?, Float) -> Unit,
    /** Called once the last step is walked, so the map can mark the node as done. */
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val micLab = remember { MicLab() }
    val status by micLab.status.collectAsState()
    val probe = remember { ThresholdProbe() }
    val calibration = remember { LatencyCalibration() }

    var step by remember { mutableStateOf(startStep) }
    var stage by remember { mutableStateOf(Stage.Idle) }
    var gate by remember { mutableFloatStateOf(MicThreshold.clamp(micThresholdLevel)) }
    var probeStrokes by remember { mutableIntStateOf(0) }
    var probeSeparated by remember { mutableStateOf(true) }
    var reading by remember { mutableStateOf(calibration.reading()) }
    var quietStrokes by remember { mutableIntStateOf(0) }
    var playStrokes by remember { mutableIntStateOf(0) }
    var skewMs by remember { mutableStateOf<Float?>(null) }
    var stalled by remember { mutableStateOf(false) }
    var audioFailed by remember { mutableStateOf(false) }
    var peak by remember { mutableFloatStateOf(0f) }
    // Steps already walked in this visit, so the header can show progress and the last step
    // knows it is the last one.
    var padDone by remember { mutableStateOf(startStep != SoundCheckStep.Pad) }
    var headphonesDone by remember { mutableStateOf(false) }

    // One log per visit, written the same way a calibration round is: the sound check used to
    // leave two DevLog lines behind, so a walk-through that felt wrong could not be read back
    // afterwards. It lands in the same list as the attempts, and Share sends it the same way
    // (decision 175).
    val telemetry = remember {
        CalibrationTelemetry(
            header = CalibrationHeader(
                startedAt = logStamp(),
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = Build.VERSION.RELEASE ?: "?",
                build = buildInfo.displayLabel,
                clickBpm = LatencyCalibration.CLICK_BPM,
                warmUpStrokes = LatencyCalibration.WARM_UP_SAMPLES,
                targetStrokes = LatencyCalibration.MAX_SAMPLES,
                headphones = headphonesConnected,
                sensitivity = MicLab.DEFAULT_SENSITIVITY,
                previousLatencyMs = latencyMs,
                previousCalibrated = latencyCalibrated,
                previousThresholdLevel = MicThreshold.clamp(micThresholdLevel),
                audio = null,
                flow = FLOW_SOUND_CHECK,
            ),
        )
    }
    val startedAtNanos = remember { System.nanoTime() }
    var saved by remember { mutableStateOf(false) }

    fun elapsedMs(): Float = (System.nanoTime() - startedAtNanos) / 1_000_000f

    fun saveLog() {
        if (saved) return
        saved = true
        PracticeLogStore.saveCalibration(context, telemetry)
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }

    fun startEngine(clickAudible: Boolean): Boolean {
        micLab.setBpm(LatencyCalibration.CLICK_BPM)
        micLab.setClickAudible(clickAudible)
        micLab.setInputLatencyMs(0f)
        micLab.setSensitivity(MicLab.DEFAULT_SENSITIVITY)
        // The probe listens to the room with the gate open; anything that counts strokes
        // listens through the softened gate, exactly as the calibration screen does.
        micLab.setMicThresholdLevel(
            if (clickAudible) MicThreshold.softened(gate) else MicThreshold.MIN_LEVEL,
        )
        val started = micLab.start(scope)
        audioFailed = !started
        if (!started) DevLog.error("soundcheck", "audio engine refused to start")
        if (clickAudible) {
            telemetry.roundStarted(elapsedMs(), started)
        } else {
            telemetry.probeStarted(elapsedMs())
        }
        return started
    }

    fun stopEngine() {
        micLab.stop()
        peak = 0f
        stage = Stage.Idle
    }

    LaunchedEffect(micLab) {
        micLab.events.collect { event ->
            if (event !is MicLab.MicLabEvent.Hit) return@collect
            if (event.envelope > peak) peak = event.envelope
            when (stage) {
                Stage.ProbeStrokes -> {
                    val counted = probe.addStroke(event.envelope)
                    probeStrokes = probe.strokeCount
                    telemetry.probeStroke(
                        atMs = elapsedMs(),
                        envelope = event.envelope,
                        counted = counted,
                        count = probeStrokes,
                    )
                }

                Stage.Latency -> {
                    val skew = status.streamSkewMs
                    if (skew > 0f) skewMs = skew
                    val outcome = if (event.loud) {
                        calibration.add(event.offsetMs)
                    } else {
                        quietStrokes += 1
                        LatencyCalibration.Outcome.Rejected
                    }
                    reading = calibration.reading()
                    telemetry.stroke(
                        atMs = elapsedMs(),
                        roundTripMs = event.offsetMs,
                        outcome = outcome,
                        medianMs = reading.medianMs,
                        spreadMs = reading.spreadMs,
                        samples = reading.samples.size,
                        envelope = event.envelope,
                        threshold = event.threshold,
                        loud = event.loud,
                    )
                }

                Stage.Play -> if (event.loud) {
                    playStrokes += 1
                    // The third step scores nothing, but a stroke that is not heard here is
                    // the same failure a level would show, so it goes in the log too.
                    telemetry.probeStroke(
                        atMs = elapsedMs(),
                        envelope = event.envelope,
                        counted = true,
                        count = playStrokes,
                    )
                }

                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(PEAK_DECAY_INTERVAL_MS)
            if (peak > 0f) peak *= PEAK_DECAY
        }
    }

    // The silent half of the pad step: the room alone, sampled off the live meter.
    LaunchedEffect(stage) {
        if (stage != Stage.ProbeNoise) return@LaunchedEffect
        val until = System.nanoTime() + ThresholdProbe.NOISE_WINDOW_MS * 1_000_000
        while (System.nanoTime() < until && stage == Stage.ProbeNoise) {
            probe.addNoise(micLab.status.value.envelope)
            delay(NOISE_SAMPLE_INTERVAL_MS)
        }
        if (stage != Stage.ProbeNoise) return@LaunchedEffect
        probe.startStrokes()
        stage = Stage.ProbeStrokes
    }

    // The loud half is over once the probe has its strokes: the gate is decided and kept.
    LaunchedEffect(probeStrokes) {
        if (stage != Stage.ProbeStrokes) return@LaunchedEffect
        val result = probe.result() ?: return@LaunchedEffect
        gate = result.thresholdLevel
        probeSeparated = result.separated
        DevLog.log(
            "soundcheck",
            "gate ${result.thresholdLevel} from room ${result.noiseLevel} " +
                "stroke ${result.strokeLevel} separated ${result.separated}",
        )
        telemetry.probeFinished(elapsedMs(), result)
        stopEngine()
        // The gate is worth keeping even if the player walks away here: it is the part of
        // the check that makes every later attempt scoreable.
        telemetry.thresholdApplied(elapsedMs(), gate, "sound check probe")
        onApply(null, null, gate)
        padDone = true
    }

    // The click round ends itself once it holds its strokes, so nobody plays into a counter
    // that has stopped counting.
    LaunchedEffect(reading.finished, stage) {
        if (!reading.finished || stage != Stage.Latency) return@LaunchedEffect
        val median = reading.medianMs
        DevLog.log("soundcheck", "round complete, median ${median?.roundToInt() ?: -1} ms")
        telemetry.roundStopped(elapsedMs(), CalibrationTelemetry.REASON_COMPLETE)
        stopEngine()
        if (median != null) {
            telemetry.applied(
                atMs = elapsedMs(),
                medianMs = median,
                spreadMs = reading.spreadMs,
                samples = reading.samples.size,
            )
        }
        onApply(median, skewMs, gate)
        headphonesDone = true
    }

    // A round that counts nothing is stopped instead of clicking forever (decision 160).
    LaunchedEffect(stage) {
        if (stage != Stage.Latency) return@LaunchedEffect
        delay(STALL_TIMEOUT_MS)
        if (stage != Stage.Latency) return@LaunchedEffect
        if (reading.samples.isEmpty() && reading.skipped == 0) {
            stalled = true
            DevLog.error(
                "soundcheck",
                "round counted nothing, gate $gate, quiet $quietStrokes",
            )
            telemetry.roundStopped(elapsedMs(), CalibrationTelemetry.REASON_STOPPED)
            stopEngine()
        }
    }

    // Eight strokes with the click is the whole third step; there is nothing to score.
    LaunchedEffect(playStrokes) {
        if (stage != Stage.Play || playStrokes < PLAY_STROKES) return@LaunchedEffect
        stopEngine()
    }

    OnBackgrounded {
        if (stage == Stage.Latency) {
            telemetry.roundStopped(elapsedMs(), CalibrationTelemetry.REASON_BACKGROUNDED)
        }
        stopEngine()
    }

    DisposableEffect(micLab) {
        onDispose { micLab.stop() }
    }

    fun leave() {
        if (stage == Stage.Latency) {
            telemetry.roundStopped(elapsedMs(), CalibrationTelemetry.REASON_LEFT)
        }
        stopEngine()
        telemetry.abandoned(
            atMs = elapsedMs(),
            medianMs = reading.medianMs,
            spreadMs = reading.spreadMs,
            samples = reading.samples.size,
        )
        saveLog()
        onBack()
    }

    BackHandler { leave() }

    ToolbarScreen(
        toolbar = {
            AppToolbar(
                title = stringResource(R.string.sound_check_title),
                onBack = { leave() },
            )
        },
    ) {
        if (!micGranted) {
            SettingsPanel(title = stringResource(R.string.sound_check_permission_title)) {
                SettingsNote(text = stringResource(R.string.sound_check_permission_body))
                SettingsGap()
                RudiButton(
                    text = stringResource(R.string.sound_check_permission_grant),
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
            }
            return@ToolbarScreen
        }

        Text(
            text = stringResource(
                R.string.sound_check_step_of,
                step.ordinal + 1,
                SoundCheckStep.entries.size,
                stringResource(step.titleRes),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (step) {
            SoundCheckStep.Pad -> SettingsPanel(
                title = stringResource(R.string.sound_check_pad_title),
            ) {
                SettingsNote(text = stringResource(R.string.sound_check_pad_intro))
                SettingsGap()
                MicLevelMeter(
                    envelope = status.envelope,
                    peak = peak,
                    thresholdLevel = gate,
                    contentDescription = stringResource(R.string.sound_check_meter_cd),
                )
                SettingsGap()
                Text(
                    text = when {
                        audioFailed -> stringResource(R.string.sound_check_audio_failed)
                        stage == Stage.ProbeNoise -> stringResource(R.string.sound_check_pad_silence)
                        stage == Stage.ProbeStrokes -> stringResource(
                            R.string.sound_check_pad_strokes,
                            probeStrokes,
                            ThresholdProbe.STROKES_NEEDED,
                        )

                        padDone && !probeSeparated ->
                            stringResource(R.string.sound_check_pad_noisy)

                        padDone -> stringResource(R.string.sound_check_pad_done)
                        else -> stringResource(R.string.sound_check_pad_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RudiColors.Muted,
                )
                SettingsGap()
                StepActions(
                    running = stage == Stage.ProbeNoise || stage == Stage.ProbeStrokes,
                    canAdvance = padDone,
                    startLabel = stringResource(
                        if (padDone) R.string.sound_check_again else R.string.sound_check_start,
                    ),
                    onStart = {
                        probe.reset()
                        probeStrokes = 0
                        if (startEngine(clickAudible = false)) stage = Stage.ProbeNoise
                    },
                    onStop = { stopEngine() },
                    onNext = { step = SoundCheckStep.Headphones },
                )
            }

            SoundCheckStep.Headphones -> SettingsPanel(
                title = stringResource(R.string.sound_check_headphones_title),
            ) {
                SettingsNote(text = stringResource(R.string.sound_check_headphones_intro))
                SettingsGap()
                // The manual way still works and is what a learner without a free hand
                // will do, so it is offered here rather than hidden (decision 176).
                SettingsNote(text = stringResource(R.string.sound_check_headphones_manual))
                SettingsGap()
                Progress(
                    done = reading.samples.size,
                    total = LatencyCalibration.MAX_SAMPLES,
                )
                SettingsGap()
                Text(
                    text = when {
                        audioFailed -> stringResource(R.string.sound_check_audio_failed)
                        stalled -> stringResource(R.string.sound_check_headphones_stalled)
                        stage == Stage.Latency -> stringResource(
                            R.string.sound_check_headphones_playing,
                            reading.samples.size,
                            LatencyCalibration.MAX_SAMPLES,
                        )

                        headphonesDone -> stringResource(
                            R.string.sound_check_headphones_done,
                            profileName,
                        )

                        else -> stringResource(R.string.sound_check_headphones_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RudiColors.Muted,
                )
                if (quietStrokes > 0 && !headphonesDone) {
                    SettingsGap()
                    Text(
                        text = stringResource(R.string.sound_check_quiet, quietStrokes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RudiColors.Muted,
                    )
                }
                SettingsGap()
                StepActions(
                    running = stage == Stage.Latency,
                    canAdvance = headphonesDone,
                    startLabel = stringResource(
                        if (headphonesDone) R.string.sound_check_again else R.string.sound_check_start,
                    ),
                    onStart = {
                        calibration.reset()
                        reading = calibration.reading()
                        quietStrokes = 0
                        stalled = false
                        if (startEngine(clickAudible = true)) stage = Stage.Latency
                    },
                    onStop = { stopEngine() },
                    onNext = { step = SoundCheckStep.FirstClick },
                )
            }

            SoundCheckStep.FirstClick -> SettingsPanel(
                title = stringResource(R.string.sound_check_play_title),
            ) {
                SettingsNote(text = stringResource(R.string.sound_check_play_intro))
                SettingsGap()
                Progress(done = playStrokes, total = PLAY_STROKES)
                SettingsGap()
                Text(
                    text = when {
                        audioFailed -> stringResource(R.string.sound_check_audio_failed)
                        playStrokes >= PLAY_STROKES -> stringResource(R.string.sound_check_play_done)
                        stage == Stage.Play -> stringResource(R.string.sound_check_play_running)
                        else -> stringResource(R.string.sound_check_play_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RudiColors.Muted,
                )
                SettingsGap()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RudiButton(
                        text = stringResource(
                            if (stage == Stage.Play) {
                                R.string.sound_check_stop
                            } else {
                                R.string.sound_check_start
                            },
                        ),
                        style = RudiButtonStyle.Secondary,
                        onClick = {
                            if (stage == Stage.Play) {
                                stopEngine()
                            } else {
                                playStrokes = 0
                                if (startEngine(clickAudible = true)) stage = Stage.Play
                            }
                        },
                    )
                    RudiButton(
                        text = stringResource(R.string.sound_check_finish),
                        onClick = {
                            stopEngine()
                            saveLog()
                            onFinished()
                        },
                    )
                }
            }
        }
    }
}

private val SoundCheckStep.titleRes: Int
    get() = when (this) {
        SoundCheckStep.Pad -> R.string.sound_check_pad_short
        SoundCheckStep.Headphones -> R.string.sound_check_headphones_short
        SoundCheckStep.FirstClick -> R.string.sound_check_play_short
    }

/** Start / stop on the left, and the way onwards once the step has produced something. */
@Composable
private fun StepActions(
    running: Boolean,
    canAdvance: Boolean,
    startLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RudiButton(
            text = if (running) stringResource(R.string.sound_check_stop) else startLabel,
            style = RudiButtonStyle.Secondary,
            onClick = { if (running) onStop() else onStart() },
        )
        if (canAdvance && !running) {
            RudiButton(text = stringResource(R.string.sound_check_next), onClick = onNext)
        }
    }
}

/**
 * Strokes so far, as dots.
 *
 * A row of dots and not a number of milliseconds: the player has nothing to do with the
 * measurement itself, and a filling row is the one thing they can act on -- keep playing.
 */
@Composable
private fun Progress(done: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(minOf(total, DOT_LIMIT)) { index ->
                val filledDots = if (total <= DOT_LIMIT) {
                    done
                } else {
                    (done.toFloat() / total * DOT_LIMIT).toInt()
                }
                Box(
                    modifier = Modifier
                        .size(DOT_SIZE)
                        .background(
                            color = if (index < filledDots) RudiColors.Brick else RudiColors.Muted,
                            shape = CircleShape,
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sound_check_progress, done, total),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
    }
}

private fun logStamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

/** Strokes the free-play step asks for: enough to feel the click, short enough to not be a level. */
private const val PLAY_STROKES = 8

/** Dots the progress row draws at most, so 32 strokes still fit one line. */
private const val DOT_LIMIT = 8
private val DOT_SIZE = 10.dp

private const val PEAK_DECAY = 0.88f
private const val PEAK_DECAY_INTERVAL_MS = 120L
private const val NOISE_SAMPLE_INTERVAL_MS = 40L

/** How long a click round may count nothing before it is stopped (decision 160). */
private const val STALL_TIMEOUT_MS = 12_000L
