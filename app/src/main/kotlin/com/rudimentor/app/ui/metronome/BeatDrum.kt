package com.rudimentor.app.ui.metronome

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Hand
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiMotion
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The looping drum of beat rows. One row is shown alone; with two or more rows the
 * neighbours peek in above and below, and the whole barrel rotates on every row change.
 */
@Composable
fun BeatDrum(
    grid: BeatGrid,
    activeRow: Int,
    bpm: Int,
    showLetters: Boolean,
    playingRow: Int?,
    playingBeat: Int?,
    editable: Boolean,
    onSelectRow: (Int) -> Unit,
    onCycleBeat: (Int) -> Unit,
    onToggleHand: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (grid.rowCount == 1) {
        // A single row is not a drum: no track, no guides, no row number.
        BeatRowPads(
            row = grid.rows[0],
            isActive = true,
            showLetters = showLetters,
            litBeat = if (playingRow == 0) playingBeat else null,
            editable = editable,
            onCycleBeat = onCycleBeat,
            onToggleHand = onToggleHand,
            modifier = modifier
                .fillMaxWidth()
                .height(RudiDimens.DrumSlotHeight),
        )
        return
    }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }
    val slotHeightPx = with(density) { RudiDimens.DrumSlotHeight.toPx() }

    // A virtual index keeps the rotation going in the swipe direction across the loop seam.
    var virtualRow by remember { mutableFloatStateOf(activeRow.toFloat()) }
    LaunchedEffect(activeRow, grid.rowCount) {
        val current = virtualRow.roundToInt()
        val forward = (activeRow - current).mod(grid.rowCount)
        val delta = if (forward * 2 > grid.rowCount) forward - grid.rowCount else forward
        virtualRow = (current + delta).toFloat()
    }
    val animatedRow by animateFloatAsState(
        targetValue = virtualRow,
        animationSpec = tween(
            durationMillis = RudiMotion.spinMillis(bpm),
            easing = DrumEasing,
        ),
        label = "drum",
    )

    var dragged by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(RudiDimens.DrumSlotHeight * VISIBLE_SLOTS)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        FADE_STOP to Color.Black,
                        1f - FADE_STOP to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .pointerInput(grid.rowCount) {
                detectVerticalDragGestures(
                    onDragEnd = { dragged = 0f },
                    onDragCancel = { dragged = 0f },
                ) { _, dragAmount ->
                    dragged += dragAmount
                    if (abs(dragged) >= swipeThresholdPx) {
                        val step = if (dragged < 0) 1 else -1
                        onSelectRow((virtualRow.roundToInt() + step).mod(grid.rowCount))
                        dragged = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Guide lines mark the editable slot in the middle of the barrel.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RudiDimens.DrumSlotHeight)
                .drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    drawRect(
                        color = RudiColors.Guide,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, stroke),
                    )
                    drawRect(
                        color = RudiColors.Guide,
                        topLeft = Offset(0f, size.height - stroke),
                        size = Size(size.width, stroke),
                    )
                },
        )

        val centre = animatedRow.roundToInt()
        for (offset in -1..1) {
            val virtualIndex = centre + offset
            val rowIndex = virtualIndex.mod(grid.rowCount)
            val distance = virtualIndex - animatedRow
            val magnitude = abs(distance).coerceAtMost(2f)
            val alpha = when {
                magnitude <= 1f -> 1f - magnitude * (1f - RudiMotion.NEIGHBOUR_ALPHA)
                else -> RudiMotion.NEIGHBOUR_ALPHA * (2f - magnitude)
            }
            if (alpha <= 0.01f) continue
            val scale = 1f - (1f - RudiMotion.NEIGHBOUR_SCALE) * magnitude.coerceAtMost(1f)
            val isCentre = offset == 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RudiDimens.DrumSlotHeight)
                    .offset { IntOffset(0, (distance * slotHeightPx).roundToInt()) }
                    .alpha(alpha)
                    .scale(scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (rowIndex + 1).toString(),
                    style = if (isCentre) RudiTextStyles.RowNumberActive else RudiTextStyles.RowNumber,
                    color = if (isCentre) RudiColors.BrickLit else RudiColors.RowNumber,
                    modifier = Modifier.width(RudiDimens.RowNumberWidth),
                )
                BeatRowPads(
                    row = grid.rows[rowIndex],
                    isActive = isCentre,
                    showLetters = showLetters,
                    litBeat = if (playingRow == rowIndex) playingBeat else null,
                    editable = editable && isCentre,
                    onCycleBeat = onCycleBeat,
                    onToggleHand = onToggleHand,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(RudiDimens.RowNumberWidth))
            }
        }
    }
}

/** One row of pads, sized to fill the available width with drum-machine grouping. */
@Composable
private fun BeatRowPads(
    row: BeatRow,
    isActive: Boolean,
    showLetters: Boolean,
    litBeat: Int?,
    editable: Boolean,
    onCycleBeat: (Int) -> Unit,
    onToggleHand: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val count = row.size
        val gap: Dp = when {
            count > 8 -> 5.dp
            count > 4 -> 8.dp
            else -> 12.dp
        }
        val divider: Dp = if (count > 4) 14.dp else 0.dp
        val groups = if (count > 4) (count - 1) / 4 else 0
        val maxSize = if (isActive) RudiDimens.PadActiveMaxSize else RudiDimens.PadNeighbourMaxSize
        val available = maxWidth - gap * (count - 1) - divider * groups
        val size = maxOf(RudiDimens.PadMinSize, minOf(available / count, maxSize))

        Row(verticalAlignment = Alignment.CenterVertically) {
            row.beats.forEachIndexed { index, beat ->
                if (index > 0) {
                    Spacer(modifier = Modifier.width(gap))
                    if (count > 4 && index % 4 == 0) {
                        Spacer(modifier = Modifier.width(divider))
                    }
                }
                var pressed by remember(row, index) { mutableStateOf(false) }
                Pad(
                    size = size,
                    shape = if (beat.hand == Hand.Right) PadShape.Square else PadShape.Round,
                    tone = when (beat.state) {
                        BeatState.Normal -> PadTone.Normal
                        BeatState.Accent -> PadTone.Accent
                        BeatState.Mute -> PadTone.Mute
                    },
                    lit = litBeat == index,
                    letter = beat.hand.label,
                    showLetter = showLetters,
                    pressed = pressed,
                    letterFraction = RudiDimens.PAD_LETTER_FRACTION_GRID,
                    modifier = if (editable) {
                        Modifier.pointerInput(row, index) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                pressed = true
                                var longPressed = false
                                var released = false
                                try {
                                    released = withTimeout(RudiMotion.LONG_PRESS_MS) {
                                        waitForUpOrCancellation() != null
                                    }
                                } catch (_: PointerEventTimeoutCancellationException) {
                                    longPressed = true
                                }
                                if (longPressed) {
                                    onToggleHand(index)
                                    waitForUpOrCancellation()
                                } else if (released) {
                                    onCycleBeat(index)
                                }
                                pressed = false
                            }
                        }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

private val DrumEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)
private const val VISIBLE_SLOTS = 3
private const val FADE_STOP = 0.22f
private val SWIPE_THRESHOLD = 28.dp
