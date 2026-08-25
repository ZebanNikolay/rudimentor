package com.rudimentor.app.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.component.drawPadFace
import com.rudimentor.app.ui.component.padPalette
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * The scrolling note track: notes are pads, drawn right to left onto the hit line.
 *
 * Everything is painted in one Canvas through [drawPadFace], the same face the
 * [com.rudimentor.app.ui.component.Pad] composable uses, so a hundred notes cost
 * one draw pass instead of a hundred composables while still looking identical to
 * the metronome pads.
 *
 * A note has two looks only: ahead of the hit line it wears the accent frame, and
 * once it is judged it falls back to the plain, dimmed face (decision 115). The
 * accent tone is free for this because no lesson marks accented strokes yet -- when
 * they arrive they will get a look of their own.
 *
 * Geometry follows the approved concept: one lane line across the vertical centre
 * with the notes sitting on it and hit dots on a virtual rail below the pads. The
 * verdict of a stroke is written under its own note, in the colour of its window, and
 * rides away with the note instead of flying over the hit line (decision 143): a word
 * that climbs over the hit line collides with the line itself and is gone before it can
 * be read, while a word parked under the note leaves a readable trail of the attempt.
 *
 * The note side comes from the pad size library ([RudiDimens.TrackNoteSize]) and is
 * read from arm's length over a practice pad, which is also why the lane shows less
 * time at once than the concept did: the notes have to keep their air.
 *
 * [frame] is the poll counter: reading it inside the draw lambda is what makes the
 * track redraw on every engine poll.
 */
