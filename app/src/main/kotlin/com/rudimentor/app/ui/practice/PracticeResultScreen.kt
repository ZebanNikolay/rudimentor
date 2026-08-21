package com.rudimentor.app.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.RudiChip
import com.rudimentor.app.ui.component.SideSettingsDrawer
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.roundToInt

/**
 * The screen after an attempt: verdict, stars, the numbers, and where to go next.
 *
 * The pass bar is accuracy only for now (decision 89) -- the level tree does not
 * gate on score yet, so a clean run at a slow tempo still opens the next node.
 *
 * Settings are reachable here through the same drawer as during an attempt, so the
 * latency can be trimmed right after seeing the timing spread that motivated it
 * (decision 102).
 */
@Composable
fun PracticeResultScreen(
    level: Level,
    family: Family,
    rank: PracticeRank,
    bpm: Int,
    result: PracticeResult,
    buildInfo: BuildInfo,
    clickAudible: Boolean,
    onClickAudible: (Boolean) -> Unit,
    latencyMs: Float,
    onLatencyMs: (Float) -> Unit,
    onRetry: () -> Unit,
    onNextLevel: (() -> Unit)?,
    onToMap: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }

    // Without this the system back gesture closed the app from the result screen
    // instead of leaving the practice flow.
    BackHandler { if (settingsOpen) settingsOpen = false else onToMap() }

    SideSettingsDrawer(
        open = settingsOpen,
        onOpenChange = { settingsOpen = it },
        panel = {
            PracticeSettingsPanel(
                clickAudible = clickAudible,
                onClickAudible = onClickAudible,
                latencyMs = latencyMs,
                onLatencyMs = onLatencyMs,
                buildInfo = buildInfo,
                onDone = { settingsOpen = false },
            )
        },
    ) {
        ResultBody(
            level = level,
            family = family,
            rank = rank,
            bpm = bpm,
            result = result,
            onRetry = onRetry,
            onNextLevel = onNextLevel,
            onToMap = onToMap,
        )
    }
}

