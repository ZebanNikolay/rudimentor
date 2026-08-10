package com.rudimentor.app.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    rowSwipeEnabled: Boolean = true,
    onSelectRow: (Int) -> Unit,
    onCycleBeat: (Int, Int) -> Unit,
    onToggleHand: (Int, Int) -> Unit,
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
            onCycleBeat = { beat -> onCycleBeat(0, beat) },
            onToggleHand = { beat -> onToggleHand(0, beat) },
            modifier = modifier
                .fillMaxWidth()
                .height(RudiDimens.DrumSlotHeight),
        )
        return
    }

    val density = LocalDensity.current
    // Rows are pitched tighter than the focus slot is tall, so the neighbours stay clear
    // of the fade instead of hiding in it.
    val slotHeightPx = with(density) { RudiDimens.DrumSlotHeight.toPx() } * ROW_PITCH
    val scope = rememberCoroutineScope()

    // The barrel position is a continuous virtual row index, so it can cross the loop
    // seam in either direction and follow the finger between two rows.
    val position = remember { Animatable(activeRow.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(activeRow, grid.rowCount) {
        if (dragging) return@LaunchedEffect
        val current = position.value.roundToInt()
        val forward = (activeRow - current).mod(grid.rowCount)
        val delta = if (forward * 2 > grid.rowCount) forward - grid.rowCount else forward
        val target = (current + delta).toFloat()
        if (target != position.value) {
            position.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = RudiMotion.spinMillis(bpm),
                    easing = DrumEasing,
                ),
            )
        }
    }

    val decay = splineBasedDecay<Float>(density)
    val dragState = rememberDraggableState { delta ->
        scope.launch { position.snapTo(position.value - delta / slotHeightPx) }
    }

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
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = rowSwipeEnabled,
                onDragStarted = { dragging = true },
                onDragStopped = { velocity ->
                    // The barrel always settles on a row: the fling is projected with a
                    // decay curve, clamped to one row per flick, then eased home by a
                    // slightly under-damped spring so it resists, gives and locks in.
                    val rowsPerSecond = -velocity / slotHeightPx
                    val projected = decay.calculateTargetValue(position.value, rowsPerSecond)
                    val target = projected
                        .coerceIn(position.value - 1f, position.value + 1f)
                        .roundToInt()
                    // Run the settle outside the gesture scope so lifting the finger can
                    // never leave the barrel parked between two rows.
                    scope.launch {
                        position.animateTo(
                            targetValue = target.toFloat(),
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = Spring.StiffnessLow,
                            ),
                            initialVelocity = rowsPerSecond,
                        )
                        dragging = false
                    }
                    onSelectRow(target.mod(grid.rowCount))
                },
            ),
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

        val current = position.value
        val base = floor(current).toInt()
        for (virtualIndex in (base - 1)..(base + 2)) {
            val rowIndex = virtualIndex.mod(grid.rowCount)
            val distance = virtualIndex - current
            if (abs(distance) > 1.75f) continue
            val magnitude = abs(distance).coerceAtMost(2f)
            val alpha = when {
                magnitude <= 1f -> 1f - magnitude * (1f - RudiMotion.NEIGHBOUR_ALPHA)
                else -> RudiMotion.NEIGHBOUR_ALPHA * (2f - magnitude)
            }
            if (alpha <= 0.01f) continue
            val scale = 1f - (1f - RudiMotion.NEIGHBOUR_SCALE) * magnitude.coerceAtMost(1f)
            val isCentre = magnitude < 0.5f

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
                    onCycleBeat = { beat -> onCycleBeat(rowIndex, beat) },
                    onToggleHand = { beat -> onToggleHand(rowIndex, beat) },
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

/** Row spacing as a fraction of the focus slot height. */
private const val ROW_PITCH = 0.66f