@Composable
fun PracticeTrack(
    notes: List<PracticeNote>,
    attempt: PracticeAttempt,
    positionMs: Float,
    beatMs: Float,
    frame: Int,
    finishMs: Float,
    modifier: Modifier = Modifier,
    showOffsetMs: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        // Reading the poll counter is what makes the track redraw every poll.
        if (frame < 0) return@Canvas

        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val pxPerMs = width / VISIBLE_MS
        val lineX = width * LINE_FRACTION
        val side = minOf(
            with(density) { RudiDimens.TrackNoteSize.toPx() },
            height * RudiDimens.TRACK_NOTE_HEIGHT_FRACTION,
        )
        // The lane sits on the vertical centre and the notes are centred on it.
        val laneY = height / 2f
        val hitDotY = laneY + side * HIT_DOT_OFFSET
        val extraDotY = laneY + side * EXTRA_DOT_OFFSET
        // The verdict block starts below the hit dot rail and stays inside the canvas.
        val verdictY = minOf(laneY + side * VERDICT_TOP_OFFSET, height - side * 0.5f)

        drawBarLines(
            positionMs = positionMs,
            beatMs = beatMs,
            pxPerMs = pxPerMs,
            lineX = lineX,
            height = height,
        )
        drawLane(laneY = laneY, width = width)
        drawHitLine(lineX = lineX, height = height)

        val letterSize = with(density) { (side * 0.42f).toSp() }
        val letterStyle = TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = letterSize,
        )

        drawCountIn(
            positionMs = positionMs,
            beatMs = beatMs,
            pxPerMs = pxPerMs,
            lineX = lineX,
            laneY = laneY,
            side = side,
            measurer = measurer,
            style = letterStyle,
        )

        // The finish is a line across the lane, not a pad: a pad shape says "hit me",
        // and on the first live run it did exactly that (decision 130 replaces 116).
        // Drawn before the notes so the last note passes over it, not under it.
        if (finishMs > 0f) {
            val finishX = lineX + (finishMs - positionMs) * pxPerMs
            if (finishX > -side && finishX < width + side) {
                drawFinishLine(
                    x = finishX,
                    height = height,
                    passed = positionMs >= finishMs,
                    strokeWidth = with(density) { FINISH_STROKE.toPx() },
                    measurer = measurer,
                    style = letterStyle.copy(
                        fontSize = with(density) { (side * FINISH_LABEL_FRACTION).toSp() },
                    ),
                )
            }
        }

        notes.forEach { note ->
            val x = lineX + (note.timeMs - positionMs) * pxPerMs
            if (x < -side || x > width + side) return@forEach
            // A phase level changes its sticking mid-attempt and a subdivision switch changes
            // its density: both are announced by a mark in front of the first note of the new
            // block, so the switch is seen coming instead of being discovered on the hit line
            // (decision 141).
            if ((note.phaseStart || note.densityStart) && note.index > 0) {
                drawPhaseSwitch(x = x - side * PHASE_MARK_GAP, height = height)
            }
            val judgement = attempt.judgementAt(note.index)
            val missed = judgement?.window == HitWindow.Miss
            // A missed note drops out of the lane and fades instead of being crossed
            // out (decision 87).
            val fallProgress = if (missed) {
                ((positionMs - (note.timeMs + attempt.windows.okMs)) / MISS_FALL_MS)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            val fallY = fallProgress * fallProgress * side * 1.6f
            val alpha = if (missed) (1f - fallProgress * 0.85f).coerceAtLeast(0.15f) else 1f
            // The note that is still coming is the one the eye needs: it wears the
            // accent frame. A note already played drops back to the plain face and
            // dims, so the lane ahead of the hit line always reads first
            // (decision 115). Its result is on the dot below, not on the pad.
            val played = judgement != null
            val tone = if (played) PadTone.Normal else PadTone.Accent
            val palette = padPalette(
                round = note.hand == PatternHand.Left,
                tone = tone,
                lit = false,
                light = false,
            )
            val fade = if (played && !missed) PLAYED_ALPHA else 1f
            drawPadFace(
                topLeft = Offset(x - side / 2f, laneY - side / 2f + fallY),
                side = side,
                round = note.hand == PatternHand.Left,
                tone = tone,
                palette = palette,
                lit = false,
                strokeWidth = with(density) { 1.dp.toPx() },
                light = false,
                alpha = palette.alpha * alpha * fade,
            )
            val label = measurer.measure(
                text = if (note.hand == PatternHand.Right) "R" else "L",
                style = letterStyle.copy(color = palette.letter.copy(alpha = alpha * fade)),
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x - label.size.width / 2f,
                    laneY - label.size.height / 2f + fallY,
                ),
            )

            // The hit itself is a dot below the note, offset by how late or early it
            // landed -- the note keeps its own place on the grid.
            if (judgement != null && judgement.window != HitWindow.Miss) {
                drawCircle(
                    color = windowColor(judgement.window),
                    radius = side * HIT_DOT_SIZE / 2f,
                    center = Offset(x + judgement.offsetMs * pxPerMs, hitDotY),
                )
            }

            // The verdict sits under the stroke that earned it and scrolls away with it:
            // it never fades and never moves on its own, so a glance back down the lane
            // still reads the last few strokes (decision 143).
            if (judgement != null) {
                val hitX = if (judgement.window == HitWindow.Miss) {
                    x
                } else {
                    x + judgement.offsetMs * pxPerMs
                }
                drawVerdictLabel(
                    x = hitX,
                    topY = verdictY,
                    side = side,
                    judgement = judgement,
                    showOffsetMs = showOffsetMs,
                    measurer = measurer,
                    style = letterStyle,
                )
            }
        }

        attempt.extras.forEach { hitMs ->
            val x = lineX + (hitMs - positionMs) * pxPerMs
            if (x < 0f || x > width) return@forEach
            drawCircle(
                color = RudiColors.TrackExtraHit.copy(alpha = 0.7f),
                radius = side * EXTRA_DOT_SIZE / 2f,
                center = Offset(x, extraDotY),
            )
        }
    }
}

/**
 * The verdict of one stroke: the word of its window under the hit dot, optionally with
 * the milliseconds under it. Bigger than the old parked label (decision 143) because it
 * is read at arm's length over a pad, and drawn at full opacity — the lane scrolls it
 * out on its own, nothing has to fade it.
 */
