package com.rudimentor.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.rudimentor.app.telemetry.PracticeLogStore
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.ToolbarScreen
import com.rudimentor.app.ui.component.HelpButton
import com.rudimentor.app.ui.component.MicLevelMeter
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.SettingsSliderRow
import com.rudimentor.app.ui.component.SettingsValueRow
import com.rudimentor.app.ui.component.SettingsWarning
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.util.DevLog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Calibrates the two numbers that decide whether an attempt can be scored at all: how loud
 * a stroke has to be, and how late the microphone hears it.
 *
 * The order on the screen is the order they have to be measured in. The dev.37 field log
 * showed why: in a noisy room the onset detector's adaptive threshold sits on its floor, so
 * it reported room noise (envelope 0.012-0.020) as strokes alongside real ones (0.25-1.02).
 * The latency measurement then took a median over both populations and produced 114 ms
 * instead of the ~270 ms Bluetooth round trip, and the attempt scored 24 % with the real
 * strokes counted as extras (decision 158).
 *
 * So the loudness gate comes first, with a logarithmic meter -- on the linear practice meter
 * the noise was less than a pixel wide -- and only strokes above the gate are ever measured.
 *
 * The two measurements are laid out as two numbered steps, each with its own instructions
 * and its own button, because the first version put one shared instruction at the top of the
 * screen -- "press Start and play on every click" -- above a loudness step that plays nothing
 * at all, and never said that the latency round measures through the gate the first step
 * sets. It read as two mechanisms crossing each other by accident (decision 159).
 *
 * A round ends by itself once it has the strokes it needs, and the whole visit is written to
 * the practice log, so a round the learner is unsure about can be read afterwards instead of
 * guessed at (decision 157).
 *
 * Nothing here asks to be saved. A finished measurement and the loudness gate are written
 * into the selected output profile as soon as they exist, the way the system settings of the
 * phone behave. The screen used to end in an Apply button that only filled the settings
 * draft, so a measurement was lost unless the learner also pressed Save one screen later --
 * two buttons for one intention, and a caption under each explaining the other
 * (decision 166).
 */