@Composable
private fun ResultBody(
    level: Level,
    family: Family,
    rank: PracticeRank,
    bpm: Int,
    result: PracticeResult,
    onRetry: () -> Unit,
    onNextLevel: (() -> Unit)?,
    onToMap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RudiColors.Bg)
            .stageSafePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(
                if (result.passed) {
                    R.string.practice_result_passed
                } else {
                    R.string.practice_result_failed
                }
            ),
            style = RudiTextStyles.Rubric,
            color = if (result.passed) RudiColors.BrickLit else RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${family.name} · ${level.displayNumber}",
                style = MaterialTheme.typography.titleLarge,
                color = RudiColors.Text,
            )
            Spacer(modifier = Modifier.width(10.dp))
            RudiChip(text = rank.name.uppercase(), accent = true)
            Spacer(modifier = Modifier.width(6.dp))
            RudiChip(text = stringResource(R.string.practice_bpm, bpm))
        }

        Spacer(modifier = Modifier.height(14.dp))
        StarRow(stars = result.stars)

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric(
                label = stringResource(R.string.practice_result_score),
                value = result.score.toString(),
                strong = true,
            )
            Metric(
                label = stringResource(R.string.practice_result_accuracy),
                value = stringResource(
                    R.string.practice_result_accuracy_value,
                    (result.accuracy * 100f).roundToInt(),
                ),
            )
            Metric(
                label = stringResource(R.string.practice_result_max_combo),
                value = "×${result.maxCombo}",
            )
            Metric(
                label = stringResource(R.string.practice_result_misses),
                value = result.misses.toString(),
            )
            Metric(
                label = stringResource(R.string.practice_result_extras),
                value = result.extras.toString(),
            )
            Metric(
                label = stringResource(R.string.practice_result_mean),
                value = PracticeScoring.verdictLabel(result.meanOffsetMs),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        // The histogram takes whatever is left between the numbers and the actions:
        // a fixed height left it as a strip in the middle of an empty screen.
        OffsetHistogram(
            offsets = result.offsets,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Another attempt is always one tap away, whether the level was passed
            // or not; only the primary action changes.
            if (result.passed && onNextLevel != null) {
                RudiButton(
                    text = stringResource(R.string.practice_result_next),
                    onClick = onNextLevel,
                )
                RudiButton(
                    text = stringResource(R.string.practice_result_retry),
                    onClick = onRetry,
                    style = RudiButtonStyle.Secondary,
                )
            } else {
                RudiButton(
                    text = stringResource(R.string.practice_result_retry),
                    onClick = onRetry,
                )
            }
            RudiButton(
                text = stringResource(R.string.practice_result_map),
                onClick = onToMap,
                style = RudiButtonStyle.Secondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            // The drawer handle lives on the trailing edge: keep the row clear of it.
            Spacer(modifier = Modifier.width(28.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, strong: Boolean = false) {
    Column {
        Text(
            text = label,
            style = RudiTextStyles.Rubric,
            color = RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = if (strong) {
                RudiTextStyles.BpmValue.copy(fontSize = 30.sp, lineHeight = 32.sp)
            } else {
                RudiTextStyles.BpmValue.copy(fontSize = 20.sp, lineHeight = 22.sp)
            },
            color = RudiColors.Text,
        )
    }
}

@Composable
private fun StarRow(stars: Int) {
    val cd = stringResource(R.string.practice_result_stars_cd, stars)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { contentDescription = cd },
    ) {
        repeat(3) { index ->
            val filled = index < stars
            Canvas(modifier = Modifier.size(24.dp)) {
                val path = starPath(size.minDimension)
                if (filled) {
                    drawPath(path = path, color = RudiColors.BrickLit)
                } else {
                    drawPath(
                        path = path,
                        color = RudiColors.Line,
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                }
            }
        }
    }
}

/** Five-pointed star inscribed in a square of [side]. */
private fun starPath(side: Float): Path {
    val path = Path()
    val center = side / 2f
    val outer = side / 2f
    val inner = outer * 0.42f
    for (point in 0 until 10) {
        val radius = if (point % 2 == 0) outer else inner
        val angle = (-90f + point * 36f) * (Math.PI / 180f).toFloat()
        val x = center + radius * kotlin.math.cos(angle)
        val y = center + radius * kotlin.math.sin(angle)
        if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Timing spread over the whole attempt: thirty bins from -120 to +120 ms, coloured
 * by the window each bin falls into.
 */
@Composable
private fun OffsetHistogram(offsets: List<Float>, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.practice_result_histogram_cd)
    val bins = IntArray(PracticeScoring.HISTOGRAM_BINS)
    offsets.forEach { offset ->
        PracticeScoring.histogramBin(offset)?.let { bin -> bins[bin] += 1 }
    }
    val peak = bins.maxOrNull() ?: 0
    Box(modifier = modifier.semantics { contentDescription = cd }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Hairlines top and bottom instead of a card: the concept frames the plot
            // with two rules and lets the bars stand on the background.
            drawLine(
                color = HistogramRule,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1f,
            )
            drawLine(
                color = HistogramRule,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f,
            )
            val centreX = size.width / 2f
            drawLine(
                color = HistogramCentre,
                start = Offset(centreX, 0f),
                end = Offset(centreX, size.height),
                strokeWidth = 1f,
            )
            if (peak == 0) return@Canvas
            val gap = 1f
            val binWidth = (size.width - gap * (bins.size - 1)) / bins.size
            val msPerBin = 2f * PracticeScoring.SCALE_MS / bins.size
            val ceiling = size.height * 0.92f
            bins.forEachIndexed { index, count ->
                if (count == 0) return@forEachIndexed
                val centreMs = -PracticeScoring.SCALE_MS + msPerBin * (index + 0.5f)
                val height = ceiling * (count.toFloat() / peak)
                drawRect(
                    color = windowColor(PracticeScoring.window(centreMs)).copy(alpha = 0.8f),
                    topLeft = Offset(index * (binWidth + gap), size.height - height),
                    size = Size(binWidth, height),
                )
            }
        }
    }
}

private val HistogramRule = Color(0xFF202020)
private val HistogramCentre = Color(0xFF3A3A3A)