private fun DrawScope.drawVerdictLabel(
    x: Float,
    topY: Float,
    side: Float,
    judgement: NoteJudgement,
    showOffsetMs: Boolean,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val color = windowColor(judgement.window)
    val word = measurer.measure(
        text = verdictWord(judgement),
        style = style.copy(fontSize = (side * VERDICT_WORD_FRACTION).toSp(), color = color),
    )
    drawText(
        textLayoutResult = word,
        topLeft = Offset(x - word.size.width / 2f, topY),
    )
    if (showOffsetMs && judgement.window != HitWindow.Miss) {
        val offsets = measurer.measure(
            text = PracticeScoring.verdictLabel(judgement.offsetMs),
            style = style.copy(
                fontSize = (side * VERDICT_MS_FRACTION).toSp(),
                color = color.copy(alpha = 0.8f),
            ),
        )
        drawText(
            textLayoutResult = offsets,
            topLeft = Offset(x - offsets.size.width / 2f, topY + word.size.height),
        )
    }
}

/**
 * The end of the lane: one heavy vertical line with FINISH written over it. A pad
 * shape here read as "hit me" and earned an extra stroke on the first live run, so
 * the finish carries no pad geometry at all (decision 130). Once it crosses the hit
 * line it turns brick; LEVEL CLEAR is announced by the HUD, not by the label.
 */
private fun DrawScope.drawFinishLine(
    x: Float,
    height: Float,
    passed: Boolean,
    strokeWidth: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    if (height <= 0f) return
    val top = height * FINISH_TOP_FRACTION
    val bottom = height * (1f - FINISH_TOP_FRACTION)
    drawLine(
        color = if (passed) RudiColors.BrickLit else RudiColors.Text,
        start = Offset(x, top),
        end = Offset(x, bottom),
        strokeWidth = strokeWidth,
    )
    val label = measurer.measure(text = "FINISH", style = style.copy(color = RudiColors.Text))
    drawText(
        textLayoutResult = label,
        topLeft = Offset(
            x - label.size.width / 2f,
            (top - label.size.height - height * FINISH_LABEL_GAP).coerceAtLeast(0f),
        ),
    )
}

/**
 * Where a phase level swaps its sticking: one brick hairline across the lane, thinner and
 * shorter than the finish line so it reads as a switch and never as an end.
 */
private fun DrawScope.drawPhaseSwitch(x: Float, height: Float) {
    if (height <= 0f) return
    drawLine(
        color = RudiColors.BrickBright.copy(alpha = PHASE_MARK_ALPHA),
        start = Offset(x, height * PHASE_TOP_FRACTION),
        end = Offset(x, height * (1f - PHASE_TOP_FRACTION)),
        strokeWidth = 2f,
    )
}

internal fun windowColor(window: HitWindow): Color = when (window) {
    HitWindow.Perfect -> RudiColors.WindowPerfect
    HitWindow.Good -> RudiColors.WindowGood
    HitWindow.Ok -> RudiColors.WindowOk
    HitWindow.Miss -> RudiColors.WindowMiss
}

/** Bar lines every four beats, plus a quieter line on every beat. */
private fun DrawScope.drawBarLines(
    positionMs: Float,
    beatMs: Float,
    pxPerMs: Float,
    lineX: Float,
    height: Float,
) {
    if (beatMs <= 0f) return
    val firstBeat = ((positionMs - lineX / pxPerMs) / beatMs).toInt() - 1
    val lastBeat = ((positionMs + (size.width - lineX) / pxPerMs) / beatMs).toInt() + 1
    for (beat in firstBeat..lastBeat) {
        val x = lineX + (beat * beatMs - positionMs) * pxPerMs
        if (x < 0f || x > size.width) continue
        val strong = beat % 4 == 0
        drawLine(
            color = if (strong) RudiColors.TrackBarStrong else RudiColors.TrackBar,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f,
        )
    }
}

/**
 * The hit line is shortened to 21..79% of the height and capped with square nibs.
 * No band behind it: the concept had a faint brick gradient there and on a device it
 * only muddied the lane, so the line carries the target on its own.
 */
