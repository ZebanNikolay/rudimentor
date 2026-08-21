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

/**
 * The scrolling note track: notes are pads, drawn right to left onto the hit line.
 *
 * Everything is painted in one Canvas through [drawPadFace], the same face the
 * [com.rudimentor.app.ui.component.Pad] composable uses, so a hundred notes cost
 * one draw pass instead of a hundred composables while still looking identical to
 * the metronome pads.
 *
 * Geometry follows the approved concept: one lane line across the vertical centre
 * with the notes sitting on it, hit dots on a virtual rail below the pads, and the
 * verdict on the bottom edge under the hit line.
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
    modifier: Modifier = Modifier,
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
        val side = minOf(with(density) { NOTE_SIDE.toPx() }, height * 0.34f)
        // The lane sits on the vertical centre and the notes are centred on it.
        val laneY = height / 2f
        val hitDotY = laneY + side * HIT_DOT_OFFSET
        val extraDotY = laneY + side * EXTRA_DOT_OFFSET

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

        notes.forEach { note ->
            val x = lineX + (note.timeMs - positionMs) * pxPerMs
            if (x < -side || x > width + side) return@forEach
            val judgement = attempt.judgementAt(note.index)
            val missed = judgement?.window == HitWindow.Miss
            // A missed note drops out of the lane and fades instead of being crossed
            // out (decision 87).
            val fallProgress = if (missed) {
                ((positionMs - (note.timeMs + PracticeScoring.OK_MS)) / MISS_FALL_MS)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            val fallY = fallProgress * fallProgress * side * 1.6f
            val alpha = if (missed) (1f - fallProgress * 0.85f).coerceAtLeast(0.15f) else 1f
            val lit = judgement != null && judgement.window != HitWindow.Miss
            val palette = padPalette(
                round = note.hand == PatternHand.Left,
                tone = PadTone.Normal,
                lit = lit,
                light = false,
            )
            drawPadFace(
                topLeft = Offset(x - side / 2f, laneY - side / 2f + fallY),
                side = side,
                round = note.hand == PatternHand.Left,
                tone = PadTone.Normal,
                palette = palette,
                lit = lit,
                strokeWidth = with(density) { 1.dp.toPx() },
                light = false,
                alpha = palette.alpha * alpha,
            )
            val label = measurer.measure(
                text = if (note.hand == PatternHand.Right) "R" else "L",
                style = letterStyle.copy(color = palette.letter.copy(alpha = alpha)),
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

        // The verdict of the last judged note sits on the bottom edge under the hit
        // line and fades out shortly after, as in the concept (decision 98).
        val judged = attempt.lastJudged
        val verdict = verdictText(judged)
        val age = positionMs - attempt.lastJudgedAtMs
        if (verdict != null && age in 0f..VERDICT_HOLD_MS) {
            val layout = measurer.measure(
                text = verdict,
                style = letterStyle.copy(
                    fontSize = with(density) { (side * 0.30f).toSp() },
                    color = windowColor(judged?.window ?: HitWindow.Miss),
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    lineX - layout.size.width / 2f,
                    height - layout.size.height - with(density) { 6.dp.toPx() },
                ),
            )
        }
    }
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

/** Verdict text of the last judged note, always in milliseconds. */
internal fun verdictText(judgement: NoteJudgement?): String? = when {
    judgement == null -> null
    judgement.window == HitWindow.Miss -> "MISS"
    else -> PracticeScoring.verdictLabel(judgement.offsetMs)
}

private const val VISIBLE_MS = 2000f
private const val LINE_FRACTION = 0.33f
private const val MISS_FALL_MS = 420f

/** How long the verdict stays on screen after a note is judged. */
private const val VERDICT_HOLD_MS = 380f

/** Concept geometry, as fractions of the note side (44 px in the concept). */
private const val HIT_DOT_OFFSET = 0.74f
private const val HIT_DOT_SIZE = 0.25f
private const val EXTRA_DOT_OFFSET = 0.58f
private const val EXTRA_DOT_SIZE = 0.16f

private val NOTE_SIDE = 44.dp
