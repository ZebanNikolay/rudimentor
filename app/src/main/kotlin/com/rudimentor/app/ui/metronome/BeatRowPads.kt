package com.rudimentor.app.ui.metronome

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Hand
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.theme.PadHand
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiMotion

/**
 * One row of pads, sized to fill the available width with drum-machine
 * grouping. Extracted from [BeatDrum] because both the single-row case and each
 * slot of the barrel render the same row layout.
 */
@Composable
internal fun BeatRowPads(
    row: BeatRow,
    isActive: Boolean,
    showLetters: Boolean,
    litBeat: Int?,
    editable: Boolean,
    onCycleBeat: (Int) -> Unit,
    onToggleHand: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sizeScale: Float = 1f,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val count = row.size
        val baseGap: Dp = when {
            count > 8 -> 5.dp
            count > 4 -> 8.dp
            else -> 12.dp
        }
        val baseDivider: Dp = if (count > 4) 14.dp else 0.dp
        // Fours, unless the row only splits in threes: six or nine strokes are beats of three,
        // and a gap after the fourth cut through a beat (decision 243, as 205 spaces stickings).
        val groupSize = if (count % 4 != 0 && count % 3 == 0) 3 else 4
        val groups = if (count > 4) (count - 1) / groupSize else 0
        val maxSize = (if (isActive) RudiDimens.PadActiveMaxSize else RudiDimens.PadNeighbourMaxSize) * sizeScale
        // The gaps grow with the pads only while the width has room for both. A row the width
        // already caps keeps the base gaps: grown gaps there only took the room from the pads,
        // and a long row shrank below the size it had before the drum could grow (decision 240).
        val grownAvailable = maxWidth - baseGap * sizeScale * (count - 1) - baseDivider * sizeScale * groups
        val widthBound = grownAvailable / count < maxSize
        val gap = if (widthBound) baseGap else baseGap * sizeScale
        val divider = if (widthBound) baseDivider else baseDivider * sizeScale
        val available = maxWidth - gap * (count - 1) - divider * groups
        val size = maxOf(RudiDimens.PadMinSize, minOf(available / count, maxSize))

        Row(verticalAlignment = Alignment.CenterVertically) {
            row.beats.forEachIndexed { index, beat ->
                if (index > 0) {
                    Spacer(modifier = Modifier.width(gap))
                    if (count > 4 && index % groupSize == 0) {
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
                    contrastLetters = true,
                    hand = if (beat.hand == Hand.Right) PadHand.Right else PadHand.Left,
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
                                    // A hand swap is a mode change, so it is confirmed by touch.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
