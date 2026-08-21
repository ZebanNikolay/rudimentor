package com.rudimentor.app.ui.practice

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.ui.component.BackButton
import com.rudimentor.app.ui.component.RudiChip
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.roundToInt

/**
 * The top bar of the practice screen: back, what is being played, and the live
 * score. Progress is the 2 dp line above it -- there is no progress bar or beat
 * counter (decision 88).
 */
@Composable
fun PracticeHud(
    rubric: String,
    chips: List<String>,
    score: Int,
    combo: Int,
    accuracy: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackButton(onClick = onBack)
        Text(
            text = rubric,
            style = RudiTextStyles.Rubric,
            color = RudiColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        chips.forEach { chip -> RudiChip(text = chip) }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = score.toString(),
                style = RudiTextStyles.BpmValue.copy(fontSize = 26.sp, lineHeight = 28.sp),
                color = RudiColors.Text,
            )
            Text(
                text = stringResource(
                    R.string.practice_combo,
                    combo,
                    (accuracy * 100f).roundToInt(),
                ),
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
            )
        }
    }
}

/** The 2 dp progress line above the HUD. */
@Composable
fun PracticeProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        drawRect(color = RudiColors.Line, size = Size(size.width, size.height))
        drawRect(
            color = RudiColors.BrickLit,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
        )
    }
}

/**
 * The deviation scale: the last two dozen hits from -120 to +120 ms.
 *
 * Drawn as in the approved concept -- a 1 px rail, the timing windows as thin
 * rounded bars on it, and every hit as a short vertical tick. The first build used
 * a thick band with round dots; on a device it read as a progress bar.
 */
@Composable
fun PracticeDeviationScale(
    offsets: List<Float>,
    modifier: Modifier = Modifier,
) {
    val recent = if (offsets.size <= PracticeScoring.RECENT_OFFSETS) {
        offsets
    } else {
        offsets.subList(offsets.size - PracticeScoring.RECENT_OFFSETS, offsets.size)
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(SCALE_HEIGHT)) {
                val half = size.width / 2f
                val midY = size.height / 2f
                val pxPerMs = half / PracticeScoring.SCALE_MS
                val barHeight = SCALE_BAR.toPx()
                val radius = CornerRadius(barHeight / 2f)

                fun window(edgeMs: Float, color: Color) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(half - edgeMs * pxPerMs, midY - barHeight / 2f),
                        size = Size(edgeMs * 2f * pxPerMs, barHeight),
                        cornerRadius = radius,
                    )
                }

                // The rail first, then OK, GOOD and PERFECT on top of each other: the
                // windows read as one target getting tighter towards the centre.
                drawLine(
                    color = RudiColors.Line,
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 1f,
                )
                window(PracticeScoring.OK_MS, RudiColors.WindowOk.copy(alpha = 0.16f))
                window(PracticeScoring.GOOD_MS, RudiColors.WindowGood.copy(alpha = 0.18f))
                window(PracticeScoring.PERFECT_MS, RudiColors.Brick.copy(alpha = 0.34f))
                drawLine(
                    color = RudiColors.TrackHitLine,
                    start = Offset(half, midY - ZERO_HEIGHT.toPx() / 2f),
                    end = Offset(half, midY + ZERO_HEIGHT.toPx() / 2f),
                    strokeWidth = 1f,
                )

                val tickWidth = TICK_WIDTH.toPx()
                val tickHeight = TICK_HEIGHT.toPx()
                recent.forEachIndexed { index, offset ->
                    val fade = 0.35f + 0.65f * (index + 1f) / recent.size
                    val x = half + offset.coerceIn(
                        -PracticeScoring.SCALE_MS,
                        PracticeScoring.SCALE_MS,
                    ) * pxPerMs
                    drawRoundRect(
                        color = windowColor(PracticeScoring.window(offset)).copy(alpha = fade),
                        topLeft = Offset(x - tickWidth / 2f, midY - tickHeight / 2f),
                        size = Size(tickWidth, tickHeight),
                        cornerRadius = CornerRadius(tickWidth / 2f),
                    )
                }
                if (recent.isNotEmpty()) {
                    val mean = recent.average().toFloat()
                    val x = half + mean.coerceIn(
                        -PracticeScoring.SCALE_MS,
                        PracticeScoring.SCALE_MS,
                    ) * pxPerMs
                    drawLine(
                        color = RudiColors.Text,
                        start = Offset(x, midY - MEAN_HEIGHT.toPx() / 2f),
                        end = Offset(x, midY + MEAN_HEIGHT.toPx() / 2f),
                        strokeWidth = 1f,
                    )
                }
            }
        }
        // Room for the mean tag so it never sits under the floating transport button.
        Box(modifier = Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
            if (recent.isNotEmpty()) {
                Text(
                    text = PracticeScoring.verdictLabel(recent.average().toFloat()),
                    style = RudiTextStyles.Timer,
                    color = RudiColors.Muted,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private val SCALE_HEIGHT = 40.dp
private val SCALE_BAR = 8.dp
private val ZERO_HEIGHT = 20.dp
private val TICK_WIDTH = 3.dp
private val TICK_HEIGHT = 14.dp
private val MEAN_HEIGHT = 24.dp

/** The only thing on the bottom edge: the microphone indicator. */
@Composable
fun PracticeMicMeter(
    envelope: Float,
    threshold: Float,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.practice_mic_cd)
    Row(
        modifier = modifier.semantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 10.dp, height = 14.dp)) {
            // A capsule with a stand: the smallest readable microphone glyph.
            val capsuleWidth = size.width * 0.62f
            drawRoundRect(
                color = RudiColors.Muted,
                topLeft = Offset((size.width - capsuleWidth) / 2f, 0f),
                size = Size(capsuleWidth, size.height * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleWidth / 2f),
            )
            drawLine(
                color = RudiColors.Muted,
                start = Offset(size.width / 2f, size.height * 0.62f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
        Box(modifier = Modifier.size(width = 74.dp, height = 6.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = RudiColors.SurfaceAlt,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
                val level = envelope.coerceIn(0f, 1f)
                if (level > 0f) {
                    drawRoundRect(
                        color = if (envelope >= threshold) RudiColors.BrickLit else RudiColors.Line,
                        size = Size(size.width * level, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                    )
                }
                val thresholdX = size.width * threshold.coerceIn(0f, 1f)
                drawLine(
                    color = RudiColors.Muted,
                    start = Offset(thresholdX, 0f),
                    end = Offset(thresholdX, size.height),
                    strokeWidth = 1f,
                )
            }
        }
    }
}
