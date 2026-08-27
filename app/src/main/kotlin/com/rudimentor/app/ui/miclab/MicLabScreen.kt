package com.rudimentor.app.ui.miclab

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.audio.MicLab
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.ToolbarScreen
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.ui.util.OnForegrounded
import com.rudimentor.app.util.DevLog
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dev-only Mic Lab screen. Two stacked tracks share a horizontal timeline
 * anchored on the metronome tick line. The top track shows metronome ticks
 * as accent dots that fade over one beat; the bottom track shows detected
 * mic onsets as colored dots whose horizontal position encodes the signed
 * offset in milliseconds.
 *
 * The screen also owns runtime microphone permission handling. The mic lab
 * cannot run without RECORD_AUDIO; the UI shows a permission gate until it
 * is granted, then starts the engine.
 */
@Composable
fun MicLabScreen(
    buildInfo: BuildInfo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val micLab = remember { MicLab() }
    val status by micLab.status.collectAsState()

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }

    val hits = remember { mutableStateListOf<MicLab.MicLabEvent.Hit>() }
    val ticks = remember { mutableStateListOf<TimedTick>() }

    // Tracks whether the engine is open, so returning to the foreground cannot
    // start a second stream on top of the first one.
    var streaming by remember { mutableStateOf(false) }

    LaunchedEffect(micGranted) {
        if (micGranted && !streaming) {
            micLab.setBpm(MicLab.DEFAULT_BPM)
            micLab.setSensitivity(MicLab.DEFAULT_SENSITIVITY)
            micLab.setInputLatencyMs(MicLab.DEFAULT_LATENCY_MS)
            micLab.start(scope)
            streaming = true
        }
    }

    // The screen is not disposed when the app leaves the foreground, so the input
    // stream has to be closed by hand and reopened on the way back.
    OnBackgrounded {
        if (streaming) {
            DevLog.log("miclab", "backgrounded, input stream closed")
            micLab.stop()
            streaming = false
        }
    }
    OnForegrounded {
        if (micGranted && !streaming) {
            micLab.start(scope)
            streaming = true
        }
    }

    LaunchedEffect(micLab) {
        micLab.events.collect { event ->
            when (event) {
                is MicLab.MicLabEvent.Hit -> {
                    hits.add(event)
                    if (hits.size > MAX_HITS) hits.removeAt(0)
                }
                is MicLab.MicLabEvent.Tick -> {
                    ticks.add(TimedTick(event, System.currentTimeMillis()))
                    if (ticks.size > MAX_TICKS) ticks.removeAt(0)
                }
            }
        }
    }

    DisposableEffect(micLab) {
        onDispose { micLab.stop() }
    }

    BackHandler {
        micLab.stop()
        onBack()
    }

    ToolbarScreen(
        toolbarGap = 14,
        toolbar = {
            AppToolbar(
                title = "MIC LAB",
                onBack = {
                    micLab.stop()
                    onBack()
                },
                rightContent = {
                    Text(
                        text = "DEV",
                        style = RudiTextStyles.Rubric,
                        color = RudiColors.BrickLit,
                    )
                },
            )
        },
    ) {

        if (!micGranted) {
            PermissionGate(
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
            return@ToolbarScreen
        }

        TrackHeader(
            title = "Metronome",
            subtitle = "${status.bpm} BPM",
        )
        TickTrack(
            ticks = ticks,
            tickCount = status.tickCount,
            periodMs = 60_000f / status.bpm.toFloat().coerceAtLeast(1f),
        )

        Spacer(modifier = Modifier.height(10.dp))

        TrackHeader(
            title = "Your hits",
            subtitle = if (status.hitCount == 0) "waiting…"
            else "mean %+.1f ms  •  σ %.1f ms  •  n=%d".format(
                status.meanOffsetMs, status.stdDevMs, status.hitCount,
            ),
        )
        HitTrack(hits = hits)

        Spacer(modifier = Modifier.height(10.dp))
        OffsetLegend()

        Spacer(modifier = Modifier.height(18.dp))
        ControlsCard(
            status = status,
            onBpm = micLab::setBpm,
            onSensitivity = micLab::setSensitivity,
            onLatency = micLab::setInputLatencyMs,
            onClickAudible = micLab::setClickAudible,
            onReset = {
                hits.clear()
                ticks.clear()
                micLab.resetStats()
            },
        )

        Spacer(modifier = Modifier.height(14.dp))
        HudCard(status = status)

        Spacer(modifier = Modifier.height(18.dp))
        LastHitsList(hits = hits)

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = RudiColors.Line)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${buildInfo.displayLabel} · mic lab preview",
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
        )
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(RudiColors.SurfaceAlt, RoundedCornerShape(16.dp))
            .border(1.dp, RudiColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onRequest)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Microphone required",
                color = RudiColors.Text,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap to grant RECORD_AUDIO",
                color = RudiColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, color = RudiColors.Text, style = MaterialTheme.typography.titleSmall)
        Text(text = subtitle, color = RudiColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

private const val TRACK_WINDOW_MS = 800f

/** A metronome tick paired with the wall-clock time the UI received it. */
private data class TimedTick(val tick: MicLab.MicLabEvent.Tick, val arrivalMs: Long)

// Where the fixed hit-line sits, as a fraction of the track's travel
// distance from the left edge. The marker is timed so it crosses this
// line at the exact instant the next beat fires, giving the player a
// fixed visual target to anticipate instead of having to react to a
// sound that has already happened.
private const val HIT_LINE_FRACTION = 1f / 3f

// The marker is born at the right edge exactly when a tick fires and
// must reach HIT_LINE_FRACTION exactly one period later, i.e. at the
// *next* tick. Solving `fraction(periodMs) == HIT_LINE_FRACTION` for a
// marker that starts at fraction=1 and moves at constant speed gives a
// total travel time of periodMs / (1 - HIT_LINE_FRACTION):
//   fraction(t) = 1 - t / travelDurationMs
//   1 - HIT_LINE_FRACTION = periodMs / travelDurationMs
//   travelDurationMs = periodMs / (1 - HIT_LINE_FRACTION) = 1.5 * periodMs
private const val TRAVEL_DURATION_FACTOR = 1f / (1f - HIT_LINE_FRACTION)

@Composable
private fun TickTrack(
    ticks: List<TimedTick>,
    tickCount: Long,
    periodMs: Float,
) {
    // Anticipatory timeline: the marker is born at the right edge the
    // instant a tick fires and travels left at constant speed, crossing
    // the fixed hit-line exactly when the *next* tick is due. That lets
    // the player watch the marker approach the line and strike right as
    // it crosses, instead of reacting after the click sound has already
    // played. `nowMs` is refreshed on a timer so the marker actually
    // animates instead of jumping between fixed slots.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(16L)
        }
    }
    val safePeriodMs = if (periodMs > 0f) periodMs else 1000f
    val travelDurationMs = safePeriodMs * TRAVEL_DURATION_FACTOR
    // Render every tick still inside its own travel window, not just the
    // newest one. A tick reaches the hit-line exactly when the *next* tick
    // fires, so keying the marker off `ticks.lastOrNull()` alone made it
    // jump straight back to the right edge the instant it touched the
    // line -- it never visibly continued past it. Rendering all recent
    // ticks lets the old marker keep travelling left and fading out while
    // the new one is born on the right, so the crossing is visible.
    val activeTicks = ticks.filter { (nowMs - it.arrivalMs) < travelDurationMs }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RudiColors.SurfaceAlt)
            .border(1.dp, RudiColors.Line, RoundedCornerShape(14.dp)),
    ) {
        val dotWidth = 10.dp
        val travel = maxWidth - dotWidth

        // Fixed hit-line: the target the marker must cross exactly on the
        // beat.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = travel * HIT_LINE_FRACTION)
                .width(2.dp)
                .height(44.dp)
                .background(RudiColors.PadLetterAccent),
        )

        activeTicks.forEach { tick ->
            val elapsedMs = (nowMs - tick.arrivalMs).toFloat().coerceAtLeast(0f)
            val fraction = (1f - elapsedMs / travelDurationMs).coerceIn(0f, 1f)
            // Stay fully visible on the approach; fade only over the second
            // half of the journey, i.e. after crossing the hit-line, so the
            // marker visibly continues past it instead of disappearing right
            // at the line.
            val alpha = if (fraction >= HIT_LINE_FRACTION) {
                1f
            } else {
                (fraction / HIT_LINE_FRACTION).coerceIn(0f, 1f)
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = travel * fraction)
                    .width(dotWidth)
                    .height(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(RudiColors.BrickLit.copy(alpha = alpha)),
            )
        }
        Text(
            text = "#${tickCount}",
            color = RudiColors.Muted,
            style = RudiTextStyles.RowNumber,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

@Composable
private fun HitTrack(hits: List<MicLab.MicLabEvent.Hit>) {
    val recent = hits.takeLast(8)
    val newestOffset = recent.lastOrNull()?.offsetMs ?: 0f
    val animated by animateFloatAsState(
        targetValue = newestOffset.coerceIn(-TRACK_WINDOW_MS, TRACK_WINDOW_MS),
        label = "hitOffset",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RudiColors.SurfaceAlt)
            .border(1.dp, RudiColors.Line, RoundedCornerShape(14.dp)),
    ) {
        // Perfect-timing anchor.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RudiColors.Line)
                .align(Alignment.Center),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(48.dp)
                .background(RudiColors.PadLetterAccent),
        )

        // History dots along the track for context. Position is a fraction
        // of the track's ACTUAL width (BoxWithConstraints' maxWidth), not a
        // fixed dp constant — otherwise the dot can never travel past a
        // small fixed band near the left edge and can never reach the
        // center anchor line above, no matter how accurate the real timing
        // is.
        val fullWidthFraction = { ms: Float ->
            0.5f + (ms.coerceIn(-TRACK_WINDOW_MS, TRACK_WINDOW_MS) / (TRACK_WINDOW_MS * 2f))
        }
        val dotWidth = 10.dp
        val travel = maxWidth - dotWidth
        Box(modifier = Modifier.fillMaxSize()) {
            recent.forEachIndexed { index, hit ->
                val fraction = fullWidthFraction(hit.offsetMs)
                val alpha = (index + 1).toFloat() / recent.size
                val color = colorForOffset(hit.offsetMs).copy(alpha = alpha)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = travel * fraction)
                        .width(dotWidth)
                        .height(dotWidth)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        // Highlighted current dot: bigger and moved to the animated position.
        if (recent.isNotEmpty()) {
            val fraction = fullWidthFraction(animated)
            val color = colorForOffset(newestOffset)
            val bigDotWidth = 20.dp
            val bigTravel = maxWidth - bigDotWidth
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = bigTravel * fraction)
                    .width(bigDotWidth)
                    .height(bigDotWidth)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, RudiColors.Text, CircleShape),
            )
        }

        Text(
            text = if (recent.isEmpty()) "no hits yet" else "%+.1f ms".format(newestOffset),
            color = RudiColors.Text,
            style = RudiTextStyles.Timer.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

@Composable
private fun OffsetLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendChip("±25", COLOR_PERFECT)
        LegendChip("±60", COLOR_GOOD)
        LegendChip("±120", COLOR_LATE)
        LegendChip(">120", COLOR_OFF)
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${label} ms",
            color = RudiColors.Muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ControlsCard(
    status: MicLab.Status,
    onBpm: (Int) -> Unit,
    onSensitivity: (Float) -> Unit,
    onLatency: (Float) -> Unit,
    onClickAudible: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RudiColors.SurfaceAlt)
            .border(1.dp, RudiColors.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        SliderRow(
            label = "BPM",
            value = status.bpm.toFloat(),
            range = MicLab.MIN_BPM.toFloat()..MicLab.MAX_BPM.toFloat(),
            steps = (MicLab.MAX_BPM - MicLab.MIN_BPM) - 1,
            display = "${status.bpm}",
            onChange = { onBpm(it.roundToInt()) },
        )
        Spacer(Modifier.height(10.dp))
        SliderRow(
            label = "Sensitivity",
            value = status.sensitivity,
            range = 0f..1f,
            steps = 20,
            display = "%.2f".format(status.sensitivity),
            onChange = onSensitivity,
        )
        Spacer(Modifier.height(10.dp))
        SliderRow(
            label = "Latency offset",
            value = status.inputLatencyMs,
            range = -50f..250f,
            steps = 60,
            display = "%.0f ms".format(status.inputLatencyMs),
            onChange = onLatency,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Click audible", color = RudiColors.Text)
            Switch(
                checked = status.clickAudible,
                onCheckedChange = onClickAudible,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RudiColors.Text,
                    checkedTrackColor = RudiColors.Brick,
                    checkedBorderColor = RudiColors.BrickLit,
                    uncheckedThumbColor = RudiColors.Muted,
                    uncheckedTrackColor = RudiColors.Surface,
                    uncheckedBorderColor = RudiColors.Line,
                ),
            )
        }
        if (status.clickAudible) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Headphones recommended \u2014 without them the mic hears its own click and scores it as a hit.",
                color = RudiColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RudiColors.Surface)
                .border(1.dp, RudiColors.Line, RoundedCornerShape(10.dp))
                .clickable(onClick = onReset),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Reset stats",
                color = RudiColors.Text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = RudiColors.Text, style = MaterialTheme.typography.labelLarge)
            Text(
                text = display,
                color = RudiColors.Muted,
                style = RudiTextStyles.RowNumber.copy(fontFamily = FontFamily.Monospace),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = RudiColors.Text,
                activeTrackColor = RudiColors.Brick,
                inactiveTrackColor = RudiColors.Line,
            ),
        )
    }
}

