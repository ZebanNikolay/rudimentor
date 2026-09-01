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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.RudiChip
import com.rudimentor.app.ui.component.padStarPath
import com.rudimentor.app.ui.levels.title
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The screen after an attempt: verdict, stars, the numbers, and where to go next.
 *
 * The pass bar is accuracy only for now (decision 89) -- the level tree does not
 * gate on score yet, so a clean run at a slow tempo still opens the next node.
 *
 * No settings drawer here (decision 185): a run just ended, and a screen that both judges
 * the run and lets its terms be changed made every reading arguable. The levels screen owns
 * settings now; the only way out of this screen into a setting is the advice card offering
 * the sound check when the numbers say the audio path is at fault.
 *
 * The plot takes a third of the width, not all of it: the screen is landscape, so full width
 * was length the distribution never needed. The rest of the row carries the advice.
 */
@Composable
fun PracticeResultScreen(
    level: Level,
    family: Family,
    rank: PracticeRank,
    bpm: Int,
    result: PracticeResult,
    onRetry: () -> Unit,
    onNextLevel: (() -> Unit)?,
    onToMap: () -> Unit,
    /** Opens the sound check, offered when the run says the audio path is the problem. */
    onSoundCheck: () -> Unit,
) {
    // Without this the system back gesture closed the app from the result screen
    // instead of leaving the practice flow.
    BackHandler { onToMap() }

    ResultBody(
        level = level,
        family = family,
        rank = rank,
        bpm = bpm,
        result = result,
        onRetry = onRetry,
        onNextLevel = onNextLevel,
        onToMap = onToMap,
        onSoundCheck = onSoundCheck,
    )
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
    onSoundCheck: () -> Unit,
) {
    val metrics = remember(result) { PracticeMetrics.of(result) }
    val advice = remember(result) { PracticeAdvice.of(result, metrics) }
    // Collapsed by default: the one line of advice is what the screen is for, and the three
    // numbers behind it are for the run where that line is not believed (decision 168).
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RudiColors.Bg)
            .stageSafePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // The row of actions is laid out first and at its full height, and everything the
        // screen reports scrolls in what is left (decision 199). Before this the whole
        // screen was one rigid column: on a short landscape window -- and always with the
        // advice opened -- the readings ate the height and the buttons at the bottom were
        // squeezed to a sliver. Nothing here is allowed to squeeze the way out of the run.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
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
                    // The exercise the attempt was, named the way the map and the level
                    // screen name it, plus its code. The family name with a bare number
                    // read `Paradiddles · 1` here while the level itself was called
                    // `Sticking transition · TR-1` two screens earlier (decision 201).
                    text = "${level.title(family)} · ${level.displayCode}",
                    style = MaterialTheme.typography.titleLarge,
                    color = RudiColors.Text,
                )
                Spacer(modifier = Modifier.width(10.dp))
                RudiChip(text = rank.name.uppercase(), accent = true)
                Spacer(modifier = Modifier.width(6.dp))
                RudiChip(text = stringResource(R.string.practice_bpm, bpm))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarRow(stars = result.stars)
                // The badges name the two top states the stars already encode, so the screen
                // says out loud what the node will carry (decision 126).
                if (result.fullCombo) {
                    Spacer(modifier = Modifier.width(12.dp))
                    RudiChip(text = stringResource(R.string.practice_result_full_combo), accent = true)
                }
                if (result.crown) {
                    Spacer(modifier = Modifier.width(6.dp))
                    RudiChip(text = stringResource(R.string.practice_result_crown), accent = true)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Accuracy is the result now: one number, and under it what it was spent on
                // (decision 125). Score and max combo are gone.
                Metric(
                    label = stringResource(R.string.practice_result_accuracy),
                    value = stringResource(
                        R.string.practice_result_accuracy_value,
                        (result.accuracy * 100f).roundToInt(),
                    ),
                    strong = true,
                )
                Metric(
                    label = stringResource(R.string.practice_result_perfect),
                    value = stringResource(
                        R.string.practice_result_count_value,
                        result.perfect,
                        result.noteCount,
                    ),
                )
                Metric(
                    label = stringResource(R.string.practice_result_good),
                    value = result.good.toString(),
                )
                Metric(
                    label = stringResource(R.string.practice_result_ok),
                    value = result.ok.toString(),
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
            Row(modifier = Modifier.fillMaxWidth()) {
                // A third of the width is enough for the shape of the run: the bars come out
                // three times thinner and read the same, and the length they gave up is where
                // the advice lives (decision 185). The plot has a height of its own now that
                // the block scrolls -- there is no leftover height to fill.
                Column(modifier = Modifier.fillMaxWidth(HISTOGRAM_WIDTH_FRACTION)) {
                    OffsetHistogram(
                        offsets = result.offsets,
                        windows = result.windows,
                        modifier = Modifier.fillMaxWidth().height(HISTOGRAM_HEIGHT),
                    )
                    HistogramScale()
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (expanded) {
                        // Opened on purpose: the numbers behind the advice, for the run where
                        // the one line is not believed.
                        AdviceDetails(
                            metrics = metrics,
                            advice = advice,
                            onCollapse = { expanded = false },
                        )
                    } else {
                        AdviceCard(
                            advice = advice,
                            metrics = metrics,
                            onExpand = { expanded = true },
                            onSoundCheck = onSoundCheck,
                        )
                    }
                }
            }
        }

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
                val path = padStarPath(size.minDimension)
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

/**
 * Timing spread over the whole attempt: thirty bins from -120 to +120 ms, coloured
 * by the window each bin falls into.
 */
@Composable
private fun OffsetHistogram(
    offsets: List<Float>,
    windows: HitWindows,
    modifier: Modifier = Modifier,
) {
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
                    color = windowColor(windows.window(centreMs)).copy(alpha = 0.8f),
                    topLeft = Offset(index * (binWidth + gap), size.height - height),
                    size = Size(binWidth, height),
                )
            }
        }
    }
}

