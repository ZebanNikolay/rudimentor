package com.rudimentor.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rudimentor.app.R
import com.rudimentor.app.audio.LatencyCalibration
import com.rudimentor.app.audio.MicLab
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.SettingsGap
import com.rudimentor.app.ui.component.SettingsNote
import com.rudimentor.app.ui.component.SettingsPanel
import com.rudimentor.app.ui.component.SettingsValueRow
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.util.OnBackgrounded
import com.rudimentor.app.util.DevLog
import kotlin.math.roundToInt

/**
 * Measures the latency of this device and this pair of headphones.
 *
 * A slider was the whole latency story until dev.36, and the field log of that build
 * showed why that cannot work: the learner played dead even, and every stroke still
 * landed 190-230 ms late, because the round trip of a Bluetooth headset is nothing the
 * app can guess and `calculateLatencyMillis()` does not report over A2DP. So the number
 * is measured instead: a slow click, one stroke per beat, and the median of what the
 * microphone hears goes into the setting (decision 154).
 *
 * The screen never writes a setting itself. Apply hands the median back to the settings
 * screen, which holds it in its draft until the learner says Save.
 */
@Composable
fun CalibrationScreen(
    onApply: (Float) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val micLab = remember { MicLab() }
    val calibration = remember { LatencyCalibration() }
    var reading by remember { mutableStateOf(calibration.reading()) }
    var running by remember { mutableStateOf(false) }
    var audioFailed by remember { mutableStateOf(false) }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }

    // Every stroke the engine reports is a whole round trip -- click written, heard,
    // struck, detected -- because the engine compensates nothing here.
    LaunchedEffect(micLab) {
        micLab.events.collect { event ->
            if (event is MicLab.MicLabEvent.Hit) {
                calibration.add(event.offsetMs)
                reading = calibration.reading()
            }
        }
    }

    fun startRound() {
        micLab.setBpm(LatencyCalibration.CLICK_BPM)
        micLab.setClickAudible(true)
        micLab.setInputLatencyMs(0f)
        micLab.setSensitivity(MicLab.DEFAULT_SENSITIVITY)
        val started = micLab.start(scope)
        if (!started) DevLog.error("calibration", "audio engine refused to start")
        audioFailed = !started
        running = started
    }

    fun stopRound() {
        if (!running) return
        micLab.stop()
        running = false
    }

    // The screen survives the app going to the background, so the click and the
    // microphone have to be closed by hand. The samples stay: the learner comes back
    // to the round they were in the middle of.
    OnBackgrounded {
        if (running) {
            DevLog.log("calibration", "backgrounded, round stopped")
            stopRound()
        }
    }

    DisposableEffect(micLab) {
        onDispose { micLab.stop() }
    }

    fun leave() {
        stopRound()
        onBack()
    }

    BackHandler { leave() }

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(
                title = stringResource(R.string.calibration_title),
                onBack = { leave() },
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!micGranted) {
                PermissionGate(
                    onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
                return@Column
            }

            Text(
                text = stringResource(R.string.calibration_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.Muted,
            )
            Spacer(modifier = Modifier.height(16.dp))

            SettingsPanel(title = stringResource(R.string.settings_latency_label)) {
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
                CalibrationHint(reading = reading)
            }

            Spacer(modifier = Modifier.height(18.dp))
            SettingsNote(text = stringResource(R.string.calibration_click_note))
            if (audioFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNote(text = stringResource(R.string.calibration_audio_failed))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RudiButton(
                    text = stringResource(
                        if (running) R.string.calibration_stop else R.string.calibration_start,
                    ),
                    onClick = { if (running) stopRound() else startRound() },
                    modifier = Modifier.weight(1f),
                )
                RudiButton(
                    text = stringResource(R.string.calibration_reset),
                    onClick = {
                        calibration.reset()
                        micLab.resetStats()
                        reading = calibration.reading()
                    },
                    style = RudiButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            RudiButton(
                text = stringResource(R.string.calibration_apply),
                onClick = {
                    val median = reading.medianMs ?: return@RudiButton
                    DevLog.log(
                        "calibration",
                        "applied ${median.roundToInt()} ms from ${reading.samples.size} strokes " +
                            "spread ±${reading.spreadMs.roundToInt()} ms",
                    )
                    stopRound()
                    onApply(median)
                },
                enabled = reading.ready,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** What the learner should do next: play more, play evener, or take the number. */
@Composable
private fun CalibrationHint(reading: LatencyCalibration.Reading) {
    val text = when {
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

/** Above this scatter the median is not worth trusting, and the screen says so. */
private const val WIDE_SPREAD_MS = 40f