@Composable
private fun HudCard(status: MicLab.Status) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RudiColors.SurfaceAlt)
            .border(1.dp, RudiColors.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HudCell(label = "Envelope", value = "%.3f".format(status.envelope))
            HudCell(label = "Threshold", value = "%.3f".format(status.threshold))
            HudCell(label = "Peak", value = "%.3f".format(status.peak))
        }
        Spacer(Modifier.height(10.dp))
        // Simple horizontal meter: envelope vs threshold, both scaled to peak.
        val scale = maxOf(status.peak, status.threshold, 0.02f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(RudiColors.Surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((status.envelope / scale).coerceIn(0f, 1f))
                    .height(14.dp)
                    .background(RudiColors.BrickLit),
            )
            // Threshold marker.
            val thresholdFraction = (status.threshold / scale).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(thresholdFraction)
                    .height(14.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(RudiColors.Text),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "sr ${status.sampleRate} Hz · input ${status.inputLatencyMs.toInt()} ms",
            color = RudiColors.Muted,
            style = RudiTextStyles.RowNumber,
        )
    }
}

@Composable
private fun HudCell(label: String, value: String) {
    Column {
        Text(text = label, color = RudiColors.Muted, style = MaterialTheme.typography.labelSmall)
        Text(
            text = value,
            color = RudiColors.Text,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun LastHitsList(hits: List<MicLab.MicLabEvent.Hit>) {
    val recent = hits.takeLast(8).asReversed()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RudiColors.SurfaceAlt)
            .border(1.dp, RudiColors.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(text = "LAST HITS", color = RudiColors.Muted, style = RudiTextStyles.Rubric)
        Spacer(Modifier.height(8.dp))
        if (recent.isEmpty()) {
            Text(
                text = "Hit the pad to see offsets.",
                color = RudiColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }
        recent.forEach { hit ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(colorForOffset(hit.offsetMs)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "%+7.1f ms".format(hit.offsetMs),
                    color = RudiColors.Text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    text = "env %.3f · thr %.3f".format(hit.envelope, hit.threshold),
                    color = RudiColors.Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

private val COLOR_PERFECT = Color(0xFF3DCF71)
private val COLOR_GOOD = Color(0xFFE0C948)
private val COLOR_LATE = Color(0xFFE38A2B)
private val COLOR_OFF = Color(0xFFE03131)

private fun colorForOffset(offsetMs: Float): Color = when (abs(offsetMs)) {
    in 0f..25f -> COLOR_PERFECT
    in 25f..60f -> COLOR_GOOD
    in 60f..120f -> COLOR_LATE
    else -> COLOR_OFF
}

private const val MAX_HITS = 128
private const val MAX_TICKS = 128