private fun DrawScope.drawHitLine(lineX: Float, height: Float) {
    val top = height * 0.21f
    val bottom = height * 0.79f
    drawLine(
        color = RudiColors.Text,
        start = Offset(lineX, top),
        end = Offset(lineX, bottom),
        strokeWidth = 2f,
    )
    val nib = 7f
    drawLine(
        color = RudiColors.Text,
        start = Offset(lineX - nib / 2f, top),
        end = Offset(lineX + nib / 2f, top),
        strokeWidth = 2f,
    )
    drawLine(
        color = RudiColors.Text,
        start = Offset(lineX - nib / 2f, bottom),
        end = Offset(lineX + nib / 2f, bottom),
        strokeWidth = 2f,
    )
}

/** The lane the notes ride on: one hairline across the vertical centre. */
private fun DrawScope.drawLane(laneY: Float, width: Float) {
    drawLine(
        color = RudiColors.TrackLane,
        start = Offset(0f, laneY),
        end = Offset(width, laneY),
        strokeWidth = 1f,
    )
}

/**
 * The count-in rides along the track like the notes do: four digits with a tick,
 * lit once they pass the hit line, and the microphone is not judged yet.
 */
private fun DrawScope.drawCountIn(
    positionMs: Float,
    beatMs: Float,
    pxPerMs: Float,
    lineX: Float,
    laneY: Float,
    side: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    for (beat in 0 until PracticeScoring.COUNT_IN_BEATS) {
        val timeMs = beat * beatMs
        val x = lineX + (timeMs - positionMs) * pxPerMs
        if (x < -side || x > size.width + side) continue
        val passed = positionMs >= timeMs
        val color = if (passed) RudiColors.PadLedLit else RudiColors.Muted
        val digit = measurer.measure(
            text = (beat + 1).toString(),
            style = style.copy(color = color),
        )
        val digitCenterY = laneY - side * 0.49f
        drawText(
            textLayoutResult = digit,
            topLeft = Offset(x - digit.size.width / 2f, digitCenterY - digit.size.height / 2f),
        )
        drawLine(
            color = color.copy(alpha = 0.6f),
            start = Offset(x, laneY - side * 0.09f),
            end = Offset(x, laneY + side * 0.55f),
            strokeWidth = 2f,
        )
    }
}

/** The word of a window: what is written under the stroke that earned it. */
internal fun verdictWord(judgement: NoteJudgement): String = when (judgement.window) {
    HitWindow.Perfect -> "PERFECT"
    HitWindow.Good -> "GOOD"
    HitWindow.Ok -> "OK"
    HitWindow.Miss -> "MISS"
}

/**
 * How much time the lane shows at once. Shorter than the concept's two seconds: the
 * notes grew by half (decision 130), so the lane has to spread them out to keep the
 * air between them at the fast ranks.
 */
private const val VISIBLE_MS = 1400f
private const val LINE_FRACTION = 0.33f
private const val MISS_FALL_MS = 420f

/** How far a played note steps back once its verdict is on the rail. */
private const val PLAYED_ALPHA = 0.45f

/** The finish line: how tall it stands, how heavy it is, where its label sits. */
private const val FINISH_TOP_FRACTION = 0.18f
private const val FINISH_LABEL_FRACTION = 0.26f
private const val FINISH_LABEL_GAP = 0.03f
private val FINISH_STROKE = 4.dp

/**
 * The verdict label under a stroke: how big the word is, how big the optional
 * milliseconds are, and how far below the hit dot the block starts. All fractions of the
 * note side, so the label scales with the pads (decision 143).
 */
private const val VERDICT_WORD_FRACTION = 0.40f
private const val VERDICT_MS_FRACTION = 0.20f
private const val VERDICT_TOP_OFFSET = 1.02f

/** The phase switch mark: how far in front of the note it stands, how tall and how loud. */
private const val PHASE_MARK_GAP = 0.66f
private const val PHASE_TOP_FRACTION = 0.26f
private const val PHASE_MARK_ALPHA = 0.55f

/** Concept geometry, as fractions of the note side (44 px in the concept). */
private const val HIT_DOT_OFFSET = 0.74f
private const val HIT_DOT_SIZE = 0.25f
private const val EXTRA_DOT_OFFSET = 0.58f
private const val EXTRA_DOT_SIZE = 0.16f
