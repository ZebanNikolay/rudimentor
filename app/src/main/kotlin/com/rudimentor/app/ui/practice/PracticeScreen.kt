package com.rudimentor.app.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rudimentor.app.R
import com.rudimentor.app.audio.MicLab
import com.rudimentor.app.audio.PracticeSession
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SideSettingsDrawer
import com.rudimentor.app.ui.component.TransportButton
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.ui.util.OnForegrounded
import com.rudimentor.app.util.DevLog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The level attempt: a landscape track that scrolls the notes onto the hit line
 * while the microphone judges the strokes.
 *
 * The screen owns the attempt only. Progress is saved by the caller once the
 * result screen is done with it, so an attempt that is abandoned mid-way leaves no
 * trace (decision 88).
 */
@Composable
fun PracticeScreen(
    level: Level,
    family: Family,
    rank: PracticeRank,
    bpm: Int,
    startWithSettings: Boolean = false,
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
    val attempt = remember(notes) { PracticeAttempt(notes) }
    val beatMs = 60_000f / tempo
    val lastNoteMs = notes.lastOrNull()?.timeMs ?: 0f

    // Nothing before the first note counts: the count-in is played along with, not
    // judged (decision 87).
    val firstJudgedMs = (notes.firstOrNull()?.timeMs ?: 0f) - PracticeScoring.OK_MS

    var running by remember(attempt) { mutableStateOf(false) }
    var positionMs by remember(attempt) { mutableFloatStateOf(0f) }
    var frame by remember(attempt) { mutableIntStateOf(0) }
    var envelope by remember { mutableFloatStateOf(0f) }
    var threshold by remember { mutableFloatStateOf(0f) }
    var clickAudible by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableFloatStateOf(MicLab.DEFAULT_LATENCY_MS) }
    var settingsOpen by remember { mutableStateOf(startWithSettings) }
    var confirmExit by remember { mutableStateOf(false) }
    var audioFailed by remember { mutableStateOf(false) }

    DisposableEffect(session) {
        onDispose { session.stop() }
    }

    // Leaving the app does not dispose the screen, so the engine has to be stopped
    // by hand: otherwise the microphone keeps recording in the background and the
    // timeline keeps running, which scored silence as a wall of misses. An attempt
    // interrupted this way is abandoned, like any other exit mid-way (decision 88).
    OnBackgrounded {
        if (running) {
            val at = positionMs.roundToInt()
            DevLog.log("practice", "backgrounded at $at ms, attempt dropped")
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
        while (true) {
            val poll = session.poll()
            envelope = poll.envelope
            threshold = poll.threshold
            if (poll.anchored) {
                positionMs = poll.positionMs
                poll.hits.forEach { hitMs ->
                    if (hitMs >= firstJudgedMs) attempt.registerHit(hitMs)
                }
                attempt.expireMissedNotes(poll.positionMs)
                frame += 1
                if (poll.positionMs > lastNoteMs + PracticeScoring.OK_MS + TAIL_MS) {
                    session.stop()
                    running = false
                    onFinished(attempt.result())
                    return@LaunchedEffect
                }
            } else {
                frame += 1
            }
            delay(PracticeSession.POLL_INTERVAL_MS)
        }
    }

    BackHandler {
        if (settingsOpen) {
            settingsOpen = false
        } else if (running) {
            confirmExit = true
        } else {
            onExit()
        }
    }

    SideSettingsDrawer(
        modifier = Modifier.stageSafePadding(),
        open = settingsOpen,
        onOpenChange = { settingsOpen = it },
        panel = {
            PracticeSettingsPanel(
                clickAudible = clickAudible,
                onClickAudible = {
                    clickAudible = it
                    session.setClickAudible(it)
                },
                latencyMs = latencyMs,
                onLatencyMs = {
                    latencyMs = it
                    session.setInputLatencyMs(it)
                },
                onDone = { settingsOpen = false },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize().background(RudiColors.Bg)) {
            if (!micGranted) {
                PermissionGate(
                    onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onBack = onExit,
                )
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize()) {
                PracticeProgressLine(
                    progress = if (lastNoteMs <= 0f) 0f else positionMs / lastNoteMs,
                )
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
                    score = attempt.score,
                    combo = attempt.combo,
                    accuracy = attempt.liveAccuracy,
                    onBack = { if (running) confirmExit = true else onExit() },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
                PracticeTrack(
                    notes = notes,
                    attempt = attempt,
                    positionMs = positionMs,
                    beatMs = beatMs,
                    frame = frame,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                PracticeDeviationScale(
                    offsets = attempt.offsets,
                    modifier = Modifier.padding(horizontal = 18.dp),
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
                    Spacer(modifier = Modifier.width(TRANSPORT_SIZE))
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
                buttonSize = TRANSPORT_SIZE,
                // Brick in both states: on the level map the call to action is red,
                // and a grey Play read as disabled on the device.
                accentIdle = true,
                onClick = {
                    if (running) {
                        session.stop()
                        running = false
                        onFinished(attempt.result())
                    } else {
                        val started = session.start(
                            bpm = tempo,
                            clickAudible = clickAudible,
                            inputLatencyMs = latencyMs,
                        )
                        if (!started) DevLog.error("practice", "audio engine refused to start")
                        audioFailed = !started
                        running = started
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 2.dp),
            )

            if (confirmExit) {
                ExitOverlay(
                    onContinue = { confirmExit = false },
                    onLeave = {
                        session.stop()
                        running = false
                        confirmExit = false
                        onExit()
                    },
                )
            }
        }
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

/** Leaving mid-attempt is confirmed, because the score is lost (decision 88). */
@Composable
private fun ExitOverlay(onContinue: () -> Unit, onLeave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RudiColors.Scrim)
            // The scrim swallows taps: the transport button underneath must not fire.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(RudiDimens.SheetCorner))
                .background(RudiColors.Surface)
                .border(1.dp, RudiColors.Line, RoundedCornerShape(RudiDimens.SheetCorner))
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.practice_exit_title),
                style = MaterialTheme.typography.bodyLarge,
                color = RudiColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RudiButton(
                    text = stringResource(R.string.practice_exit_continue),
                    onClick = onContinue,
                )
                RudiButton(
                    text = stringResource(R.string.practice_exit_leave),
                    onClick = onLeave,
                    style = RudiButtonStyle.Secondary,
                )
            }
        }
    }
}

/** Extra time after the last note before the attempt closes itself. */
private const val TAIL_MS = 300f

private val TRANSPORT_SIZE = 72.dp
