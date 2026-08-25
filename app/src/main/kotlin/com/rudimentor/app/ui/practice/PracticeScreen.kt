package com.rudimentor.app.ui.practice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.audio.MicLab
import com.rudimentor.app.audio.NativeMicLab
import com.rudimentor.app.audio.PracticeSession
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.telemetry.PracticeLogStore
import com.rudimentor.app.telemetry.PracticeTelemetry
import com.rudimentor.app.telemetry.TelemetryAudio
import com.rudimentor.app.telemetry.TelemetryHeader
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.TransportButton
import com.rudimentor.app.ui.component.TransportSize
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.ui.util.OnForegrounded
import com.rudimentor.app.ui.util.formatElapsed
import com.rudimentor.app.util.DevLog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The level attempt: a landscape track that scrolls the notes onto the hit line
 * while the microphone judges the strokes.
 *
 * The screen owns the attempt only. Progress is saved by the caller once the
 * result screen is done with it, so an attempt that is abandoned mid-way leaves no
 * trace (decision 88).
 *
 * There is no pause and no settings here (decision 106). Back leaves for the level
 * at once, without a question; Stop closes the attempt and hands it to the result
 * screen, which is where the click and the latency are tuned before the next try.
 *
 * Every run that started the engine is also written to the practice log: the audio
 * path of the phone, every detector trigger with its envelope, and the result. That
 * is the only way a timing complaint from a real session can be told apart from a
 * detector that fired twice (decision 133).
 */