@Composable
fun CalibrationScreen(
    latencyMs: Float,
    latencyCalibrated: Boolean,
    micThresholdLevel: Float,
    headphonesConnected: Boolean,
    /**
     * Whether the click sounds on the selected output. With the built-in speaker it does not
     * (decisions 50 and 172), so there is no round trip to measure and the round is closed
     * rather than left to measure the speaker behind text asking for an earcup (decision 178).
     */
    clickSounds: Boolean,
    /** The output profile this round writes into, shown so it is never a surprise. */
    profileName: String,
    buildInfo: BuildInfo,
    /**
     * Measured round trip (null when the round produced none), the stream-start skew it was
     * measured under, and the microphone gate. The skew travels with the number because
     * hits are re-anchored by the skew of their own run, so a round trip is only valid in
     * another run once the difference of the two skews is corrected (decision 164).
     */
    onApply: (Float?, Float?, Float) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val micLab = remember { MicLab() }
    val status by micLab.status.collectAsState()
    val calibration = remember { LatencyCalibration() }
    val probe = remember { ThresholdProbe() }
    var reading by remember { mutableStateOf(calibration.reading()) }
    var mode by remember { mutableStateOf(CalibrationMode.Idle) }
    var audioFailed by remember { mutableStateOf(false) }
    var threshold by remember { mutableFloatStateOf(MicThreshold.clamp(micThresholdLevel)) }
    var probeStrokes by remember { mutableStateOf(0) }
    // What the latency round threw away, and why. The round used to drop a quiet or an
    // out-of-range stroke in complete silence, so a gate set too high looked exactly like
    // a broken counter: the clicks went on forever and "0 of 32" never moved (decision 160).
    var quietStrokes by remember { mutableStateOf(0) }
    var strayStrokes by remember { mutableStateOf(0) }
    // Set when a round was stopped because nothing at all was counted, so the screen can
    // say why instead of leaving the click running (decision 160).
    var stalled by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<ThresholdProbe.Result?>(null) }
    // Loudest onset of the last few seconds, so the meter shows how far above the gate the
    // learner's own strokes actually land.
    var peak by remember { mutableFloatStateOf(0f) }
    // Stream-start skew of the run the measurement is taken in, saved with the number
    // (decision 164).
    var measuredSkewMs by remember { mutableStateOf<Float?>(null) }
    // Guards against writing the same number twice on every recomposition.
    var storedMedianMs by remember { mutableStateOf<Float?>(null) }
    var storedThreshold by remember { mutableFloatStateOf(MicThreshold.clamp(micThresholdLevel)) }

    // One log per visit to the screen: every probe, start, stroke, stop and reset lands in
    // it, and it is written to disk when the learner applies a value or walks away.
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
        // The probe has to hear the room as the detector hears it, so it listens with the
        // gate open; the latency round measures through the gate the learner just set.
        micLab.setMicThresholdLevel(
            if (clickAudible) MicThreshold.softened(threshold) else MicThreshold.MIN_LEVEL,
        )
        val started = micLab.start(scope)
        if (!started) DevLog.error("calibration", "audio engine refused to start")
        audioFailed = !started
        return started
    }

    fun stopEngine() {
        micLab.stop()
        peak = 0f
    }

    // Every stroke the engine reports is a whole round trip -- click written, heard,
    // struck, detected -- because the engine compensates nothing here.
    LaunchedEffect(micLab) {
        micLab.events.collect { event ->
            if (event !is MicLab.MicLabEvent.Hit) return@collect
            if (event.envelope > peak) peak = event.envelope
            when (mode) {
                CalibrationMode.ProbeStrokes -> {
                    val counted = probe.addStroke(event.envelope)
                    probeStrokes = probe.strokeCount
                    telemetry.probeStroke(
                        atMs = elapsedMs(),
                        envelope = event.envelope,
                        counted = counted,
                        count = probeStrokes,
                    )
                }

                CalibrationMode.Latency -> {
                    val skew = status.streamSkewMs
                    if (skew > 0f) measuredSkewMs = skew
                    // A quiet onset is logged and ignored: letting it into the median is
                    // exactly what produced the 114 ms of dev.37 (decision 158).
                    val outcome = if (event.loud) {
                        calibration.add(event.offsetMs)
                    } else {
                        quietStrokes += 1
                        LatencyCalibration.Outcome.Rejected
                    }
                    if (event.loud && outcome == LatencyCalibration.Outcome.Rejected) {
                        strayStrokes += 1
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

                else -> Unit
            }
        }
    }

    // The peak mark fades so a single loud accident does not sit on the meter forever.
    LaunchedEffect(Unit) {
        while (true) {
            delay(PEAK_DECAY_INTERVAL_MS)
            if (peak > 0f) peak *= PEAK_DECAY
        }
    }

    fun stopRound(reason: String) {
        if (mode != CalibrationMode.Latency) return
        stopEngine()
        mode = CalibrationMode.Idle
        telemetry.roundStopped(elapsedMs(), reason)
    }

    fun startRound() {
        quietStrokes = 0
        strayStrokes = 0
        stalled = false
        val started = startEngine(clickAudible = true)
        telemetry.roundStarted(elapsedMs(), started)
        mode = if (started) CalibrationMode.Latency else CalibrationMode.Idle
    }

    fun cancelProbe() {
        if (mode != CalibrationMode.ProbeNoise && mode != CalibrationMode.ProbeStrokes) return
        stopEngine()
        mode = CalibrationMode.Idle
    }

    fun startProbe() {
        probe.reset()
        probeStrokes = 0
        probeResult = null
        val started = startEngine(clickAudible = false)
        telemetry.probeStarted(elapsedMs())
        mode = if (started) CalibrationMode.ProbeNoise else CalibrationMode.Idle
    }

    // A round that counts nothing is stopped instead of clicking forever: a gate set too
    // high, a microphone the pad cannot reach, a phone in a pocket (decision 160).
    LaunchedEffect(mode) {
        if (mode != CalibrationMode.Latency) return@LaunchedEffect
        delay(STALL_TIMEOUT_MS)
        if (mode != CalibrationMode.Latency) return@LaunchedEffect
        if (reading.samples.isEmpty() && reading.skipped == 0) {
            stalled = true
            DevLog.error(
                "calibration",
                "round counted nothing in ${STALL_TIMEOUT_MS / 1000} s, " +
                    "gate $threshold, quiet $quietStrokes, stray $strayStrokes",
            )
            stopRound(CalibrationTelemetry.REASON_STOPPED)
        }
    }

    // The silent half: the room alone, for a couple of seconds, sampled off the live meter.
    LaunchedEffect(mode) {
        if (mode != CalibrationMode.ProbeNoise) return@LaunchedEffect
        val until = System.nanoTime() + ThresholdProbe.NOISE_WINDOW_MS * 1_000_000
        while (System.nanoTime() < until && mode == CalibrationMode.ProbeNoise) {
            probe.addNoise(micLab.status.value.envelope)
            delay(NOISE_SAMPLE_INTERVAL_MS)
        }
        if (mode != CalibrationMode.ProbeNoise) return@LaunchedEffect
        probe.startStrokes()
        telemetry.probeNoise(elapsedMs(), probe.measuredNoise)
        mode = CalibrationMode.ProbeStrokes
    }

    // The loud half is over once it has its strokes: the probe proposes a gate and the
    // slider moves to it, so the learner sees what was decided before applying anything.
    LaunchedEffect(probeStrokes) {
        if (mode != CalibrationMode.ProbeStrokes) return@LaunchedEffect
        val result = probe.result() ?: return@LaunchedEffect
        probeResult = result
        threshold = result.thresholdLevel
        telemetry.probeFinished(elapsedMs(), result)
        DevLog.log(
            "calibration",
            "gate probe: noise ${result.noiseLevel} stroke ${result.strokeLevel} " +
                "-> ${result.thresholdLevel} (separated ${result.separated})",
        )
        stopEngine()
        mode = CalibrationMode.Idle
    }

    // The round is over when it has its strokes: the click stops on its own, so the
    // learner is never left playing into a counter that has stopped counting.
    LaunchedEffect(reading.finished, mode) {
        if (reading.finished && mode == CalibrationMode.Latency) {
            DevLog.log(
                "calibration",
                "round complete at ${reading.samples.size} strokes, " +
                    "median ${reading.medianMs?.roundToInt() ?: -1} ms",
            )
            stopRound(CalibrationTelemetry.REASON_COMPLETE)
        }
    }

    // The screen survives the app going to the background, so the click and the
    // microphone have to be closed by hand. The samples stay: the learner comes back
    // to the round they were in the middle of.
    OnBackgrounded {
        if (mode == CalibrationMode.Latency) {
            DevLog.log("calibration", "backgrounded, round stopped")
            stopRound(CalibrationTelemetry.REASON_BACKGROUNDED)
        } else {
            cancelProbe()
        }
    }

    DisposableEffect(micLab) {
        onDispose { micLab.stop() }
    }

    fun leave() {
        stopRound(CalibrationTelemetry.REASON_LEFT)
        cancelProbe()
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
                title = stringResource(R.string.calibration_title),
                onBack = { leave() },
            )
        },
    ) {

        if (!micGranted) {
            PermissionGate(
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
            return@ToolbarScreen
        }

        // The step titles already say what happens in which order, so the paragraphs that
        // used to sit above the controls now live behind the question marks (decision 174).
        SettingsPanel(
            title = stringResource(R.string.calibration_step1_title),
            titleAction = {
                HelpButton(
                    title = stringResource(R.string.check_gate_help_title),
                    body = stringResource(R.string.check_gate_help_body),
                )
            },
        ) {
            // One rule on both screens: the line that says what to do now is visible, and
            // the reason behind it sits under the question mark (decision 177).
            SettingsNote(text = stringResource(R.string.check_gate_action))
            SettingsGap()
            MicLevelMeter(
                envelope = status.envelope,
                peak = peak,
                thresholdLevel = threshold,
                contentDescription = stringResource(R.string.calibration_meter_cd),
            )
            SettingsGap()
            SettingsSliderRow(
                label = stringResource(R.string.calibration_gate_label),
                valueLabel = stringResource(
                    R.string.calibration_gate_value,
                    MicThreshold.decibels(threshold).roundToInt(),
                ),
                value = MicThreshold.toFraction(threshold),
                valueRange = 0f..1f,
                onValueChange = { threshold = MicThreshold.fromFraction(it) },
            )
            SettingsGap()
            GateHint(mode = mode, strokes = probeStrokes, result = probeResult)
            SettingsGap()
            RudiButton(
                text = stringResource(
                    if (mode == CalibrationMode.ProbeNoise ||
                        mode == CalibrationMode.ProbeStrokes
                    ) {
                        R.string.calibration_gate_cancel
                    } else {
                        R.string.calibration_gate_measure
                    },
                ),
                onClick = {
                    if (mode == CalibrationMode.Idle) startProbe() else cancelProbe()
                },
                style = RudiButtonStyle.Secondary,
                enabled = mode != CalibrationMode.Latency,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        SettingsPanel(
            title = stringResource(R.string.calibration_step2_title),
            titleAction = {
                HelpButton(
                    title = stringResource(R.string.check_latency_help_title),
                    body = stringResource(R.string.check_latency_help_body),
                )
            },
        ) {
            if (clickSounds) {
                SettingsNote(text = stringResource(R.string.check_latency_action))
                SettingsGap()
                SettingsNote(text = stringResource(R.string.check_latency_manual))
                SettingsGap()
            } else {
                // Same message the sound check shows, so neither screen invites a
                // measurement the selected output cannot produce (decision 178).
                SettingsWarning(text = stringResource(R.string.check_silent_body))
                SettingsGap()
                SettingsNote(text = stringResource(R.string.check_silent_invite))
                SettingsGap()
            }
            SettingsValueRow(
                label = stringResource(R.string.calibration_strokes_label),
                value = stringResource(
                    R.string.calibration_strokes_value,
                    reading.samples.size,
                    LatencyCalibration.MAX_SAMPLES,
                ),
            )
            SettingsGap()
            SettingsValueRow(
                label = stringResource(R.string.calibration_roundtrip_label),
                value = reading.medianMs?.let {
                    stringResource(R.string.practice_latency_value, it.roundToInt())
                } ?: stringResource(R.string.calibration_pending),
            )
            SettingsGap()
            SettingsValueRow(
                label = stringResource(R.string.calibration_spread_label),
                value = if (reading.ready) {
                    stringResource(
                        R.string.calibration_spread_value,
                        reading.spreadMs.roundToInt(),
                    )
                } else {
                    stringResource(R.string.calibration_pending)
                },
            )
            SettingsGap()
            if (stalled) {
                SettingsNote(text = stringResource(R.string.check_latency_stalled))
                SettingsGap()
            }
            CalibrationHint(
                reading = reading,
                quietStrokes = quietStrokes,
                strayStrokes = strayStrokes,
            )
            SettingsGap()
            SettingsNote(text = stringResource(R.string.calibration_step2_profile, profileName))
            SettingsGap()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RudiButton(
                    text = stringResource(
                        if (mode == CalibrationMode.Latency) {
                            R.string.calibration_stop
                        } else {
                            R.string.calibration_start
                        },
                    ),
                    onClick = {
                        if (mode == CalibrationMode.Latency) {
                            stopRound(CalibrationTelemetry.REASON_STOPPED)
                        } else {
                            startRound()
                        }
                    },
                    // A finished round has to be reset before it can measure again,
                    // otherwise Start would come up on a counter that is already full.
                    enabled = when (mode) {
                        CalibrationMode.Latency -> true
                        CalibrationMode.Idle -> clickSounds && !reading.finished
                        else -> false
                    },
                    modifier = Modifier.weight(1f),
                )
                RudiButton(
                    text = stringResource(R.string.calibration_reset),
                    onClick = {
                        // Reset while a round is running left the engine clicking and the
                        // round open in telemetry: stop it first, then clear.
                        stopRound(CalibrationTelemetry.REASON_STOPPED)
                        telemetry.reset(elapsedMs(), reading.samples.size)
                        quietStrokes = 0
                        strayStrokes = 0
                        calibration.reset()
                        micLab.resetStats()
                        reading = calibration.reading()
                    },
                    style = RudiButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (audioFailed) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingsNote(text = stringResource(R.string.calibration_audio_failed))
        }

        // A finished round is stored the moment it finishes: the number is a measurement,
        // not a preference to be weighed, and the round is the act of choosing it.
        val measuredMs = reading.medianMs
        LaunchedEffect(measuredMs, reading.ready, mode) {
            if (mode != CalibrationMode.Idle) return@LaunchedEffect
            if (!reading.ready || measuredMs == null) return@LaunchedEffect
            if (storedMedianMs == measuredMs) return@LaunchedEffect
            storedMedianMs = measuredMs
            DevLog.log(
                "calibration",
                "stored latency ${measuredMs.roundToInt()} ms from ${reading.samples.size} " +
                    "strokes spread ±${reading.spreadMs.roundToInt()} ms",
            )
            telemetry.applied(
                atMs = elapsedMs(),
                medianMs = measuredMs,
                spreadMs = reading.spreadMs,
                samples = reading.samples.size,
            )
            onApply(measuredMs, measuredSkewMs, threshold)
        }
        // The gate follows a dragged slider too, once the finger has been still for a
        // moment, so a drag is one write instead of a hundred.
        LaunchedEffect(threshold) {
            if (threshold == storedThreshold) return@LaunchedEffect
            delay(GATE_STORE_DELAY_MS)
            storedThreshold = threshold
            telemetry.thresholdApplied(
                atMs = elapsedMs(),
                level = threshold,
                source = if (probeResult == null) SOURCE_SLIDER else SOURCE_PROBE,
            )
            onApply(null, null, threshold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** What the screen is doing with the microphone right now. */
private enum class CalibrationMode { Idle, ProbeNoise, ProbeStrokes, Latency }

/** What the gate panel says: what to do now, or what the probe found. */
@Composable
private fun GateHint(
    mode: CalibrationMode,
    strokes: Int,
    result: ThresholdProbe.Result?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            mode == CalibrationMode.ProbeNoise ->
                SettingsNote(text = stringResource(R.string.check_gate_silence))

            mode == CalibrationMode.ProbeStrokes -> SettingsNote(
                text = stringResource(
                    R.string.check_gate_strokes,
                    strokes,
                    ThresholdProbe.STROKES_NEEDED,
                ),
            )

            result != null && !result.separated ->
                SettingsNote(text = stringResource(R.string.check_gate_noisy))

            result != null -> SettingsNote(
                text = stringResource(
                    R.string.calibration_gate_measured,
                    MicThreshold.decibels(result.noiseLevel).roundToInt(),
                    MicThreshold.decibels(result.strokeLevel).roundToInt(),
                ),
            )

            else -> SettingsNote(text = stringResource(R.string.calibration_gate_hint))
        }
    }
}

/** What the learner should do next: play more, play evener, or take the number. */
@Composable
private fun CalibrationHint(
    reading: LatencyCalibration.Reading,
    quietStrokes: Int,
    strayStrokes: Int,
) {
    val text = when {
        reading.finished -> stringResource(R.string.calibration_complete)
        !reading.ready -> stringResource(
            R.string.calibration_need_more,
            LatencyCalibration.MIN_SAMPLES - reading.samples.size,
        )
        reading.spreadMs > WIDE_SPREAD_MS -> stringResource(R.string.calibration_wide_spread)
        else -> stringResource(R.string.calibration_ready)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsNote(text = text)
        if (reading.skipped > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.calibration_skipped, reading.skipped),
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
        }
        if (quietStrokes > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.check_latency_quiet, quietStrokes),
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Brick,
            )
        }
        if (strayStrokes > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.calibration_stray_dropped, strayStrokes),
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RudiColors.SurfaceAlt, RoundedCornerShape(16.dp))
            .border(1.dp, RudiColors.Line, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.calibration_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Text,
        )
        Spacer(modifier = Modifier.height(14.dp))
        RudiButton(
            text = stringResource(R.string.practice_permission_button),
            onClick = onRequest,
        )
    }
}

/** Wall-clock stamp of the round, the one line a human reads first. */
private fun logStamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

/** Above this scatter the median is not worth trusting, and the screen says so. */
private const val WIDE_SPREAD_MS = 40f

/** A dragged gate settles before it is written, so one drag is one write. */
private const val GATE_STORE_DELAY_MS = 400L

/** How the stored gate came to be, as the log records it. */
private const val SOURCE_PROBE = "probe"
private const val SOURCE_SLIDER = "slider"

/** How often the silent half samples the live envelope. */
private const val NOISE_SAMPLE_INTERVAL_MS = 16L

/** How long a round may count nothing before it stops itself. */
private const val STALL_TIMEOUT_MS = 20_000L

/** Decay of the peak mark on the meter: visible for a few seconds, then gone. */
private const val PEAK_DECAY_INTERVAL_MS = 100L
private const val PEAK_DECAY = 0.94f
