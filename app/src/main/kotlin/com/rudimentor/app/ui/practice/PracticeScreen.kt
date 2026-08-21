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
import com.rudimentor.app.BuildInfo
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
import com.rudimentor.app.ui.component.TransportSize
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.ui.util.OnForegrounded
import com.rudimentor.app.util.DevLog
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The level attempt: a landscape track that scrolls the notes onto the hit line
 * while the microphone judges the strokes.
 *
 * The screen owns the attempt only. Progress is saved by the caller once the
 * result screen is done with it, so an attempt that is abandoned mid-way leaves no
 * trace (decision 88).
 *
 * Back, the settings drawer and leaving the app pause the attempt instead of
 * dropping it: the engine has no pause of its own, so the run is stopped and the
 * timeline is rewound to the start of the current bar, from where Play resumes
 * (decision 102).
 */
@Composable
fun PracticeScreen(
    level: Level,
    family: Family,
    rank: PracticeRank,
    bpm: Int,
    buildInfo: BuildInfo,
    clickAudible: Boolean,
    onClickAudible: (Boolean) -> Unit,
    latencyMs: Float,
    onLatencyMs: (Float) -> Unit,
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
    // Where the engine clock zero sits on the attempt timeline. Non-zero after a
    // pause: the engine always restarts from zero, so every polled position and hit
    // is shifted by this offset.
    var timeBaseMs by remember(attempt) { mutableFloatStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var audioFailed by remember { mutableStateOf(false) }

    // Rewinding to the start of the bar is safe: the attempt cursor only moves
    // forward, so notes already judged are never judged twice.
    fun pause() {
        if (!running) return
        val at = positionMs
        session.stop()
        running = false
        timeBaseMs = (floor(at / beatMs) * beatMs).coerceAtLeast(0f)
        positionMs = timeBaseMs
        DevLog.log(
            "practice",
            "paused at ${at.roundToInt()} ms, resume from ${timeBaseMs.roundToInt()} ms",
        )
    }

    DisposableEffect(session) {
        onDispose { session.stop() }
    }

    // Leaving the app does not dispose the screen, so the engine has to be stopped
    // by hand: otherwise the microphone keeps recording in the background and the
    // timeline keeps running, which scored silence as a wall of misses. The attempt
    // is kept and paused, so coming back resumes it (decision 102).
    OnBackgrounded {
        if (running) {
            DevLog.log("practice", "backgrounded at ${positionMs.roundToInt()} ms, paused")
            pause()
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
                            "latency slider ${latencyMs.roundToInt()} ms",
                    )
                }
                val now = timeBaseMs + poll.positionMs
                positionMs = now
                poll.hits.forEach { hitMs ->
                    val at = timeBaseMs + hitMs
                    if (at >= firstJudgedMs) attempt.registerHit(at)
                }
                attempt.expireMissedNotes(now)
                frame += 1
                if (now > lastNoteMs + PracticeScoring.OK_MS + TAIL_MS) {
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
            pause()
            confirmExit = true
        } else {
            onExit()
        }
    }

    SideSettingsDrawer(
        open = settingsOpen,
        onOpenChange = {
            if (it) pause()
            settingsOpen = it
        },
        panel = {
            PracticeSettingsPanel(
                clickAudible = clickAudible,
                onClickAudible = {
                    onClickAudible(it)
                    session.setClickAudible(it)
                },
                latencyMs = latencyMs,
                onLatencyMs = {
                    onLatencyMs(it)
                    session.setInputLatencyMs(it)
                },
                buildInfo = buildInfo,
                onDone = { settingsOpen = false },
            )
        },
    ) {
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
                    score = attempt.score,
                    combo = attempt.combo,
                    accuracy = attempt.liveAccuracy,
                    onBack = { if (running) confirmExit = true else onExit() },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
                PracticeProgressLine(
                    progress = if (lastNoteMs <= 0f) 0f else positionMs / lastNoteMs,
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
                    .padding(end = 18.dp, bottom = 14.dp),
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

/** Corner kept free for the floating transport button: its size plus its margin. */
private val TRANSPORT_RESERVE = 82.dp