@Composable
fun PracticeScreen(
    level: Level,
    family: Family,
    rank: PracticeRank,
    bpm: Int,
    clickAudible: Boolean,
    latencyMs: Float,
    /**
     * Whether [latencyMs] came off the calibration screen. A measured value is a whole
     * round trip and must not have the output latency added on top of it a second time
     * (decision 154).
     */
    latencyCalibrated: Boolean,
    showOffsetMs: Boolean,
    buildInfo: BuildInfo,
    headphonesConnected: Boolean,
    onExit: () -> Unit,
    onFinished: (PracticeResult) -> Unit,
) {
    val context = LocalContext.current

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }

    val session = remember { PracticeSession() }
    // The tempo comes from the level card, but the engine has its own range: clamp
    // once here so the track, the click and the scoring all use the same number.
    val tempo = bpm.coerceIn(MicLab.MIN_BPM, MicLab.MAX_BPM)
    val notes = remember(level.id, rank, tempo) { buildPracticeNotes(level, rank, tempo) }
    // The beat grid of the attempt and the tempo the engine plays it at: a tempo ramp
    // changes tempo between phases, so both are per beat (decision 148).
    val beatTimesMs = remember(level.id, rank, tempo) { buildBeatTimesMs(level, rank, tempo) }
    val tempoPlan = remember(level.id, rank, tempo) { buildTempoPlan(level, rank, tempo) }
    // Windows are a property of the note list, not of a hit: computed once, then used by
    // the attempt, the expiry check and the deviation scale alike (decision 125). Each note
    // carries the windows of its own density, so a dense block does not tighten the sparse
    // beats of the same attempt (decision 151).
    val windows = remember(notes) { attemptWindowsFor(notes) }
    val attempt = remember(notes) { PracticeAttempt(notes, windows) }
    val minIntervalMs = remember(notes) { minNoteIntervalMs(notes) ?: 0f }
    // One collector per attempt, created when the engine starts and written once the
    // run is over: a screen that is only opened and left behind logs nothing.
    val telemetry = remember(attempt) { mutableStateOf<PracticeTelemetry?>(null) }
    val beatMs = 60_000f / tempo
    val lastNoteMs = notes.lastOrNull()?.timeMs ?: 0f
    // The finish line rides one beat behind the last note, so the last stroke is what
    // drives it onto the hit line (decision 116, line since decision 130). Clamped so a slow tempo does not
    // leave a long empty lane and a fast one still gives the eye a moment.
    val finishMs = if (lastNoteMs <= 0f) {
        0f
    } else {
        lastNoteMs + beatMs.coerceIn(FINISH_GAP_MIN_MS, FINISH_GAP_MAX_MS)
    }
    // A timed level states its duration as a floor: the attempt plays on to the end of the
    // sticking cycle that floor lands in, so the HUD counts down to it and then says what it
    // is waiting for (decision 154).
    val timedFloorMs = level.durationSeconds?.let { seconds ->
        val startMs = beatTimesMs.getOrNull(PracticeScoring.COUNT_IN_BEATS) ?: return@let null
        startMs + seconds * 1000f
    }
    // The attempt cannot end before the finish line has arrived and held its glow.
    val endMs = maxOf(
        lastNoteMs + windows.widest.okMs + TAIL_MS,
        finishMs + FINISH_HOLD_MS,
    )

    // Nothing before the first note counts: the count-in is played along with, not
    // judged (decision 87).
    val firstJudgedMs = (notes.firstOrNull()?.timeMs ?: 0f) - windows.widest.okMs

    var running by remember(attempt) { mutableStateOf(false) }
    var positionMs by remember(attempt) { mutableFloatStateOf(0f) }
    var frame by remember(attempt) { mutableIntStateOf(0) }
    var envelope by remember { mutableFloatStateOf(0f) }
    var threshold by remember { mutableFloatStateOf(0f) }
    var audioFailed by remember { mutableStateOf(false) }

    DisposableEffect(session) {
        onDispose { session.stop() }
    }

    // A headphone change mid-attempt moves the click in or out of the microphone, so
    // it belongs in the log next to the strokes it changed the meaning of.
    LaunchedEffect(headphonesConnected) {
        telemetry.value?.audioEvent(
            atMs = positionMs,
            kind = "headphones",
            detail = if (headphonesConnected) "connected" else "disconnected",
        )
    }

    // Headphones can be plugged in or pulled out mid-attempt: the engine follows the
    // new click state without restarting the run (decision 114).
    LaunchedEffect(clickAudible, running) {
        if (running) session.setClickAudible(clickAudible)
    }

    // Leaving the app does not dispose the screen, so the engine has to be stopped
    // by hand: otherwise the microphone keeps recording in the background and the
    // timeline keeps running, which scored silence as a wall of misses. Without a
    // pause the attempt is simply dropped, exactly like back (decision 106).
    OnBackgrounded {
        if (running) {
            DevLog.log("practice", "backgrounded at ${positionMs.roundToInt()} ms, dropped")
            closeTelemetry(context, telemetry, session, attempt, positionMs, aborted = true)
            session.stop()
            running = false
            onExit()
        }
    }

    // The permission may have been granted in system settings while we were away.
    OnForegrounded {
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(level.id, rank, tempo) {
        DevLog.log(
            "practice",
            "open ${level.id} rank=${rank.name} bpm=$tempo notes=${notes.size} " +
                "mic=$micGranted",
        )
    }

    LaunchedEffect(running, attempt) {
        if (!running) return@LaunchedEffect
        var skewLogged = false
        // Output latency is what the timeline is corrected by; over Bluetooth it is
        // hundreds of milliseconds and it drifts, so a jump belongs in the log next
        // to the strokes it moved (decision 147).
        var loggedLatencyMs = -1f
        while (true) {
            val poll = session.poll()
            envelope = poll.envelope
            threshold = poll.threshold
            if (poll.anchored) {
                if (!skewLogged) {
                    skewLogged = true
                    DevLog.log(
                        "practice",
                        "anchored, input-output clock skew ${poll.clockSkewMs.roundToInt()} ms, " +
                            "latency slider ${latencyMs.roundToInt()} ms, " +
                            "output latency ${poll.outputLatencyMs.roundToInt()} ms",
                    )
                    loggedLatencyMs = poll.outputLatencyMs
                    telemetry.value?.latency(
                        atMs = poll.positionMs,
                        outputLatencyMs = poll.outputLatencyMs,
                        appliedMs = poll.appliedLatencyMs,
                    )
                }
                if (abs(poll.outputLatencyMs - loggedLatencyMs) > LATENCY_LOG_STEP_MS) {
                    loggedLatencyMs = poll.outputLatencyMs
                    DevLog.log(
                        "practice",
                        "output latency ${poll.outputLatencyMs.roundToInt()} ms " +
                            "at ${poll.positionMs.roundToInt()} ms",
                    )
                    // The same jump in the attempt log: without it a run that drifted late
                    // halfway through looks like the learner simply fell behind.
                    telemetry.value?.latency(
                        atMs = poll.positionMs,
                        outputLatencyMs = poll.outputLatencyMs,
                        appliedMs = poll.appliedLatencyMs,
                    )
                }
                val now = poll.positionMs
                positionMs = now
                val log = telemetry.value
                poll.hits.forEach { hit ->
                    if (hit.positionMs < firstJudgedMs) return@forEach
                    val outcome = attempt.registerHit(hit.positionMs)
                    log?.hit(
                        atMs = hit.positionMs,
                        outcome = outcome,
                        envelope = hit.envelope,
                        threshold = hit.threshold,
                        peak = poll.peak,
                        // An extra stroke carries no note of its own, so its distance to
                        // the nearest one is computed here (decision 154).
                        extraOffsetMs = if (outcome is HitOutcome.Extra) {
                            nearestNoteOffsetMs(notes, hit.positionMs)
                        } else {
                            Float.NaN
                        },
                    )
                }
                attempt.expireMissedNotes(now).forEach { index ->
                    log?.miss(atMs = now, noteIndex = index)
                }
                frame += 1
                if (now > endMs) {
                    val result = attempt.result()
                    closeTelemetry(
                        context = context,
                        telemetry = telemetry,
                        session = session,
                        attempt = attempt,
                        positionMs = now,
                        aborted = false,
                        result = result,
                    )
                    session.stop()
                    running = false
                    onFinished(result)
                    return@LaunchedEffect
                }
            } else {
                frame += 1
            }
            delay(PracticeSession.POLL_INTERVAL_MS)
        }
    }

    // Leaving mid-attempt drops it without a question (decision 106): the score is
    // only worth keeping once the attempt reaches the result screen.
    fun leave() {
        closeTelemetry(context, telemetry, session, attempt, positionMs, aborted = true)
        session.stop()
        running = false
        onExit()
    }

    BackHandler { leave() }

    Box(modifier = Modifier.fillMaxSize().background(RudiColors.Bg).stageSafePadding()) {
        if (!micGranted) {
            PermissionGate(
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onBack = onExit,
            )
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar first, progress under it: on the device the progress line ran
            // into the status bar when it sat on the very top edge (decision 101).
            PracticeHud(
                rubric = "${family.name} · ${level.displayNumber}",
                chips = listOf(
                    rank.name.uppercase(),
                    stringResource(R.string.practice_bpm, tempo),
                    stringResource(
                        R.string.practice_hits_per_beat,
                        practiceTarget(level, rank)?.hitsPerBeat ?: 1,
                    ),
                ),
                countdown = timedFloorMs?.let { floorMs ->
                    if (positionMs >= floorMs) {
                        stringResource(R.string.practice_finishing_cycle)
                    } else {
                        formatElapsed(ceil((floorMs - positionMs) / 1000f).toInt())
                    }
                },
                accuracy = attempt.liveAccuracy,
                misses = attempt.misses,
                extras = attempt.extras.size,
                finished = finishMs > 0f && positionMs >= finishMs,
                onBack = { leave() },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
            PracticeProgressLine(
                progress = if (finishMs <= 0f) 0f else positionMs / finishMs,
            )
            PracticeTrack(
                notes = notes,
                attempt = attempt,
                positionMs = positionMs,
                beatTimesMs = beatTimesMs,
                frame = frame,
                finishMs = finishMs,
                showOffsetMs = showOffsetMs,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            PracticeDeviationScale(
                offsets = attempt.offsets,
                modifier = Modifier.padding(horizontal = 18.dp),
                // The scale has to hold the offsets of every density of the attempt, so it
                // is drawn with the widest windows (decision 151).
                windows = windows.widest,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PracticeMicMeter(envelope = envelope, threshold = threshold)
                Spacer(modifier = Modifier.weight(1f))
                // Reserve the corner the floating transport button sits in.
                Spacer(modifier = Modifier.width(TRANSPORT_RESERVE))
            }
        }

        if (audioFailed) {
            Text(
                text = stringResource(R.string.practice_audio_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.WindowGood,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
            )
        }

        TransportButton(
            playing = running,
            size = TransportSize.Small,
            // Brick in both states: on the level map the call to action is red,
            // and a grey Play read as disabled on the device.
            accentIdle = true,
            onClick = {
                if (running) {
                    // Stop closes the attempt: the result screen is where the run is
                    // reviewed and the settings are tuned (decision 106).
                    val result = attempt.result()
                    closeTelemetry(
                        context = context,
                        telemetry = telemetry,
                        session = session,
                        attempt = attempt,
                        positionMs = positionMs,
                        aborted = false,
                        result = result,
                    )
                    session.stop()
                    running = false
                    onFinished(result)
                } else {
                    val started = session.start(
                        bpm = tempo,
                        clickAudible = clickAudible,
                        inputLatencyMs = latencyMs,
                        latencyCalibrated = latencyCalibrated,
                        tempoPlan = tempoPlan,
                    )
                    if (!started) DevLog.error("practice", "audio engine refused to start")
                    audioFailed = !started
                    running = started
                    if (started) {
                        telemetry.value = PracticeTelemetry(
                            header = TelemetryHeader(
                                startedAt = logStamp(),
                                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                                androidVersion = "${Build.VERSION.RELEASE} " +
                                    "(sdk ${Build.VERSION.SDK_INT})",
                                build = buildInfo.displayLabel,
                                levelId = level.id,
                                levelLabel = level.displayNumber,
                                family = family.name,
                                rank = rank.name,
                                bpm = tempo,
                                noteCount = notes.size,
                                minIntervalMs = minIntervalMs,
                                // The tightest windows of the attempt: they are the ones the
                                // shortest interval above produced (decision 151).
                                perfectMs = windows.tightest.perfectMs,
                                goodMs = windows.tightest.goodMs,
                                okMs = windows.tightest.okMs,
                                latencyMs = latencyMs,
                                latencyCalibrated = latencyCalibrated,
                                sensitivity = MicLab.DEFAULT_SENSITIVITY,
                                clickAudible = clickAudible,
                                headphones = headphonesConnected,
                                audio = session.streamInfo().toTelemetry(),
                            ),
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.practice_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = RudiColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RudiButton(
                text = stringResource(R.string.practice_permission_button),
                onClick = onRequest,
            )
            RudiButton(
                text = stringResource(R.string.practice_result_map),
                onClick = onBack,
                style = RudiButtonStyle.Secondary,
            )
        }
    }
}

/**
 * Closes the collector of the attempt and hands it to the store.
 *
 * Called from the finish, from Stop, from back and from backgrounding, and it has to be
 * safe in all four: the collector is cleared first, so a second call writes nothing.
 * The final stream read happens here, off the poll loop, and the file itself is written
 * on the store's own thread.
 */
private fun closeTelemetry(
    context: Context,
    telemetry: MutableState<PracticeTelemetry?>,
    session: PracticeSession,
    attempt: PracticeAttempt,
    positionMs: Float,
    aborted: Boolean,
    result: PracticeResult? = null,
) {
    val log = telemetry.value ?: return
    telemetry.value = null
    val audio = runCatching { session.streamInfo().toTelemetry() }.getOrNull()
    log.finish(
        atMs = positionMs,
        result = result ?: attempt.result(),
        debouncedTotal = attempt.debounced,
        audio = audio,
        aborted = aborted,
    )
    PracticeLogStore.save(context, log)
}

private fun NativeMicLab.StreamInfo.toTelemetry(): TelemetryAudio = TelemetryAudio(
    sampleRate = sampleRate,
    outputBurstFrames = outputBurstFrames,
    outputBufferFrames = outputBufferFrames,
    inputBurstFrames = inputBurstFrames,
    inputBufferFrames = inputBufferFrames,
    inputCapacityFrames = inputCapacityFrames,
    outputExclusive = outputExclusive,
    inputExclusive = inputExclusive,
    inputPreset = inputPresetName,
    outputXRuns = outputXRuns,
    inputXRuns = inputXRuns,
    errorCount = errorCount,
    lastErrorCode = lastErrorCode,
)

/** Wall-clock stamp of the attempt, the one line a human reads first. */
private fun logStamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

/** How far the measured output latency has to move before it is logged again. */
private const val LATENCY_LOG_STEP_MS = 15f

/** Extra time after the last note before the attempt closes itself. */
private const val TAIL_MS = 300f

/** Where the finish line sits behind the last note, and how long its glow is held. */
private const val FINISH_GAP_MIN_MS = 250f
private const val FINISH_GAP_MAX_MS = 900f
private const val FINISH_HOLD_MS = 450f

/** Corner kept free for the floating transport button: its size plus its margin. */
private val TRANSPORT_RESERVE = 82.dp