/** Ends of the plotted range, so the strip is readable without a grid. */
@Composable
private fun HistogramScale() {
    val scale = PracticeScoring.SCALE_MS.roundToInt()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.practice_result_scale_early, scale),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
        Text(
            text = stringResource(R.string.practice_result_scale_late, scale),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
    }
}

/**
 * The one thing the screen says about the run, or nothing at all.
 *
 * Nothing at all is a normal outcome: a run whose numbers are all inside their own error
 * gets an empty space here rather than a sentence invented to fill it (decision 168).
 */
@Composable
private fun AdviceCard(
    advice: PracticeAdvice?,
    metrics: PracticeMetrics,
    onExpand: () -> Unit,
    onSoundCheck: () -> Unit,
) {
    if (advice == null) return
    val text = adviceText(advice)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = RudiColors.Text,
        )
        // The offset hint carries the honest half with it: what the same strokes are worth
        // once the constant lateness is removed. It is a smaller number than the player
        // hopes for, which is exactly why it is shown.
        if (advice.kind == AdviceKind.Offset) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.advice_offset_gain,
                    (metrics.accuracyWithoutOffset * 100f).roundToInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.Muted,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (advice.kind == AdviceKind.SoundCheck) {
                RudiButton(
                    text = stringResource(R.string.advice_sound_check_action),
                    onClick = onSoundCheck,
                )
            }
            RudiButton(
                text = stringResource(R.string.advice_more),
                onClick = onExpand,
                style = RudiButtonStyle.Secondary,
            )
        }
    }
}

/** The sentence for one piece of advice, with its own number in it. */
@Composable
private fun adviceText(advice: PracticeAdvice): String = when (advice.kind) {
    AdviceKind.SoundCheck -> stringResource(R.string.advice_sound_check)
    AdviceKind.Detector -> stringResource(
        R.string.advice_detector,
        (advice.share * 100f).roundToInt(),
    )

    AdviceKind.ExtraHits -> stringResource(
        R.string.advice_extras,
        (advice.share * 100f).roundToInt(),
    )

    AdviceKind.Offset -> stringResource(
        if (advice.valueMs >= 0f) R.string.advice_offset_late else R.string.advice_offset_early,
        abs(advice.valueMs).roundToInt(),
    )

    AdviceKind.Drift -> stringResource(
        if (advice.valueMs < 0f) R.string.advice_drift_faster else R.string.advice_drift_slower,
        abs(advice.valueMs).roundToInt(),
    )

    AdviceKind.Spread -> stringResource(R.string.advice_spread, advice.valueMs.roundToInt())
    AdviceKind.AllGood -> stringResource(R.string.advice_all_good)
}

/**
 * The three numbers the advice was chosen from, each next to the error of its own
 * measurement, and one line saying why the winner was believed and the others were not.
 */
@Composable
private fun AdviceDetails(
    metrics: PracticeMetrics,
    advice: PracticeAdvice?,
    onCollapse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (metrics.judged < PracticeMetrics.MIN_JUDGED) {
            Text(
                text = stringResource(
                    R.string.advice_detail_short,
                    PracticeMetrics.MIN_JUDGED,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.Muted,
            )
        } else {
            DetailRow(
                label = stringResource(R.string.advice_detail_offset),
                value = metrics.offset,
                proven = metrics.offsetSignificant,
            )
            DetailRow(
                label = stringResource(R.string.advice_detail_spread),
                value = Measured(metrics.spreadMs, Float.NaN),
                proven = metrics.spreadSignificant,
            )
            DetailRow(
                label = stringResource(R.string.advice_detail_drift),
                value = metrics.drift,
                proven = metrics.driftSignificant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.advice_detail_window,
                        metrics.perfectMs.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.Muted,
                )
                Text(
                    text = stringResource(R.string.advice_detail_strokes, metrics.judged),
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.Muted,
                )
            }
            val reason = when (advice?.kind) {
                AdviceKind.Offset -> metrics.offset.sigmas
                AdviceKind.Drift -> metrics.drift.sigmas
                else -> null
            }
            if (reason != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.advice_detail_proven, reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.Muted,
                )
            } else if (metrics.drift.sigmas > 0f && !metrics.driftSignificant) {
                // The number the eye wants to believe most is the drift, so when it did not
                // clear its own bar the screen says so out loud instead of hiding it.
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.advice_detail_unproven, metrics.drift.sigmas),
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.Muted,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        RudiButton(
            text = stringResource(R.string.advice_less),
            onClick = onCollapse,
            style = RudiButtonStyle.Secondary,
        )
    }
}

/** One measured quantity: name, value, its error, and whether it was believed. */
@Composable
private fun DetailRow(label: String, value: Measured, proven: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = RudiTextStyles.Rubric,
            color = RudiColors.Muted,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = stringResource(R.string.advice_detail_value, value.valueMs.roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = if (proven) RudiColors.Text else RudiColors.Muted,
            modifier = Modifier.width(72.dp),
        )
        if (!value.errorMs.isNaN()) {
            Text(
                text = stringResource(
                    R.string.advice_detail_error,
                    value.errorMs.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
        }
    }
}

/** Height of the plot: enough to read its shape, small enough to leave room. */
private val HISTOGRAM_HEIGHT = 116.dp

/** Share of the width the plot takes, the rest going to the advice (decision 185). */
private const val HISTOGRAM_WIDTH_FRACTION = 0.34f

private val HistogramRule = Color(0xFF202020)
private val HistogramCentre = Color(0xFF3A3A3A)
